package dev.citeweave.source;

import dev.citeweave.source.TranscriptSummaryEngine.Evidence;
import dev.citeweave.source.TranscriptSummaryEngine.CheckpointTarget;
import dev.citeweave.source.TranscriptSummaryEngine.ResearchStep;
import dev.citeweave.source.TranscriptSummaryEngine.SummaryException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** 编排一次本机只读研究，资料范围与引用以 Java 复核为准。 */
@Service
class ResearchService {
    record Result(String status, String answer, List<Evidence> citations, List<ResearchStep> trace) {}

    enum Failure {
        SOURCE_UNAVAILABLE, RECORD_NOT_FOUND, DISABLED, NOT_CONFIGURED, RATE_LIMITED, TIMEOUT,
        REQUEST_IN_PROGRESS, REQUEST_KEY_CONFLICT, REQUEST_PREVIOUS_FAILED, REQUEST_OUTCOME_UNKNOWN, RESUME_UNAVAILABLE,
        EVIDENCE_UNAVAILABLE, EVIDENCE_OUT_OF_SCOPE, STEP_LIMIT, TOOL_REJECTED,
        CITATION_INVALID, INVALID_RESPONSE, ENGINE_UNAVAILABLE
    }

    static final class ResearchException extends RuntimeException {
        final Failure failure;
        final List<ResearchStep> trace;

        ResearchException(Failure failure, List<ResearchStep> trace) {
            super(failure.name());
            this.failure = failure;
            this.trace = trace;
        }
    }

    private static final Pattern CALL_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    // 完整证据 ID 由 Java 的时间轴、段落和 PDF 读取器生成。
    private static final Pattern EVIDENCE_ID = Pattern.compile("[1-9][0-9]{0,18}:[a-f0-9]{64}:(?:[1-9][0-9]*|p[1-9][0-9]*:[0-9]+)");
    private final SourceRepository sources;
    private final DocumentEvidenceReader documents;
    private final TranscriptSummaryEngine engine;

    ResearchService(SourceRepository sources, DocumentEvidenceReader documents, TranscriptSummaryEngine engine) {
        this.sources = sources;
        this.documents = documents;
        this.engine = engine;
    }

    /** 先校验范围，再调用 Python，最后按 Java 当前版本回填原句。 */
    Result run(List<Long> ids, String question, CheckpointTarget checkpoint, TranscriptSummaryEngine.Resume resume) {
        if (!sources.allQuestionSourcesAvailable(ids)) throw new ResearchException(Failure.SOURCE_UNAVAILABLE, List.of());
        var reply = invoke(question, ids, checkpoint, resume);
        var draft = reply.draft();
        if (draft == null) throw invalid();
        var scope = ids.stream().map(String::valueOf).collect(Collectors.toSet());
        var read = validateTrace(draft.trace(), scope);
        var trace = List.copyOf(draft.trace());
        if (reply.status() != 200) throw new ResearchException(engineFailure(draft.code()), trace);
        if (draft.code() != null || draft.evidenceIds() == null) throw invalid();
        if ("INSUFFICIENT_EVIDENCE".equals(draft.status()) && draft.answer() == null && draft.evidenceIds().isEmpty()) {
            return new Result(draft.status(), null, List.of(), trace);
        }
        if (!"ANSWERED".equals(draft.status()) || draft.answer() == null || draft.answer().isBlank()
                || draft.answer().length() > 8000 || draft.answer().indexOf(0) >= 0 || draft.evidenceIds().isEmpty()) {
            throw invalid();
        }
        if (!read.containsAll(draft.evidenceIds())) throw new ResearchException(Failure.CITATION_INVALID, trace);
        var citations = draft.evidenceIds().stream().distinct().map(id -> currentCitation(id, trace)).toList();
        return new Result(draft.status(), draft.answer(), citations, trace);
    }

