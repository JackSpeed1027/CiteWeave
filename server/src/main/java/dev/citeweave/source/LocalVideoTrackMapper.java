package dev.citeweave.source;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 查询视频字幕轨道，并以视频行锁串行化同一视频的绑定。 */
@Mapper
interface LocalVideoTrackMapper extends BaseMapper<LocalVideoTrackMapper.Binding> {
    Long lockDuration(@Param("videoId") UUID videoId);
    List<SourceRow> listSources(@Param("videoId") UUID videoId, @Param("limit") int limit);

    @TableName("local_video_subtitle_tracks")
    class Binding {
        @TableId(type = IdType.INPUT)
        Long sourceId;
        UUID videoId;
        String sourceVersion;
        LocalVideoSubtitleService.TrackSource trackSource;

        Binding(long sourceId, UUID videoId, String sourceVersion, LocalVideoSubtitleService.TrackSource trackSource) {
            this.sourceId = sourceId;
            this.videoId = videoId;
            this.sourceVersion = sourceVersion;
            this.trackSource = trackSource;
        }

        public Binding() {}
        public UUID getVideoId() { return videoId; }
        public String getSourceVersion() { return sourceVersion; }
        public LocalVideoSubtitleService.TrackSource getTrackSource() { return trackSource; }
    }
}
