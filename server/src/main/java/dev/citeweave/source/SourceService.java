package dev.citeweave.source;

import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import static dev.citeweave.source.SourceException.Code.*;

@Service
class SourceService {
    record PreparedUpload(String title, String filename, SourceParser.Parsed parsed) {}

    private static final String LOCAL_WORKSPACE = "local-personal";
    private final SourceRepository repository;
    private final SourceParser parser;

    SourceService(SourceRepository repository, SourceParser parser) {
        this.repository = repository;
        this.parser = parser;
    }

    List<SourceRepository.Summary> list() {
        return repository.list();
    }

    /** 当前只开放本机个人空间，工作区不从请求读取。 */
    List<SourceRepository.Summary> listFavorites() {
        return repository.listFavorites(LOCAL_WORKSPACE);
    }

    boolean favorite(long id) {
        return repository.favorite(LOCAL_WORKSPACE, id).orElseThrow(SourceService::notFound);
    }

    /** 以显式目标状态保存或取消收藏，资料正文无需读写。 */
    boolean setFavorite(long id, boolean favorited) {
        return repository.setFavorite(LOCAL_WORKSPACE, id, favorited).orElseThrow(SourceService::notFound);
    }

    SourceRepository.Detail find(long id) {
        return repository.find(id).orElseThrow(SourceService::notFound);
    }

    private static SourceException notFound() {
        return new SourceException(SOURCE_NOT_FOUND, "找不到这份资料。");
    }

    List<TranscriptParser.Segment> transcript(long id) {
        var detail = find(id);
        String kind = detail.source().kind();
        if (!parser.isTranscript(kind)) {
            throw new SourceException(SOURCE_TRANSCRIPT_NOT_FOUND, "这份资料没有字幕轨道。");
        }
        return parser.transcript(kind, detail.content());
    }

    SourceRepository.Detail paste(String title, String content) {
        return repository.insert(validTitle(title), "TEXT", null, parser.text(content));
    }

    SourceRepository.Detail upload(String title, MultipartFile file) {
        return upload(title, file, false);
    }

    /** 视频导入只复用字幕解析分支。 */
    SourceRepository.Detail uploadSubtitle(MultipartFile file) {
        return upload(null, file, true);
    }

    private SourceRepository.Detail upload(String title, MultipartFile file, boolean subtitleOnly) {
        var upload = prepareUpload(title, file, subtitleOnly);
        var parsed = upload.parsed();
        if (parsed.kind().equals("PDF")) return repository.insertPdf(upload.title(), upload.filename(), parsed);
        return repository.insert(upload.title(), parsed.kind(), upload.filename(), parsed.content());
    }

    /** 在写入前验证字幕，使视频绑定能先检查整个时间轴。 */
    PreparedUpload prepareSubtitle(MultipartFile file) {
        return prepareUpload(null, file, true);
    }

    private PreparedUpload prepareUpload(String title, MultipartFile file, boolean subtitleOnly) {
        String originalName = file.getOriginalFilename();
        String filename = originalName == null ? "" : originalName.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1).strip();
        if (file.isEmpty() || filename.isBlank() || filename.length() > 255
                || filename.indexOf('.') <= 0 || filename.indexOf(0) >= 0) {
            throw new SourceException(SOURCE_FILE_INVALID, "请选择具有有效文件名的非空文件。");
        }
        if (subtitleOnly && !filename.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(srt|vtt)$")) {
            throw new SourceException(SOURCE_FILE_TYPE_UNSUPPORTED, "请为视频上传 SRT 或 VTT 字幕。");
        }
        String resolvedTitle = title == null || title.isBlank()
                ? filename.substring(0, filename.lastIndexOf('.')) : title;
        resolvedTitle = validTitle(resolvedTitle);
        try {
            return new PreparedUpload(resolvedTitle, filename, parser.file(filename, file.getBytes()));
        } catch (IOException exception) {
            throw new SourceException(SOURCE_FILE_UNREADABLE, "无法读取上传的文件。", exception);
        }
    }

    private String validTitle(String title) {
        String value = title == null ? "" : title.strip();
        if (value.isEmpty() || value.length() > 160 || value.indexOf(0) >= 0) {
            throw new SourceException(SOURCE_TITLE_INVALID, "标题长度须为 1–160 字符。");
        }
        return value;
    }
}
