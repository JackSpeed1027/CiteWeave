package dev.citeweave.video.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 在有时限的本机进程中调用已经验证的 CPU 转写命令。 */
@Component
class LocalVideoAsrRunner {
    static final class Failure extends RuntimeException {
        Failure(String code) { super(code); }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalVideoAsrRunner.class);
    private static final long MAX_VTT_BYTES = 2_000_000;
    private final boolean enabled;
    private final String python;
    private final String modulePath;
    private final String modelDir;
    private final String ffmpeg;
    private final String ffprobe;
    private final int maxSeconds;
    private final int timeoutSeconds;
    private final int workerThreads;

    LocalVideoAsrRunner(@Value("${citeweave.video.asr.enabled}") boolean enabled,
                        @Value("${citeweave.video.asr.python}") String python,
                        @Value("${citeweave.video.asr.python-path}") String modulePath,
                        @Value("${citeweave.video.asr.model-dir}") String modelDir,
                        @Value("${citeweave.video.asr.ffmpeg}") String ffmpeg,
                        @Value("${citeweave.video.probe.executable}") String ffprobe,
                        @Value("${citeweave.video.asr.max-seconds}") int maxSeconds,
                        @Value("${citeweave.video.asr.timeout-seconds}") int timeoutSeconds,
                        @Value("${citeweave.video.asr.threads}") int workerThreads) {
        if (maxSeconds < 1 || timeoutSeconds < 1 || workerThreads < 1) {
            throw new IllegalArgumentException("Invalid ASR process limits");
        }
        this.enabled = enabled;
        this.python = python;
        this.modulePath = modulePath;
        this.modelDir = modelDir;
        this.ffmpeg = ffmpeg;
        this.ffprobe = ffprobe;
        this.maxSeconds = maxSeconds;
        this.timeoutSeconds = timeoutSeconds;
        this.workerThreads = workerThreads;
    }

    boolean available() {
        return enabled && !modulePath.isBlank() && !modelDir.isBlank()
                && Files.isRegularFile(Path.of(modulePath, "citeweave_ai", "local_asr.py"))
                && List.of("model.bin", "config.json", "tokenizer.json").stream()
                    .allMatch(name -> Files.isRegularFile(Path.of(modelDir, name)));
    }

    int timeoutSeconds() { return timeoutSeconds; }

    void checkDuration(long durationMs) {
        if (durationMs > maxSeconds * 1000L) throw new Failure("ASR_VIDEO_TOO_LONG");
    }

    /** 输入只能是受控存储的对象；所有子进程产物只写入本次尝试的临时目录。 */
    String transcribe(Path media) {
        Path temporary = null;
        Process process = null;
        try {
            temporary = Files.createTempDirectory("citeweave-asr-job-");
            Path output = temporary.resolve("output");
            var builder = new ProcessBuilder(python, "-m", "citeweave_ai.local_asr", media.toString(),
                    "--managed-input", "--model-dir", modelDir, "--output", output.toString(),
                    "--runs", "1", "--max-seconds", Integer.toString(maxSeconds),
                    "--threads", Integer.toString(workerThreads), "--ffmpeg", ffmpeg, "--ffprobe", ffprobe)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
            // 不向子进程传递数据库和模型供应商凭据，只保留运行所需的系统环境。
            var environment = builder.environment();
            environment.keySet().removeIf(key -> !List.of("PATH", "SYSTEMROOT", "WINDIR", "TEMP", "TMP",
                    "HOME", "USERPROFILE").contains(key.toUpperCase(java.util.Locale.ROOT)));
            environment.put("PYTHONPATH", modulePath);
            environment.put("PYTHONUTF8", "1");
            environment.put("HF_HUB_OFFLINE", "1");

            process = builder.start();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) throw new Failure("ASR_TIMEOUT");
            Path vtt = output.resolve("machine-captions.vtt");
            if (process.exitValue() != 0 || !Files.isRegularFile(vtt, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(vtt) > MAX_VTT_BYTES) {
                throw new Failure("ASR_PROCESS_FAILED");
            }
            return Files.readString(vtt);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new Failure("ASR_INTERRUPTED");
        } catch (IOException error) {
            LOGGER.warn("ASR process I/O failed ({})", error.getClass().getSimpleName());
            throw new Failure("ASR_PROCESS_FAILED");
        } finally {
            if (process != null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                try {
                    process.waitFor(1, TimeUnit.SECONDS);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
            }
            clean(temporary);
        }
    }

    private static void clean(Path temporary) {
        if (temporary == null) return;
        Path output = temporary.resolve("output");
        try {
            Files.deleteIfExists(output.resolve("machine-captions.vtt"));
            Files.deleteIfExists(output.resolve("report.json"));
            Files.deleteIfExists(output);
            Files.deleteIfExists(temporary);
        } catch (IOException error) {
            LOGGER.warn("ASR temporary output could not be removed");
        }
    }
}
