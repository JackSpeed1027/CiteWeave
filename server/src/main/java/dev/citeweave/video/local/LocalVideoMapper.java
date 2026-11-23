package dev.citeweave.video.local;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 通过内容摘要约束处理并发导入，按工作区查询已经提交的记录。 */
@Mapper
interface LocalVideoMapper extends BaseMapper<LocalVideoMapper.VideoRow> {
    int insertIfAbsent(@Param("video") VideoRow video);
    List<VideoRow> recent(@Param("workspace") String workspace, @Param("limit") int limit);

    @TableName("local_videos")
    class VideoRow {
        @TableId(type = IdType.INPUT)
        UUID id;
        String workspaceKey;
        String filename;
        String sha256;
        long sizeBytes;
        long durationMs;
        int width;
        int height;
        String videoCodec;
        String audioCodec;
        OffsetDateTime createdAt;

        public UUID getId() { return id; }
        public String getWorkspaceKey() { return workspaceKey; }
        public String getSha256() { return sha256; }

        LocalVideoService.Video view() {
            return new LocalVideoService.Video(id, filename, sha256, createdAt,
                    new LocalVideoProbe.Preview(sizeBytes, durationMs, width, height, videoCodec, audioCodec));
        }
    }
}
