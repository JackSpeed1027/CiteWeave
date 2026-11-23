package dev.citeweave.source;

import static dev.citeweave.source.SourceException.Code.SOURCE_NOT_FOUND;
import static dev.citeweave.source.SourceException.Code.SOURCE_SUBTITLE_OUTSIDE_VIDEO;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import dev.citeweave.source.LocalVideoTrackMapper.Binding;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 将已授权视频与不可变字幕证据关联，沿用原资料与摘要接口。 */
@Service
public class LocalVideoSubtitleService {
    private static final int RECENT_LIMIT = 50;
    private final SourceService sources;
    private final SourceRepository repository;
    private final SourceParser parser;
    private final LocalVideoTrackMapper tracks;

    LocalVideoSubtitleService(SourceService sources, SourceRepository repository,
                              SourceParser parser, LocalVideoTrackMapper tracks) {
        this.sources = sources;
        this.repository = repository;
        this.parser = parser;
        this.tracks = tracks;
    }

    public enum TrackSource { USER_UPLOAD, MACHINE_ASR }

    public record Track(long sourceId, String title, String filename, String kind, TrackSource trackSource) {}
    public record Uploaded(Track track, boolean created) {}
    public record Origin(UUID videoId, String filename, TrackSource trackSource) {}

    /** 调用方先核对视频归属。正文验证完成后再持有视频行锁，并原子写入字幕和绑定。 */
    @Transactional
    public Uploaded upload(UUID videoId, MultipartFile file) {
        return bind(videoId, sources.prepareSubtitle(file), TrackSource.USER_UPLOAD);
    }

    /** 机器字幕和任务终态必须由调用方放在同一事务内。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public Track publishMachine(UUID videoId, String vtt) {
        String filename = "machine-captions.vtt";
        var parsed = parser.file(filename, vtt.getBytes(StandardCharsets.UTF_8));
        return bind(videoId, new SourceService.PreparedUpload("本地视频机器字幕", filename, parsed),
                TrackSource.MACHINE_ASR).track();
    }

    private Uploaded bind(UUID videoId, SourceService.PreparedUpload input, TrackSource origin) {
        var parsed = input.parsed();
        long lastEnd = parser.transcript(parsed.kind(), parsed.content()).stream()
                .mapToLong(TranscriptParser.Segment::endMs).max().orElseThrow();
        String version = TranscriptEvidenceReader.transcriptVersion(parsed.kind(), parsed.content());

        Long duration = tracks.lockDuration(videoId);
        if (duration == null) throw new SourceException(SOURCE_NOT_FOUND, "找不到字幕对应的视频。");
        if (lastEnd > duration) {
            throw new SourceException(SOURCE_SUBTITLE_OUTSIDE_VIDEO, "字幕结束时间超过视频时长，请检查对应的视频版本。");
        }
        // 在 PostgreSQL 默认 READ COMMITTED 下，持锁后查询能看到前一个成功提交的绑定。
        var existing = tracks.selectOne(Wrappers.lambdaQuery(Binding.class)
                .eq(Binding::getVideoId, videoId).eq(Binding::getSourceVersion, version)
                .eq(Binding::getTrackSource, origin));
        if (existing != null) return new Uploaded(view(sources.find(existing.sourceId).source(), origin), false);

        var source = repository.insert(input.title(), parsed.kind(), input.filename(), parsed.content()).source();
        tracks.insert(new Binding(source.id(), videoId, version, origin));
        return new Uploaded(view(source, origin), true);
    }

    /** 列出指定视频的最近字幕，调用方先检查视频归属。 */
    public List<Track> list(UUID videoId) {
        return tracks.listSources(videoId, RECENT_LIMIT).stream()
                .map(row -> view(row.summary(), row.localTrackSource)).toList();
    }

    private static Track view(SourceRepository.Summary summary, TrackSource origin) {
        return new Track(summary.id(), summary.title(), summary.filename(), summary.kind(), origin);
    }
}
