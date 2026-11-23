package dev.citeweave.source;

import dev.citeweave.source.ResearchHistoryRepository.Header;
import dev.citeweave.source.ResearchHistoryRepository.Context;
import dev.citeweave.source.ResearchHistoryRepository.PublishRejected;
import dev.citeweave.source.ResearchHistoryRepository.Snapshot;
import dev.citeweave.source.ResearchService.Failure;
import dev.citeweave.source.ResearchService.ResearchException;
import dev.citeweave.source.TranscriptSummaryEngine.Evidence;
import dev.citeweave.source.TranscriptSummaryEngine.CheckpointTarget;
import dev.citeweave.source.TranscriptSummaryEngine.ResearchStep;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** 保存经过复核的终态工作，并在读回时重新检查原资料。 */
@Service
class ResearchHistoryService {
    record Saved(Header record, List<Long> sourceIds, String status, String answer,
                 List<Evidence> citations, List<ResearchStep> trace) {}
    record RequestStatus(String status, String question, List<Long> sourceIds, List<ResearchStep> trace,
                         UUID recordId, UUID nextRequestKey, boolean canResume) {}

    private static final String WORKSPACE = "local-personal";
    enum CheckpointStatus { SAVED, NOT_FOUND, CONFLICT, INVALID }
    private final ResearchHistoryRepository history;
    private final SourceRepository sources;
    private final ResearchService research;

    ResearchHistoryService(ResearchHistoryRepository history, SourceRepository sources, ResearchService research) {
        this.history = history;
        this.sources = sources;
        this.research = research;
    }

    /** 有请求键时先认领，再调用模型。只有完整结果才进入历史。 */
    Saved run(List<Long> ids, String question, UUID key) {
        String fingerprint = key == null ? null : fingerprint(ids, question);
        CheckpointTarget target = key == null ? null : new CheckpointTarget(key, UUID.randomUUID());
        if (key != null) {
            if (!history.claim(WORKSPACE, key, fingerprint, digest(target.token().toString()), ids, question)) {
                return replay(key, fingerprint);
            }
        }
        return execute(ids, question, key, fingerprint, target, null);
    }

    /** 仅从同一工作区、相同输入的未知结果恢复。续跑用新授权，旧请求状态不变。 */
    Saved resume(List<Long> ids, String question, UUID key, UUID parent) {
        String input = fingerprint(ids, question);
        String fingerprint = digest(parent + "\n" + input);
        var existing = history.claimed(WORKSPACE, key);
        if (existing.isPresent()) return replay(key, fingerprint);
        var prior = history.claimed(WORKSPACE, parent).orElseThrow(() -> conflict(Failure.RESUME_UNAVAILABLE));
        if ("RUNNING".equals(prior.status()) && !prior.active()) {
            history.expireIfDue(WORKSPACE, parent);
            prior = history.claimed(WORKSPACE, parent).orElseThrow(() -> conflict(Failure.RESUME_UNAVAILABLE));
        }
        // 对续跑再次中断的情况，父请求绑定了它自己的祖先，原输入仍需独立校验。
        if (!"UNKNOWN".equals(prior.status()) || !input.equals(prior.inputFingerprint())
                || prior.context() == null || prior.trace().isEmpty() || !ids.equals(prior.scope())) {
            throw conflict(Failure.RESUME_UNAVAILABLE);
        }
        var target = new CheckpointTarget(key, UUID.randomUUID());
        if (!history.claimResume(WORKSPACE, key, parent, fingerprint, digest(target.token().toString()))) {
            if (history.claimed(WORKSPACE, key).isPresent()) return replay(key, fingerprint);
            throw conflict(Failure.RESUME_UNAVAILABLE);
        }
        return execute(ids, question, key, fingerprint, target, new TranscriptSummaryEngine.Resume(prior.trace(), prior.context()));
    }

