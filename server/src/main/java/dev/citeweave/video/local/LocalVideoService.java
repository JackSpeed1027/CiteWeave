package dev.citeweave.video.local;

import static dev.citeweave.video.local.LocalVideoProbe.Failure.VIDEO_NOT_FOUND;
import static dev.citeweave.video.local.LocalVideoProbe.Failure.VIDEO_STORAGE_UNAVAILABLE;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import dev.citeweave.video.local.LocalVideoMapper.VideoRow;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 提交已验证的文件并用 PostgreSQL 保存可恢复的导入记录。 */
@Service
class LocalVideoService {
    record Video(UUID id, String filename, String sha256, OffsetDateTime createdAt, LocalVideoProbe.Preview metadata) {}
    record Import(Video video, boolean created) {}

    private static final String PERSONAL_WORKSPACE = "local-personal";
    private static final int RECENT_LIMIT = 50;
    private static final int MAX_FILENAME = 255;
    private final LocalVideoProbe probe;
    private final LocalVideoStorage storage;
    private final LocalVideoMapper videos;

    LocalVideoService(LocalVideoProbe probe, LocalVideoStorage storage, LocalVideoMapper videos) {
        this.probe = probe;
        this.storage = storage;
        this.videos = videos;
    }

    /** 先发布完整对象，再提交元数据。数据库失败时，相同文件重试可复用已发布对象。 */
    Import importVideo(MultipartFile file) {
        return probe.inspect(file, storage.stagingDirectory(), (staged, metadata) -> {
            String digest = storage.publish(staged);
            var row = new VideoRow();
            row.id = UUID.randomUUID();
            row.workspaceKey = PERSONAL_WORKSPACE;
            row.filename = displayName(file.getOriginalFilename());
            row.sha256 = digest;
            row.sizeBytes = metadata.sizeBytes();
            row.durationMs = metadata.durationMs();
            row.width = metadata.width();
            row.height = metadata.height();
            row.videoCodec = metadata.videoCodec();
            row.audioCodec = metadata.audioCodec();
            boolean created = videos.insertIfAbsent(row) == 1;
            // 冲突后建立新查询快照，读回另一请求已经提交的记录。
            var saved = videos.selectOne(Wrappers.lambdaQuery(VideoRow.class)
                    .eq(VideoRow::getWorkspaceKey, PERSONAL_WORKSPACE).eq(VideoRow::getSha256, digest));
            if (saved == null) throw new LocalVideoProbe.ProbeException(VIDEO_STORAGE_UNAVAILABLE);
            return new Import(saved.view(), created);
        });
    }

    List<Video> list() {
        return videos.recent(PERSONAL_WORKSPACE, RECENT_LIMIT).stream().map(VideoRow::view).toList();
    }

    Video get(UUID id) {
        var row = videos.selectOne(Wrappers.lambdaQuery(VideoRow.class)
                .eq(VideoRow::getWorkspaceKey, PERSONAL_WORKSPACE).eq(VideoRow::getId, id));
        if (row == null) throw new LocalVideoProbe.ProbeException(VIDEO_NOT_FOUND);
        storage.requireObject(row.sha256, row.sizeBytes);
        return row.view();
    }

    private static String displayName(String name) {
        String normalized = name.replace('\\', '/');
        String leaf = normalized.substring(normalized.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").strip();
        if (leaf.length() <= MAX_FILENAME) return leaf;
        int start = leaf.length() - MAX_FILENAME;
        return leaf.substring(Character.isLowSurrogate(leaf.charAt(start)) ? start + 1 : start);
    }
}
