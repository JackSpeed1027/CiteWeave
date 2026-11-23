package dev.citeweave.source;

import static dev.citeweave.source.TranscriptSummaryEngine.Failure.*;

import dev.citeweave.source.TranscriptSummaryEngine.Evidence;
import dev.citeweave.source.TranscriptSummaryEngine.SummaryException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** 生成并保存当前不可变字幕版本的首个有效摘要。 */
@Service
class TranscriptSummaryService {
    record Result(String summary, List<Evidence> citations, boolean truncated) {}

    private final TranscriptEvidenceReader reader;
    private final TranscriptSummaryEngine engine;
    private final TranscriptSummaryRepository summaries;

    TranscriptSummaryService(TranscriptEvidenceReader reader, TranscriptSummaryEngine engine,
                             TranscriptSummaryRepository summaries) {
        this.reader = reader;
        this.engine = engine;
        this.summaries = summaries;
    }

    /** 只读取与当前字幕版本匹配的已发布摘要。 */
    Optional<Result> find(long id) {
        return summaries.find(id, reader.version(reader.load(id)));
    }

    /** 不持有数据库事务等待模型。并发生成只发布一个结果，后续提交复用已保存结果。 */
    Result generate(long id) {
        var source = reader.load(id);
        String version = reader.version(source);
        return summaries.find(id, version).orElseGet(() -> {
            var verified = generateVerified(source, version);
            summaries.publish(source, version, verified.result(), verified.inputIds());
            return find(id).orElseThrow(() -> new SummaryException(SOURCE_CHANGED));
        });
    }

    /** 已有摘要直接完成任务，首次生成在模型返回后开启短事务。 */
    void generateForTask(SummaryTaskRepository.Task task) {
        var source = reader.load(task.sourceId());
        String version = reader.version(source);
        if (summaries.find(task.sourceId(), version).isPresent()) {
            summaries.completeFromSaved(source, version, task);
            return;
        }
        var verified = generateVerified(source, version);
        summaries.publishAndComplete(source, version, verified.result(), verified.inputIds(), task);
    }

    private record VerifiedDraft(Result result, List<String> inputIds) {}

    private VerifiedDraft generateVerified(SourceRepository.Detail source, String version) {
        var evidence = reader.read(source, version);
        if (evidence.isEmpty()) throw new SummaryException(EVIDENCE_INSUFFICIENT);
        var draft = engine.generate(evidence);
        return new VerifiedDraft(verify(evidence, draft), draft.inputEvidenceIds());
    }

    /** 根据实际输入 ID 复核引用，定位信息全部取自 Java 原始证据。 */
    static Result verify(List<Evidence> evidence, TranscriptSummaryEngine.Draft draft) {
        if (draft == null || draft.summary() == null || draft.summary().isBlank()
                || draft.summary().length() > 8000 || draft.summary().indexOf(0) >= 0
                || draft.evidenceIds() == null || draft.evidenceIds().isEmpty()) {
            throw new SummaryException(INVALID_OUTPUT);
        }
        var input = draft.inputEvidenceIds();
        verifyInput(evidence, input, draft.truncated());
        var citations = new HashSet<>(draft.evidenceIds());
        if (!new HashSet<>(input).containsAll(citations)) throw new SummaryException(INVALID_CITATION);
        return new Result(draft.summary(), evidence.stream().filter(item -> citations.contains(item.evidenceId())).toList(),
                draft.truncated());
    }

    /** AI 回传的实际输入必须是 Java 发出的有序前缀。 */
    static void verifyInput(List<Evidence> evidence, List<String> input, Boolean truncated) {
        if (input == null || input.isEmpty() || truncated == null) throw new SummaryException(INVALID_OUTPUT);
        if (input.size() > evidence.size() || truncated != (input.size() < evidence.size())
                || !evidence.stream().limit(input.size()).map(Evidence::evidenceId).toList().equals(input)) {
            throw new SummaryException(INVALID_CITATION);
        }
    }

}
