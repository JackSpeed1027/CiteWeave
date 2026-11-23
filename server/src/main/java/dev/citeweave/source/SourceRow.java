package dev.citeweave.source;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 资料行及可选的视频字幕来源投影。 */
@TableName("sources")
class SourceRow {
    @TableId
    Long id;
    String title;
    String kind;
    String filename;
    OffsetDateTime createdAt;
    String content;
    @TableField(exist = false)
    String bvid;
    @TableField(exist = false)
    Integer page;
    @TableField(exist = false)
    String cid;
    @TableField(exist = false)
    String videoTitle;
    @TableField(exist = false)
    String partTitle;
    @TableField(exist = false)
    String trackSource;
    @TableField(exist = false)
    UUID localVideoId;
    @TableField(exist = false)
    String localVideoFilename;
    @TableField(exist = false)
    LocalVideoSubtitleService.TrackSource localTrackSource;

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getKind() { return kind; }
    public String getFilename() { return filename; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    SourceRepository.Summary summary() {
        return new SourceRepository.Summary(id, title, kind, filename, createdAt);
    }

    SourceRepository.Detail detail() {
        var origin = bvid == null ? null
                : new BilibiliSubtitleService.Origin(bvid, page, cid, videoTitle, partTitle, trackSource);
        var localOrigin = localVideoId == null ? null
                : new LocalVideoSubtitleService.Origin(localVideoId, localVideoFilename, localTrackSource);
        return new SourceRepository.Detail(summary(), content, origin, localOrigin);
    }
}