    private TranscriptSummaryEngine.ResearchReply invoke(String question, List<Long> ids, CheckpointTarget checkpoint,
                                                          TranscriptSummaryEngine.Resume resume) {
        try {
            if (resume != null) return engine.resume(question, ids, checkpoint, resume);
            return checkpoint == null ? engine.research(question, ids) : engine.research(question, ids, checkpoint);
        } catch (SummaryException exception) {
            throw new ResearchException(switch (exception.failure) {
                case MODEL_TIMEOUT -> Failure.TIMEOUT;
                case INVALID_OUTPUT -> Failure.INVALID_RESPONSE;
                default -> Failure.ENGINE_UNAVAILABLE;
            }, List.of());
        }
    }

    /** 检查步骤顺序与证据范围，供最终结果和增量检查点共用。 */
    static Set<String> validateTrace(List<ResearchStep> trace, Set<String> scope) {
        if (trace == null || trace.size() > 3) throw invalid();
        Set<String> calls = new HashSet<>();
        Set<String> searched = new HashSet<>();
        Set<String> read = new HashSet<>();
        for (var step : trace) {
            if (step == null || step.callId() == null || !CALL_ID.matcher(step.callId()).matches()
                    || !calls.add(step.callId()) || step.evidenceIds() == null || step.evidenceIds().size() > 6
                    || step.evidenceIds().stream().anyMatch(id -> !inScope(id, scope))) throw invalid();
            if ("search_evidence".equals(step.tool())) {
                searched.addAll(step.evidenceIds());
            } else if ("read_evidence".equals(step.tool()) && step.evidenceIds().size() == 1
                    && searched.contains(step.evidenceIds().getFirst())) {
                read.add(step.evidenceIds().getFirst());
            } else {
                throw invalid();
            }
        }
        return read;
    }

    private static boolean inScope(String id, Set<String> scope) {
        return id != null && id.length() <= 160 && EVIDENCE_ID.matcher(id).matches()
                && scope.contains(id.substring(0, id.indexOf(':')));
    }

    /** 从当前资料版本回填证据正文，供研究结果和已保存记录共用。 */
    Evidence currentCitation(String evidenceId, List<ResearchStep> trace) {
        long sourceId = Long.parseLong(evidenceId.substring(0, evidenceId.indexOf(':')));
        try {
            return documents.readEvidence(sourceId, evidenceId);
        } catch (SourceException exception) {
            throw new ResearchException(Failure.EVIDENCE_UNAVAILABLE, trace);
        }
    }

    private static Failure engineFailure(String code) {
        if (code == null) return Failure.ENGINE_UNAVAILABLE;
        return switch (code) {
            case "RESEARCH_DISABLED" -> Failure.DISABLED;
            case "SUMMARY_MODEL_NOT_CONFIGURED" -> Failure.NOT_CONFIGURED;
            case "MODEL_RATE_LIMITED" -> Failure.RATE_LIMITED;
            case "MODEL_TIMEOUT", "RESEARCH_TIMEOUT", "RESEARCH_CORE_TIMEOUT" -> Failure.TIMEOUT;
            case "RESEARCH_EVIDENCE_UNAVAILABLE", "RESEARCH_EVIDENCE_CHANGED" -> Failure.EVIDENCE_UNAVAILABLE;
            case "RESEARCH_EVIDENCE_OUT_OF_SCOPE" -> Failure.EVIDENCE_OUT_OF_SCOPE;
            case "RESEARCH_STEP_LIMIT", "RESEARCH_INPUT_BUDGET" -> Failure.STEP_LIMIT;
            case "RESEARCH_TOOL_NOT_ALLOWED", "RESEARCH_TOOL_ARGUMENTS_INVALID",
                 "RESEARCH_TOOL_DUPLICATE", "MODEL_TOOL_CALL_INVALID" -> Failure.TOOL_REJECTED;
            case "RESEARCH_CITATION_INVALID" -> Failure.CITATION_INVALID;
            case "RESEARCH_CONTEXT_INVALID" -> Failure.RESUME_UNAVAILABLE;
            case "RESEARCH_OUTPUT_INVALID", "MODEL_RESPONSE_INVALID" -> Failure.INVALID_RESPONSE;
            default -> Failure.ENGINE_UNAVAILABLE;
        };
    }

    private static ResearchException invalid() {
        return new ResearchException(Failure.INVALID_RESPONSE, List.of());
    }
}
