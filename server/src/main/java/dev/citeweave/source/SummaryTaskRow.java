package dev.citeweave.source;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 映射任务表的持久化字段，供 MyBatis-Plus 单表查询使用。 */
@TableName("summary_tasks")
class SummaryTaskRow {
    @TableId(type = IdType.INPUT)
    private UUID id;
    private long sourceId;
    private String status;
    private int attempt;
    private String errorCode;
    private OffsetDateTime updatedAt;

    public long getSourceId() {
        return sourceId;
    }

    SummaryTaskRepository.Task toTask() {
        return new SummaryTaskRepository.Task(id, sourceId, status, attempt, errorCode, updatedAt);
    }
}
