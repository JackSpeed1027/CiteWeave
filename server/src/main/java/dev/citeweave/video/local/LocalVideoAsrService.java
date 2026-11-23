package dev.citeweave.video.local;

import dev.citeweave.source.LocalVideoSubtitleService;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 转写入队、领取与发布。Python 模型执行在数据库事务之外。 */
@Service
class LocalVideoAsrService {
    record Task(UUID videoId, String status, int attempt, String errorCode, Long sourceId, OffsetDateTime updatedAt) {}
    record Status(boolean available, Task task) {}

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalVideoAsrService.class);
    private final LocalVideoService videos;
    private final LocalVideoStorage storage;
    private final LocalVideoAsrMapper queue;
    private final LocalVideoAsrRunner runner;
    private final LocalVideoSubtitleService subtitles;
    private final TransactionTemplate transaction;
    private final int leaseSeconds;

    LocalVideoAsrService(LocalVideoService videos, LocalVideoStorage storage, LocalVideoAsrMapper queue,
                         LocalVideoAsrRunner runner, LocalVideoSubtitleService subtitles,
                         PlatformTransactionManager manager,
                         @Value("${citeweave.video.asr.lease-seconds}") int leaseSeconds) {
        if (leaseSeconds <= runner.timeoutSeconds()) {
            throw new IllegalArgumentException("ASR lease must exceed process timeout");
        }
        this.videos = videos;
        this.storage = storage;
        this.queue = queue;
        this.runner = runner;
        this.subtitles = subtitles;
        this.leaseSeconds = leaseSeconds;
        transaction = new TransactionTemplate(manager);
    }

    Status status(UUID id) {
        videos.get(id);
        return new Status(runner.available(), find(id));
    }

    Task enqueue(UUID id) {
        requireEligible(videos.get(id));
        queue.enqueue(id);
        return find(id);
    }

    Task retry(UUID id) {
        requireEligible(videos.get(id));
        if (!queue.retry(id)) throw new LocalVideoAsrRunner.Failure("ASR_TASK_NOT_RETRYABLE");
        return find(id);
    }

    void runNext() {
        queue.expire();
        var job = queue.claim(leaseSeconds);
        if (job == null) return;

        try {
            var video = videos.get(job.videoId);
            requireEligible(video);
            var file = storage.verifiedObject(video.sha256(), video.metadata().sizeBytes());
            String vtt = runner.transcribe(file);
            transaction.executeWithoutResult(status -> {
                // 任务成功和生成的字幕在同一事务提交。超期或旧尝试会撤销本次绑定。
                long sourceId = subtitles.publishMachine(job.videoId, vtt).sourceId();
                if (!queue.complete(job.videoId, job.attempt, sourceId)) {
                    throw new LocalVideoAsrRunner.Failure("ASR_LEASE_EXPIRED");
                }
            });
        } catch (LocalVideoAsrRunner.Failure failure) {
            queue.fail(job.videoId, job.attempt, failure.getMessage());
        } catch (RuntimeException failure) {
            LOGGER.warn("ASR publication failed for task {} ({})", job.videoId, failure.getClass().getSimpleName());
            queue.fail(job.videoId, job.attempt, "ASR_PUBLICATION_FAILED");
        }
    }

    private Task find(UUID id) {
        return Optional.ofNullable(queue.selectById(id)).map(LocalVideoAsrMapper.Job::view).orElse(null);
    }

    private void requireEligible(LocalVideoService.Video video) {
        if (!runner.available()) throw new LocalVideoAsrRunner.Failure("ASR_UNAVAILABLE");
        if (video.metadata().audioCodec() == null) throw new LocalVideoAsrRunner.Failure("ASR_NO_AUDIO");
        runner.checkDuration(video.metadata().durationMs());
    }
}
