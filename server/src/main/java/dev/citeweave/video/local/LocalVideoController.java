package dev.citeweave.video.local;

import dev.citeweave.source.LocalVideoSubtitleService;
import dev.citeweave.web.ApiProblems;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 本地媒体预检、确认导入和记录查询。 */
@RestController
class LocalVideoController {
    private final LocalVideoProbe probe;
    private final LocalVideoService videos;
    private final LocalVideoSubtitleService subtitles;
    private final LocalVideoAsrService asr;

    LocalVideoController(LocalVideoProbe probe, LocalVideoService videos, LocalVideoSubtitleService subtitles,
                         LocalVideoAsrService asr) {
        this.probe = probe;
        this.videos = videos;
        this.subtitles = subtitles;
        this.asr = asr;
    }

    @PostMapping(path = "/api/videos/local", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<LocalVideoService.Video> save(@RequestParam MultipartFile file) {
        var saved = videos.importVideo(file);
        var response = saved.created() ? ResponseEntity.created(URI.create("/api/videos/local/" + saved.video().id()))
                : ResponseEntity.ok();
        return response.cacheControl(CacheControl.noStore()).body(saved.video());
    }

    @GetMapping("/api/videos/local")
    ResponseEntity<List<LocalVideoService.Video>> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(videos.list());
    }

    @GetMapping("/api/videos/local/{id}")
    ResponseEntity<LocalVideoService.Video> get(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(videos.get(id));
    }

    @GetMapping("/api/videos/local/{id}/subtitles")
    ResponseEntity<List<LocalVideoSubtitleService.Track>> subtitles(@PathVariable UUID id) {
        videos.get(id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(subtitles.list(id));
    }

    /** 先核对当前工作区可访问该视频，再把字幕绑定为一份正式资料。 */
    @PostMapping(path = "/api/videos/local/{id}/subtitles", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<LocalVideoSubtitleService.Track> uploadSubtitle(@PathVariable UUID id,
                                                                  @RequestParam MultipartFile file) {
        videos.get(id);
        var saved = subtitles.upload(id, file);
        var response = saved.created() ? ResponseEntity.created(URI.create("/api/sources/" + saved.track().sourceId()))
                : ResponseEntity.ok();
        return response.cacheControl(CacheControl.noStore()).body(saved.track());
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail persistenceUnavailable() {
        return previewFailure(new LocalVideoProbe.ProbeException(LocalVideoProbe.Failure.VIDEO_STORAGE_UNAVAILABLE));
    }

    @GetMapping("/api/videos/local/{id}/transcription")
    ResponseEntity<LocalVideoAsrService.Status> transcription(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(asr.status(id));
    }

    /** 同一视频重复提交沿用已有任务，失败后需明确重试。 */
    @PostMapping("/api/videos/local/{id}/transcription")
    ResponseEntity<LocalVideoAsrService.Task> startTranscription(@PathVariable UUID id) {
        return accepted(asr.enqueue(id));
    }

    @PostMapping("/api/videos/local/{id}/transcription/retry")
    ResponseEntity<LocalVideoAsrService.Task> retryTranscription(@PathVariable UUID id) {
        return accepted(asr.retry(id));
    }

    private static ResponseEntity<LocalVideoAsrService.Task> accepted(LocalVideoAsrService.Task task) {
        return ResponseEntity.accepted().location(URI.create("/api/videos/local/" + task.videoId() + "/transcription"))
                .cacheControl(CacheControl.noStore()).body(task);
    }

    @ExceptionHandler(LocalVideoAsrRunner.Failure.class)
    ProblemDetail transcriptionFailure(LocalVideoAsrRunner.Failure failure) {
        String code = failure.getMessage();
        return switch (code) {
            case "ASR_NO_AUDIO" -> ApiProblems.create(HttpStatus.UNPROCESSABLE_ENTITY, code, "视频没有可转写的音轨，可上传字幕。");
            case "ASR_VIDEO_TOO_LONG" -> ApiProblems.create(HttpStatus.UNPROCESSABLE_ENTITY, code, "视频超过自动转写时长限制。");
            case "ASR_TASK_NOT_RETRYABLE" -> ApiProblems.create(HttpStatus.CONFLICT, code, "当前任务状态无法重试。");
            default -> ApiProblems.create(HttpStatus.SERVICE_UNAVAILABLE, code, "本地转写未启用或模型未配置。");
        };
    }

    @PostMapping(path = "/api/videos/local/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<LocalVideoProbe.Preview> preview(@RequestParam MultipartFile file) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(probe.preview(file));
    }

    @ExceptionHandler(LocalVideoProbe.ProbeException.class)
    ProblemDetail previewFailure(LocalVideoProbe.ProbeException error) {
        String code = error.failure.name();
        return switch (error.failure) {
            case VIDEO_EMPTY -> ApiProblems.create(HttpStatus.BAD_REQUEST, code, "请选择非空的视频文件。");
            case VIDEO_TOO_LARGE -> ApiProblems.create(HttpStatus.PAYLOAD_TOO_LARGE, code, "视频超过预检上传上限。");
            case VIDEO_FORMAT_UNSUPPORTED -> ApiProblems.create(HttpStatus.UNSUPPORTED_MEDIA_TYPE, code, "请选择 MP4 或 MOV 文件。");
            case VIDEO_INVALID -> ApiProblems.create(HttpStatus.UNPROCESSABLE_ENTITY, code, "无法读取有效视频轨，请检查文件是否损坏或仅包含音频。");
            case VIDEO_DURATION_EXCEEDED -> ApiProblems.create(HttpStatus.UNPROCESSABLE_ENTITY, code, "视频超过预检时长上限，请选择更短的视频。");
            case VIDEO_PROBE_BUSY -> ApiProblems.create(HttpStatus.SERVICE_UNAVAILABLE, code, "视频检查任务已满，请稍后重试。");
            case VIDEO_PROBE_TIMEOUT -> ApiProblems.create(HttpStatus.GATEWAY_TIMEOUT, code, "视频检查超时，请用更小的视频重试。");
            case VIDEO_PROBE_UNAVAILABLE -> ApiProblems.create(HttpStatus.SERVICE_UNAVAILABLE, code, "视频检查暂不可用，请确认 Java 服务能启动 FFprobe。");
            case VIDEO_STORAGE_UNAVAILABLE -> ApiProblems.create(HttpStatus.SERVICE_UNAVAILABLE, code, "视频存储暂不可用，请检查服务后重试。");
            case VIDEO_NOT_FOUND -> ApiProblems.create(HttpStatus.NOT_FOUND, code, "找不到这条本地视频记录。");
        };
    }
}
