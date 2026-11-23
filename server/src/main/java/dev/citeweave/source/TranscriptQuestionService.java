package dev.citeweave.source;

import static dev.citeweave.source.TranscriptSummaryEngine.Failure.INVALID_OUTPUT;

import dev.citeweave.source.TranscriptSummaryEngine.Draft;
import dev.citeweave.source.TranscriptSummaryEngine.Evidence;
import dev.citeweave.source.TranscriptSummaryEngine.SummaryException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 在选定资料版本内做关键词召回，并复核模型回答的引用。 */
@Service
class TranscriptQuestionService {
    enum Outcome { ANSWERED, INSUFFICIENT_EVIDENCE }
    record Answer(Outcome status, String answer, List<Evidence> citations) {}

    private static final Pattern WORD = Pattern.compile("\\p{IsHan}+|[\\p{IsLatin}\\p{Nd}]+");
    private static final Pattern CHINESE_QUESTION_WORDS = Pattern.compile("请问|什么|怎么|如何|哪些|哪个|为什么|是否|一下|有没有");
    private static final Set<String> STOP_WORDS = Set.of("a", "an", "the", "and", "or", "of", "in", "on", "is",
            "are", "was", "what", "when", "where", "how", "which", "why", "does", "do", "did", "about", "to");
    private static final int MAX_RESULTS = 5;
    static final int MAX_CUE_LENGTH = 1800;
    private static final Map<String, String> TERM_ALIASES = Map.of(
            "翻页", "游标分页",
            "时间码", "毫秒",
            "语种识别", "语言检测",
            "判别语种", "语言检测",
            "独占资格", "租约",
            "检索库损坏", "索引构建失败",
            "引用标识", "证据 id");

    private final TranscriptEvidenceReader reader;
    private final TranscriptSummaryEngine engine;
    private final boolean aliasFallback;

    TranscriptQuestionService(TranscriptEvidenceReader reader, TranscriptSummaryEngine engine,
                              @Value("${citeweave.question.alias-fallback:false}") boolean aliasFallback) {
        this.reader = reader;
        this.engine = engine;
        this.aliasFallback = aliasFallback;
    }

    /** 无匹配时直接返回证据不足，不调用模型。 */
    Answer ask(long sourceId, String question) {
        return answer(question, candidates(sourceId, question));
    }

    /** 先校验并读取全部资料，再按资料轮流取证据，每份至多两段。 */
    Answer askMultiple(List<Long> sourceIds, String question) {
        var groups = sourceIds.stream().map(id -> candidates(id, question)).toList();
        return answer(question, interleave(groups));
    }

    /** 返回显式所选资料的词面候选，供独立检索与跨资料问答共同使用。 */
    static List<Evidence> search(List<List<Evidence>> sources, String question) {
        var groups = sources.stream().map(evidence -> retrieve(question, evidence)).toList();
        return interleave(groups);
    }

    static List<Evidence> interleave(List<List<Evidence>> groups) {
        var evidence = new ArrayList<Evidence>();
        // 先返回每份有命中资料的首条，再补第二条，给命中的不同来源保留位置。
        for (int rank = 0; rank < 2; rank++) {
            for (var group : groups) {
                if (group.size() > rank) evidence.add(group.get(rank));
            }
        }
        return List.copyOf(evidence);
    }

    Evidence evidence(long id, String evidenceId) {
        var source = reader.load(id);
        String version = reader.version(source);
        if (!evidenceId.startsWith(id + ":" + version + ":")) throw evidenceNotFound();
        return reader.read(source, version).stream().filter(cue -> cue.evidenceId().equals(evidenceId))
                .findFirst().orElseThrow(TranscriptQuestionService::evidenceNotFound);
    }

    private static SourceException evidenceNotFound() {
        return new SourceException(SourceException.Code.SOURCE_EVIDENCE_NOT_FOUND, "找不到当前资料版本中的这条字幕证据。");
    }

    private List<Evidence> candidates(long sourceId, String question) {
        var source = reader.load(sourceId);
        var cues = reader.read(source, reader.version(source));
        return aliasFallback ? retrieveWithAliases(question, cues) : retrieve(question, cues);
    }

    /** 复核模型返回的证据范围，原文和位置以 Java 提供的候选为准。 */
    Answer answer(String question, List<Evidence> evidence) {
        if (evidence.isEmpty()) return insufficient();

        var draft = engine.answer(question, evidence);
        if (draft == null) throw new SummaryException(INVALID_OUTPUT);
        if ("INSUFFICIENT_EVIDENCE".equals(draft.status()) && draft.answer() == null
                && draft.evidenceIds() != null && draft.evidenceIds().isEmpty()) {
            TranscriptSummaryService.verifyInput(evidence, draft.inputEvidenceIds(), draft.truncated());
            return insufficient();
        }
        if (!"ANSWERED".equals(draft.status())) throw new SummaryException(INVALID_OUTPUT);
        var verified = TranscriptSummaryService.verify(evidence,
                new Draft(draft.answer(), draft.evidenceIds(), draft.truncated(), draft.inputEvidenceIds()));
        return new Answer(Outcome.ANSWERED, verified.summary(), verified.citations());
    }

    /** 词面结果为空时，按完整术语补召回。同义候选仍须经过原有答案及引用校验。 */
    static List<Evidence> retrieveWithAliases(String question, List<Evidence> cues) {
        var literal = retrieve(question, cues);
        if (!literal.isEmpty()) return literal;
        var terms = TERM_ALIASES.entrySet().stream().filter(entry -> question.contains(entry.getKey()))
                .map(Map.Entry::getValue).toList();
        if (terms.isEmpty()) return List.of();
        return cues.stream().filter(cue -> cue.text().length() <= MAX_CUE_LENGTH)
                .filter(cue -> {
                    String text = cue.text().toLowerCase(Locale.ROOT);
                    return terms.stream().anyMatch(text::contains);
                })
                .limit(MAX_RESULTS).toList();
    }

    /** 英文按词、中文按双字词计交集。同分维持字幕原顺序。 */
    static List<Evidence> retrieve(String question, List<Evidence> cues) {
        var query = queryTerms(question);
        if (query.isEmpty()) return List.of();

        record Match(Evidence cue, long score) {}
        return cues.stream().filter(cue -> cue.text().length() <= MAX_CUE_LENGTH)
                .map(cue -> new Match(cue, terms(cue.text()).stream().filter(query::contains).count()))
                .filter(match -> match.score() > 0)
                .sorted(Comparator.comparingLong(Match::score).reversed())
                .limit(MAX_RESULTS)
                .map(Match::cue)
                .toList();
    }

    static Set<String> queryTerms(String question) {
        return terms(CHINESE_QUESTION_WORDS.matcher(question).replaceAll(" "));
    }

    static Set<String> terms(String text) {
        Set<String> result = new HashSet<>();
        var words = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (words.find()) {
            String word = words.group();
            if (Character.UnicodeScript.of(word.codePointAt(0)) != Character.UnicodeScript.HAN) {
                if (word.length() > 1 && !STOP_WORDS.contains(word)) result.add(word);
                continue;
            }
            int[] letters = word.codePoints().toArray();
            for (int i = 0; i + 1 < letters.length; i++) {
                result.add(new String(letters, i, 2));
            }
        }
        return result;
    }

    private static Answer insufficient() {
        return new Answer(Outcome.INSUFFICIENT_EVIDENCE, null, List.of());
    }
}