    /** 工作区内恢复原输入与轨迹；读取只返回浏览器恢复页面需要的字段。 */
    RequestStatus status(UUID key) {
        var view = history.requestView(WORKSPACE, key).orElseThrow(() -> conflict(Failure.RECORD_NOT_FOUND));
        if (!sources.allQuestionSourcesAvailable(view.sourceIds())) throw conflict(Failure.SOURCE_UNAVAILABLE);
        boolean canResume = "UNKNOWN".equals(view.status()) && view.hasCheckpoint()
                && !view.trace().isEmpty() && view.nextRequestKey() == null;
        return new RequestStatus(view.status(), view.question(), view.sourceIds(), view.trace(),
                view.recordId(), view.nextRequestKey(), canResume);
    }

    private Saved execute(List<Long> ids, String question, UUID key, String fingerprint,
                          CheckpointTarget target, TranscriptSummaryEngine.Resume resume) {
        ResearchService.Result result;
        try {
            result = research.run(ids, question, target, resume);
            if (key != null && !checkpointTrace(key).equals(result.trace())) {
                throw new ResearchException(Failure.INVALID_RESPONSE, List.of());
            }
        } catch (ResearchException error) {
            if (key == null) throw error;
            closeFailedRequest(key, error);
            // 对有键运行，只回传数据库已确认的轨迹，包括首次收到业务失败时。
            throw new ResearchException(error.failure, checkpointTrace(key));
        }
        var snapshot = new Snapshot(result.status(), result.answer(),
                result.citations().stream().map(Evidence::evidenceId).toList(), result.trace());
        Header record;
        try {
            record = key == null ? history.insert(WORKSPACE, ids, question, snapshot)
                    : history.complete(WORKSPACE, key, ids, question, snapshot);
        } catch (PublishRejected error) {
            return replay(key, fingerprint);
        }
        return new Saved(record, ids, result.status(), result.answer(), result.citations(), result.trace());
    }

    private void closeFailedRequest(UUID key, ResearchException error) {
        // 超时与连接异常无法证明上游没收到请求。保留同一键并显式报告结果未知。
        if (error.failure == Failure.TIMEOUT || error.failure == Failure.ENGINE_UNAVAILABLE) {
            history.markUnknown(WORKSPACE, key);
            throw new ResearchException(Failure.REQUEST_OUTCOME_UNKNOWN, checkpointTrace(key));
        }
        if (!history.fail(WORKSPACE, key)) {
            history.expireIfDue(WORKSPACE, key);
            throw new ResearchException(Failure.REQUEST_OUTCOME_UNKNOWN, checkpointTrace(key));
        }
    }

    private List<ResearchStep> checkpointTrace(UUID key) {
        return history.claimed(WORKSPACE, key).map(ResearchHistoryRepository.RequestClaim::trace).orElse(List.of());
    }

    /** 从可信运行绑定的范围验证累计轨迹，每次只接受一个新步骤。 */
    CheckpointStatus checkpoint(UUID key, String token, List<ResearchStep> incoming, Context context) {
        if (token == null || token.length() != 36) return CheckpointStatus.NOT_FOUND;
        byte[] provided = digest(token).getBytes(StandardCharsets.US_ASCII);
        for (int attempt = 0; attempt < 2; attempt++) {
            var claim = history.claimed(WORKSPACE, key).orElse(null);
            if (claim == null || claim.tokenDigest() == null || !MessageDigest.isEqual(
                    provided, claim.tokenDigest().getBytes(StandardCharsets.US_ASCII))) return CheckpointStatus.NOT_FOUND;
            if (!claim.active()) return CheckpointStatus.CONFLICT;
            if (incoming == null || incoming.isEmpty() || incoming.size() > 3) return CheckpointStatus.INVALID;
            var previous = claim.trace();
            int shared = Math.min(previous.size(), incoming.size());
            if (!validContext(incoming, context)) return CheckpointStatus.INVALID;
            if (!previous.subList(0, shared).equals(incoming.subList(0, shared))
                    || (shared > 0 && (claim.context() == null || !claim.context().actions().subList(0, shared)
                    .equals(context.actions().subList(0, shared))))) return CheckpointStatus.CONFLICT;
            if (incoming.size() <= previous.size()) return CheckpointStatus.SAVED;
            if (incoming.size() != previous.size() + 1) return CheckpointStatus.CONFLICT;
            try {
                ResearchService.validateTrace(incoming, claim.scope().stream().map(String::valueOf)
                        .collect(Collectors.toSet()));
            } catch (ResearchException exception) {
                return CheckpointStatus.INVALID;
            }
            if (history.appendCheckpoint(WORKSPACE, key, claim, incoming, context)) return CheckpointStatus.SAVED;
        }
        return CheckpointStatus.CONFLICT;
    }

