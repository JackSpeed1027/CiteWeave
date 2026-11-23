package dev.citeweave.video.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/** 本地视频预检。FFprobe 仅接收服务端创建的临时文件路径。 */
@Service
class LocalVideoProbe {
    record Preview(long sizeBytes, long durationMs, int width, int height, String videoCodec, String audioCodec) {}
    enum Failure { VIDEO_EMPTY, VIDEO_TOO_LARGE, VIDEO_FORMAT_UNSUPPORTED, VIDEO_INVALID,
        VIDEO_DURATION_EXCEEDED, VIDEO_PROBE_BUSY, VIDEO_PROBE_TIMEOUT, VIDEO_PROBE_UNAVAILABLE,
        VIDEO_STORAGE_UNAVAILABLE, VIDEO_NOT_FOUND }

    @FunctionalInterface
    interface InspectedFile<T> {
        T apply(Path path, Preview metadata);
    }

    static final class ProbeException extends RuntimeException {
        final Failure failure;

        ProbeException(Failure failure) {
            super(failure.name());
            this.failure = failure;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalVideoProbe.class);
    private static final int MAX_OUTPUT_BYTES = 64 * 1024;
    private static final String MAX_SINGLE_ALLOCATION_BYTES = "33554432";
    private final ObjectMapper json;
    private final String executable;
    private final Duration timeout;
    private final long maxBytes;
    private final long maxDurationMs;
    private final Semaphore slots;

    LocalVideoProbe(ObjectMapper json,
                    @Value("${citeweave.video.probe.executable}") String executable,
                    @Value("${citeweave.video.probe.timeout}") Duration timeout,
                    @Value("${spring.servlet.multipart.max-file-size}") DataSize maxFileSize,
                    @Value("${citeweave.video.probe.max-duration}") Duration maxDuration,
                    @Value("${citeweave.video.probe.concurrency}") int concurrency) {
        if (executable.isBlank() || timeout.toMillis() < 1 || maxDuration.toMillis() < 1 || concurrency < 1) {
            throw new IllegalArgumentException("Invalid local video probe configuration");
        }
        this.json = json;
        this.executable = executable;
        this.timeout = timeout;
        maxBytes = maxFileSize.toBytes();
        maxDurationMs = maxDuration.toMillis();
        slots = new Semaphore(concurrency);
    }

    /** 先校验请求并取得并发名额，再为这次预检创建临时副本。 */
    Preview preview(MultipartFile file) {
        return inspect(file, null, (path, metadata) -> metadata);
    }

    /** 导入与预检共用校验，并将同一份已探测字节交给存储。 */
    <T> T inspect(MultipartFile file, Path staging, InspectedFile<T> action) {
        verifyUpload(file);
        if (!slots.tryAcquire()) throw new ProbeException(Failure.VIDEO_PROBE_BUSY);
        Path staged = null;
        try {
            staged = staging == null ? Files.createTempFile("citeweave-video-preview-", ".media")
                    : Files.createTempFile(staging, "stage-", ".media");
            file.transferTo(staged);
            return action.apply(staged, readMetadata(probe(staged), Files.size(staged)));
        } catch (IOException error) {
            LOGGER.warn("Local video preflight I/O failed ({})", error.getClass().getSimpleName());
            throw new ProbeException(staged == null && staging != null
                    ? Failure.VIDEO_STORAGE_UNAVAILABLE : Failure.VIDEO_PROBE_UNAVAILABLE);
        } finally {
            if (staged != null) {
                try {
                    Files.deleteIfExists(staged);
                } catch (IOException error) {
                    LOGGER.warn("Local video preflight could not remove its temporary file");
                }
            }
            slots.release();
        }
    }

    private void verifyUpload(MultipartFile file) {
        if (file.isEmpty()) throw new ProbeException(Failure.VIDEO_EMPTY);
        if (file.getSize() > maxBytes) throw new ProbeException(Failure.VIDEO_TOO_LARGE);
        String name = file.getOriginalFilename();
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".mp4") && !lower.endsWith(".mov")) {
            throw new ProbeException(Failure.VIDEO_FORMAT_UNSUPPORTED);
        }
    }

    /** 固定本地协议及容器，禁止外部轨道。并行读取有上限的 stdout，避免管道阻塞。 */
    private JsonNode probe(Path staged) throws IOException {
        Process process = new ProcessBuilder(executable, "-v", "error", "-protocol_whitelist", "file",
                "-f", "mov", "-enable_drefs", "0", "-use_absolute_path", "0",
                "-max_alloc", MAX_SINGLE_ALLOCATION_BYTES,
                "-show_entries", "format=duration:stream=codec_type,codec_name,width,height:stream_disposition=attached_pic",
                "-of", "json", "-i", staged.toAbsolutePath().toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        var output = new FutureTask<>(() -> {
            try (var stream = process.getInputStream()) {
                return stream.readNBytes(MAX_OUTPUT_BYTES + 1);
            }
        });
        Thread.ofVirtual().start(output);

        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new ProbeException(Failure.VIDEO_PROBE_TIMEOUT);
            }
            byte[] bytes = output.get();
            if (process.exitValue() != 0 || bytes.length > MAX_OUTPUT_BYTES) {
                throw new ProbeException(Failure.VIDEO_INVALID);
            }
            return json.readTree(bytes);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ProbeException(Failure.VIDEO_PROBE_UNAVAILABLE);
        } catch (ExecutionException error) {
            throw new ProbeException(Failure.VIDEO_PROBE_UNAVAILABLE);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                try {
                    process.waitFor(1, TimeUnit.SECONDS);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
            }
            output.cancel(true);
        }
    }

    private Preview readMetadata(JsonNode payload, long bytes) {
        if (payload == null) throw new ProbeException(Failure.VIDEO_INVALID);
        long durationMs = durationMs(payload.path("format").path("duration").asText());
        String audio = null;
        JsonNode video = null;
        for (JsonNode stream : payload.path("streams")) {
            String type = stream.path("codec_type").asText();
            String codec = stream.path("codec_name").asText();
            if (codec.isBlank()) continue;
            if (audio == null && type.equals("audio")) audio = codec;
            if (video == null && type.equals("video") && stream.path("disposition").path("attached_pic").asInt() == 0
                    && stream.path("width").asInt() > 0 && stream.path("height").asInt() > 0) {
                video = stream;
            }
        }
        if (video == null) throw new ProbeException(Failure.VIDEO_INVALID);
        return new Preview(bytes, durationMs, video.path("width").asInt(), video.path("height").asInt(),
                video.path("codec_name").asText(), audio);
    }

    private long durationMs(String seconds) {
        try {
            var millis = new BigDecimal(seconds).movePointRight(3);
            if (millis.signum() <= 0) throw new ProbeException(Failure.VIDEO_INVALID);
            if (millis.compareTo(BigDecimal.valueOf(maxDurationMs)) > 0) {
                throw new ProbeException(Failure.VIDEO_DURATION_EXCEEDED);
            }
            return millis.setScale(0, RoundingMode.UP).longValueExact();
        } catch (NumberFormatException | ArithmeticException error) {
            throw new ProbeException(Failure.VIDEO_INVALID);
        }
    }
}
