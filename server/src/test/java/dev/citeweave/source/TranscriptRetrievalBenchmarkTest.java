package dev.citeweave.source;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.citeweave.source.TranscriptSummaryEngine.Evidence;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

/** 用生产字幕解析及召回函数复现冻结题集的检索结果。 */
class TranscriptRetrievalBenchmarkTest {
    private static final Path FIXTURE = Path.of("..", "sample-data", "retrieval", "transcript-v1");
    private static final Path OUTPUT = Path.of("target", "transcript-retrieval-v1.json");
    private static final Path HOLDOUT = FIXTURE.resolveSibling("transcript-alias-holdout-v1");
    private static final Path COMPARISON = OUTPUT.resolveSibling("transcript-retrieval-alias-comparison.json");
    private static final Path REFUSAL_INPUT = OUTPUT.resolveSibling("transcript-refusal-eval-input.json");

    record Dataset(String version, String provenance, Map<String, String> sources, List<Question> questions) {}
    record Question(String id, String pairId, String source, String category, String text,
                    List<List<Integer>> requiredGroups, String expectedAnswer) {}
    record Observation(String id, String source, String category, List<Integer> top5,
                       int groupsFound, int groupsRequired, Double recallAt5) {}
    record Metrics(int questions, int fullyCovered, int noCandidates, Double macroRecallAt5) {}
    record Report(String dataset, Map<String, Metrics> byCategory, Metrics answerable,
                  Metrics unanswerable, List<Observation> questions) {}
    record Comparison(Report lexical, Report aliasFallback) {}
    record Evaluations(Comparison original, Comparison holdout) {}
    record ModelCase(String id, String question, String expectedAnswer, List<List<String>> requiredGroups,
                     List<Evidence> lexical, List<Evidence> aliasFallback) {}
    record ModelDataset(String dataset, List<ModelCase> cases) {}
    record DenseDataset(String dataset, Map<String, List<Evidence>> tracks, List<DenseCase> cases) {}
    record DenseCase(String id, String source, String category, String question, List<List<String>> requiredGroups,
                     List<String> lexical) {}