    /** 在保存前核对可重建的工具参数，避免把任意模型消息或伪造预算写入检查点。 */
    private static boolean validContext(List<ResearchStep> trace, Context context) {
        if (context == null || !Objects.equals(context.version(), 1)
                || !Objects.equals(context.remainingToolSteps(), 3 - trace.size())
                || context.actions() == null || context.actions().size() != trace.size()) return false;
        for (int index = 0; index < trace.size(); index++) {
            var step = trace.get(index);
            var action = context.actions().get(index);
            if (step == null || action == null) return false;
            if ("search_evidence".equals(step.tool())) {
                if (action.query() == null || action.query().isBlank() || action.query().length() > 500
                        || action.query().indexOf(0) >= 0 || action.sourceId() != null || action.evidenceId() != null) {
                    return false;
                }
            } else if (!"read_evidence".equals(step.tool()) || action.query() != null || action.sourceId() == null
                    || action.evidenceId() == null || !action.evidenceId().startsWith(action.sourceId() + ":")
                    || !List.of(action.evidenceId()).equals(step.evidenceIds())) {
                return false;
            }
        }
        return true;
    }

    private Saved replay(UUID key, String fingerprint) {
        var claim = history.claimed(WORKSPACE, key).orElseThrow(() -> conflict(Failure.REQUEST_IN_PROGRESS));
        if (!claim.fingerprint().equals(fingerprint)) throw conflict(Failure.REQUEST_KEY_CONFLICT);
        if ("RUNNING".equals(claim.status())) {
            history.expireIfDue(WORKSPACE, key);
            // 更新与发布存在竞争。再读一次，以数据库观测到的最新状态为准。
            claim = history.claimed(WORKSPACE, key).orElseThrow(() -> conflict(Failure.REQUEST_IN_PROGRESS));
        }
        return switch (claim.status()) {
            case "COMPLETED" -> get(claim.recordId());
            case "FAILED" -> throw new ResearchException(Failure.REQUEST_PREVIOUS_FAILED, claim.trace());
            case "UNKNOWN" -> throw new ResearchException(Failure.REQUEST_OUTCOME_UNKNOWN, claim.trace());
            default -> throw new ResearchException(Failure.REQUEST_IN_PROGRESS, claim.trace());
        };
    }

    private static ResearchException conflict(Failure failure) {
        return new ResearchException(failure, List.of());
    }

    private static String fingerprint(List<Long> ids, String question) {
        // 顺序会影响候选排序。哈希使用 HTTP 层去除首尾空白后的问题及有序 ID。
        return digest(ids + "\n" + question);
    }

    private static String digest(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** 列表只读取元数据，不调用模型。 */
    List<Header> recent() {
        return history.recent(WORKSPACE);
    }

    /** 保存的引用仍指向原始版本；只有当前证据校验通过才回填正文。 */
    Saved get(UUID id) {
        var entry = history.find(WORKSPACE, id)
                .orElseThrow(() -> new ResearchException(Failure.RECORD_NOT_FOUND, List.of()));
        if (!sources.allQuestionSourcesAvailable(entry.sourceIds())) {
            throw new ResearchException(Failure.SOURCE_UNAVAILABLE, List.of());
        }
        var snapshot = entry.snapshot();
        var citations = snapshot.evidenceIds().stream()
                .map(evidenceId -> research.currentCitation(evidenceId, List.of())).toList();
        return new Saved(entry.header(), entry.sourceIds(), snapshot.status(), snapshot.answer(), citations, snapshot.trace());
    }
}
