package dev.citeweave.source;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.citeweave.source.ResearchHistoryMapper.HistoryRow;
import dev.citeweave.source.TranscriptSummaryEngine.ResearchStep;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 按工作区存取终态研究快照。正文仍由原资料提供。 */
@Repository
class ResearchHistoryRepository {
    record Header(UUID id, String question, OffsetDateTime createdAt) {}
    record Snapshot(String status, String answer, List<String> evidenceIds, List<ResearchStep> trace) {}
    record Entry(Header header, List<Long> sourceIds, Snapshot snapshot) {}
    record Action(String query, String sourceId, String evidenceId) {}
    record Context(Integer version, Integer remainingToolSteps, List<Action> actions) {}
    record RequestClaim(String fingerprint, String inputFingerprint, String status, UUID recordId, String tokenDigest,
                        List<Long> scope, List<ResearchStep> trace, Context context, boolean active) {}
    record RequestView(String status, String question, List<Long> sourceIds, List<ResearchStep> trace,
                       UUID recordId, UUID nextRequestKey, boolean hasCheckpoint) {}
    static final class PublishRejected extends IllegalStateException {}

    private static final int RECENT_LIMIT = 20;
    private static final TypeReference<List<Long>> SOURCE_IDS = new TypeReference<>() {};
    private static final TypeReference<List<ResearchStep>> TRACE = new TypeReference<>() {};
    private static final TypeReference<Context> CONTEXT = new TypeReference<>() {};
    private static final TypeReference<Snapshot> SNAPSHOT = new TypeReference<>() {};
    private final ResearchHistoryMapper history;
    private final ObjectMapper mapper;

    ResearchHistoryRepository(ResearchHistoryMapper history, ObjectMapper mapper) {
        this.history = history;
        this.mapper = mapper;
    }

    /** 在单个写语句中提交完整快照和资料范围。 */
    Header insert(String workspace, List<Long> sourceIds, String question, Snapshot snapshot) {
        return history.insertSnapshot(UUID.randomUUID(), workspace, question, encode(sourceIds), encode(snapshot))
                .header();
    }

    /** 仅唯一键赢家可进入模型调用。此写入在调用前提交。 */
    boolean claim(String workspace, UUID key, String fingerprint, String tokenDigest, List<Long> scope, String question) {
        return history.claim(workspace, key, fingerprint, tokenDigest, encode(scope), question) == 1;
    }

    /** 用新键认领同一断点。数据库约束保证同一父请求只有一个直接续跑。 */
    boolean claimResume(String workspace, UUID key, UUID parent, String fingerprint, String tokenDigest) {
        return history.claimResume(workspace, key, parent, fingerprint, tokenDigest) == 1;
    }

    /** 刷新仅查询状态。对已过期的请求在读取时显示 UNKNOWN，不推进运行。 */
    Optional<RequestView> requestView(String workspace, UUID key) {
        return Optional.ofNullable(history.requestView(workspace, key)).map(row ->
                new RequestView(row.status, row.question, decode(row.checkpointScope, SOURCE_IDS),
                        decode(row.checkpointTrace, TRACE), row.recordId, row.nextKey, row.hasCheckpoint));
    }

    /** 争用失败后用新语句读数据库中的赢家状态。 */
    Optional<RequestClaim> claimed(String workspace, UUID key) {
        return Optional.ofNullable(history.claimed(workspace, key)).map(row ->
                new RequestClaim(row.fingerprint, row.inputFingerprint, row.status, row.recordId,
                        row.checkpointTokenDigest, decode(row.checkpointScope, SOURCE_IDS),
                        decode(row.checkpointTrace, TRACE), decode(row.checkpointContext, CONTEXT), row.active));
    }

    /** 只追加到已验证的旧轨迹。并发、结束或期限变化时由调用方重新判断。 */
    boolean appendCheckpoint(String workspace, UUID key, RequestClaim before, List<ResearchStep> after, Context context) {
        return history.appendCheckpoint(workspace, key, encode(before.trace()),
                before.context() == null ? null : encode(before.context()), encode(after), encode(context)) == 1;
    }

    /** 保存记录与发布请求终态属于同一个短事务。模型调用在事务之外。 */
    @Transactional
    Header complete(String workspace, UUID key, List<Long> sourceIds, String question, Snapshot snapshot) {
        var saved = insert(workspace, sourceIds, question, snapshot);
        int changed = history.completeRequest(workspace, key, saved.id(), encode(snapshot.trace()));
        // 异常会回滚前面的快照。过期或未与最新检查点对齐的执行无法发布。
        if (changed != 1) throw new PublishRejected();
        return saved;
    }

    /** 已知业务失败关闭同一请求键，后续新一轮执行需要新键。 */
    boolean fail(String workspace, UUID key) {
        return history.fail(workspace, key) == 1;
    }

    /** 重放发现过期时单调转换状态，不能覆盖已发布结果。 */
    void expireIfDue(String workspace, UUID key) {
        history.expireIfDue(workspace, key);
    }

    /** 上游调用结果不确定时，保留请求键避免自动重跑。 */
    void markUnknown(String workspace, UUID key) {
        history.markUnknown(workspace, key);
    }

    /** 按写入时间及稳定的 UUID 次序展示最近记录。 */
    List<Header> recent(String workspace) {
        return history.recent(workspace, RECENT_LIMIT).stream().map(HistoryRow::header).toList();
    }

    /** 工作区与记录 ID 必须同时匹配。 */
    Optional<Entry> find(String workspace, UUID id) {
        return Optional.ofNullable(history.selectOne(Wrappers.lambdaQuery(HistoryRow.class)
                .eq(HistoryRow::getWorkspaceKey, workspace).eq(HistoryRow::getId, id)))
                .map(row -> new Entry(row.header(), decode(row.sourceIds, SOURCE_IDS), decode(row.snapshot, SNAPSHOT)));
    }

    private String encode(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not encode research snapshot", exception);
        }
    }

    private <T> T decode(String json, TypeReference<T> type) {
        if (json == null) return null;
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid research snapshot", exception);
        }
    }
}