    @Test
    void evaluatesProductionRetrievalWithoutModelOrDatabase() throws IOException {
        var mapper = new ObjectMapper();
        var dataset = mapper.readValue(FIXTURE.resolve("questions.json").toFile(), Dataset.class);
        var tracks = loadTracks(FIXTURE, dataset);
        validateLabels(dataset, tracks);
        assertTrue(dataset.questions().size() >= 40);
        assertTrue(dataset.questions().stream().filter(question -> question.requiredGroups().isEmpty()).count() >= 10);
        var report = measure(dataset, tracks, TranscriptQuestionService::retrieve);
        Files.createDirectories(OUTPUT.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(OUTPUT.toFile(), report);
        exportDense(mapper, dataset, tracks, "transcript-dense-original-input.json");
        System.out.println("Retrieval benchmark: " + OUTPUT.toAbsolutePath().normalize());
        System.out.println(mapper.writeValueAsString(Map.of(
                "answerable", report.answerable(), "unanswerable", report.unanswerable(),
                "byCategory", report.byCategory())));
    }

    @Test
    void comparesAliasFallbackOnOriginalAndIndependentQuestions() throws IOException {
        var mapper = new ObjectMapper();
        var evaluation = new Evaluations(compare(mapper, FIXTURE), compare(mapper, HOLDOUT));
        Files.createDirectories(COMPARISON.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(COMPARISON.toFile(), evaluation);
        var dataset = mapper.readValue(HOLDOUT.resolve("questions.json").toFile(), Dataset.class);
        var tracks = loadTracks(HOLDOUT, dataset);
        exportDense(mapper, dataset, tracks, "transcript-dense-holdout-input.json");
        var cases = dataset.questions().stream().map(question -> modelCase(question, tracks.get(question.source())))
                .toList();
        mapper.writerWithDefaultPrettyPrinter().writeValue(REFUSAL_INPUT.toFile(),
                new ModelDataset(dataset.version(), cases));
        System.out.println("Alias fallback comparison: " + COMPARISON.toAbsolutePath().normalize());
    }

    /** 两种检索使用同一批生产切片和标注，冻结词面基线供 Python 离线对照。 */
    private static void exportDense(ObjectMapper mapper, Dataset dataset, Map<String, List<Evidence>> tracks,
                                    String filename) throws IOException {
        var cases = dataset.questions().stream().map(question -> {
            var baseline = modelCase(question, tracks.get(question.source()));
            return new DenseCase(question.id(), question.source(), question.category(), question.text(),
                    baseline.requiredGroups(), baseline.lexical().stream().map(Evidence::evidenceId).toList());
        }).toList();
        mapper.writerWithDefaultPrettyPrinter().writeValue(OUTPUT.resolveSibling(filename).toFile(),
                new DenseDataset(dataset.version(), tracks, cases));
    }

    /** 导出固定合成字幕的生产候选。同一问题的两条路径共享期望答案和证据组。 */
    private static ModelCase modelCase(Question question, List<Evidence> track) {
        Map<Integer, String> ids = new LinkedHashMap<>();
        track.forEach(cue -> ids.put(sequence(cue), cue.evidenceId()));
        var groups = question.requiredGroups().stream()
                .map(group -> group.stream().map(ids::get).toList())
                .toList();
        return new ModelCase(question.id(), question.text(), question.expectedAnswer(), groups,
                TranscriptQuestionService.retrieve(question.text(), track),
                TranscriptQuestionService.retrieveWithAliases(question.text(), track));
    }

    private static Comparison compare(ObjectMapper mapper, Path root) throws IOException {
        var dataset = mapper.readValue(root.resolve("questions.json").toFile(), Dataset.class);
        var tracks = loadTracks(root, dataset);
        validateLabels(dataset, tracks);
        return new Comparison(measure(dataset, tracks, TranscriptQuestionService::retrieve),
                measure(dataset, tracks, TranscriptQuestionService::retrieveWithAliases));
    }

    private static Report measure(Dataset dataset, Map<String, List<Evidence>> tracks,
                                  BiFunction<String, List<Evidence>, List<Evidence>> retriever) {
        var observations = dataset.questions().stream()
                .map(question -> evaluate(question, tracks.get(question.source()), retriever))
                .toList();
        var breakdown = new LinkedHashMap<String, Metrics>();
        dataset.questions().stream().map(Question::category).distinct()
                .forEach(category -> breakdown.put(category, metrics(observations.stream()
                        .filter(result -> result.category().equals(category)).toList())));
        return new Report(dataset.version(), breakdown,
                metrics(observations.stream().filter(result -> result.groupsRequired() > 0).toList()),
                metrics(observations.stream().filter(result -> result.groupsRequired() == 0).toList()),
                observations);
    }

    private static Map<String, List<Evidence>> loadTracks(Path root, Dataset dataset) throws IOException {
        var parser = new SourceParser();
        var reader = new TranscriptEvidenceReader(null, parser);
        var tracks = new LinkedHashMap<String, List<Evidence>>();
        long nextId = 1;
        for (var entry : dataset.sources().entrySet()) {
            String filename = entry.getValue();
            var parsed = parser.file(filename, Files.readAllBytes(root.resolve(filename)));
            var header = new SourceRepository.Summary(nextId++, entry.getKey(), parsed.kind(), filename, OffsetDateTime.MIN);
            var detail = new SourceRepository.Detail(header, parsed.content());
            tracks.put(entry.getKey(), reader.read(detail, reader.version(detail)));
        }
        return tracks;
    }

    private static void validateLabels(Dataset dataset, Map<String, List<Evidence>> tracks) {
        var ids = new HashSet<String>();
        for (var question : dataset.questions()) {
            assertTrue(ids.add(question.id()), "Duplicate question: " + question.id());
            var track = tracks.get(question.source());
            assertNotNull(track, "Unknown source: " + question.id());
            var validSequences = track.stream().map(TranscriptRetrievalBenchmarkTest::sequence).toList();
            assertEquals(question.requiredGroups().isEmpty(), question.expectedAnswer() == null, question.id());
            for (var group : question.requiredGroups()) {
                assertFalse(group.isEmpty(), "Empty evidence group: " + question.id());
                assertTrue(validSequences.containsAll(group), "Unknown subtitle cue: " + question.id());
            }
        }
    }

    /** 每个证据组命中任一可替代的片段就算命中，多组题允许计部分召回。 */
    private static Observation evaluate(Question question, List<Evidence> track,
                                        BiFunction<String, List<Evidence>, List<Evidence>> retriever) {
        var top5 = retriever.apply(question.text(), track).stream()
                .map(TranscriptRetrievalBenchmarkTest::sequence).toList();
        int required = question.requiredGroups().size();
        int found = (int) question.requiredGroups().stream().filter(group -> group.stream().anyMatch(top5::contains)).count();
        return new Observation(question.id(), question.source(), question.category(), top5, found, required,
                required == 0 ? null : (double) found / required);
    }

    private static Metrics metrics(List<Observation> cases) {
        int fullyCovered = (int) cases.stream().filter(result -> result.groupsRequired() > 0
                && result.groupsFound() == result.groupsRequired()).count();
        int noCandidates = (int) cases.stream().filter(result -> result.top5().isEmpty()).count();
        var recall = cases.stream().filter(result -> result.recallAt5() != null)
                .mapToDouble(Observation::recallAt5).average();
        return new Metrics(cases.size(), fullyCovered, noCandidates, recall.isPresent() ? recall.getAsDouble() : null);
    }

    private static int sequence(Evidence evidence) {
        return Integer.parseInt(evidence.evidenceId().substring(evidence.evidenceId().lastIndexOf(':') + 1));
    }
}
