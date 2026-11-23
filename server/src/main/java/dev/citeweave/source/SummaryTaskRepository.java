package dev.citeweave.source;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

/** 将持久化行映射为任务，数据库状态转换由 Mapper 执行。 */
@Repository
class SummaryTaskRepository {
    record Task(UUID id, long sourceId, String status, int attempt, String errorCode, OffsetDateTime updatedAt) {}

    private static final int RECENT_LIMIT = 50;
    private final SummaryTaskMapper mapper;
    private final int leaseSeconds;

    SummaryTaskRepository(SummaryTaskMapper mapper, @Value("${citeweave.tasks.lease-seconds}") int leaseSeconds) {
        if (leaseSeconds <= 0) throw new IllegalArgumentException("citeweave.tasks.lease-seconds must be positive");
        this.mapper = mapper;
        this.leaseSeconds = leaseSeconds;
    }

    /** 同一份不可变字幕最多一个任务，冲突后从新查询读回已有行。 */
    Task enqueue(long sourceId) {
        var row = mapper.insertQueued(UUID.randomUUID(), sourceId);
        if (row == null) {
            row = mapper.selectOne(Wrappers.lambdaQuery(SummaryTaskRow.class)
                    .eq(SummaryTaskRow::getSourceId, sourceId));
        }
        return row.toTask();
    }

    List<Task> list() {
        return mapper.listRecent(RECENT_LIMIT).stream().map(SummaryTaskRow::toTask).toList();
    }

    Optional<Task> find(UUID id) {
        return Optional.ofNullable(mapper.selectById(id)).map(SummaryTaskRow::toTask);
    }

    void expire() {
        mapper.expire();
    }

    Optional<Task> claim() {
        return Optional.ofNullable(mapper.claim(leaseSeconds)).map(SummaryTaskRow::toTask);
    }

    void fail(Task task, String error) {
        mapper.fail(task.id(), task.attempt(), error);
    }

    Optional<Task> retry(UUID id) {
        return Optional.ofNullable(mapper.retry(id)).map(SummaryTaskRow::toTask);
    }
}
