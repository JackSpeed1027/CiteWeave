package dev.citeweave.source;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 单表保存导入字幕绑定的分 P 与轨道信息。 */
@Mapper
interface BilibiliTrackMapper extends BaseMapper<BilibiliTrackMapper.TrackRow> {
    @TableName("bilibili_subtitle_tracks")
    class TrackRow {
        @TableId(type = IdType.INPUT)
        Long sourceId;
        String bvid;
        int page;
        String cid;
        String videoTitle;
        String partTitle;
        String trackSource;

        TrackRow() {}

        TrackRow(long sourceId, BilibiliSubtitleService.Origin origin) {
            this.sourceId = sourceId;
            bvid = origin.bvid();
            page = origin.page();
            cid = origin.cid();
            videoTitle = origin.videoTitle();
            partTitle = origin.partTitle();
            trackSource = origin.trackSource();
        }
    }
}
