package dev.citeweave.video.local;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 转写队列的原子状态转换。 */
@Mapper
interface LocalVideoAsrMapper extends BaseMapper<LocalVideoAsrMapper.Job> {
    void enqueue(@Param("id") UUID id);
    Job claim(@Param("leaseSeconds") int leaseSeconds);
    void expire();
    boolean retry(@Param("id") UUID id);
    boolean complete(@Param("id") UUID id, @Param("attempt") int attempt, @Param("sourceId") long sourceId);
    void fail(@Param("id") UUID id, @Param("attempt") int attempt, @Param("error") String error);

    @TableName("local_video_asr_tasks")
    class Job {
        @TableId(type = IdType.INPUT)
        UUID videoId;
        String status;
        int attempt;
        String errorCode;
        Long sourceId;
        OffsetDateTime updatedAt;
        @TableField(select = false)
        OffsetDateTime leaseUntil;

        LocalVideoAsrService.Task view() {
            return new LocalVideoAsrService.Task(videoId, status, attempt, errorCode, sourceId, updatedAt);
        }
    }
}
