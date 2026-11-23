package dev.citeweave.source;

import dev.citeweave.source.SummaryTaskRepository.Task;
import dev.citeweave.source.TranscriptSummaryEngine.SummaryException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 编排字幕任务的提交、重试与一次后台执行。 */
@Service
class SummaryTaskService {
    enum Failure { TASK_NOT_FOUND, TASK_NOT_RETRYABLE }

    static final class TaskException extends RuntimeException {
        final Failure failure;

        TaskException(Failure failure) {
            super(failure.name());
            this.failure = failure;
        }
    }

    private final SummaryTaskRepository tasks;
    private final TranscriptEvidenceReader reader;
    private final TranscriptSummaryService summaries;

    SummaryTaskService(SummaryTaskRepository tasks, TranscriptEvidenceReader reader, TranscriptSummaryService summaries) {
        this.tasks = tasks;
        this.reader = reader;
        this.summaries = summaries;
    }

    Task enqueue(long sourceId) {
        reader.load(sourceId);
        return tasks.enqueue(sourceId);
    }

    List<Task> list() {
        return tasks.list();
    }

    Task get(UUID id) {
        return tasks.find(id).orElseThrow(() -> new TaskException(Failure.TASK_NOT_FOUND));
    }

    /** 数据库只允许失败态返回队列，服务区分不存在与状态冲突。 */
    Task retry(UUID id) {
        return tasks.retry(id).orElseGet(() -> {
            get(id);
            throw new TaskException(Failure.TASK_NOT_RETRYABLE);
        });
    }

    /** 领取后在事务之外调用模型，再提交与当前尝试匹配的结果。 */
    void processNext() {
        tasks.expire();
        tasks.claim().ifPresent(task -> {
            try {
                summaries.generateForTask(task);
            } catch (SummaryException error) {
                tasks.fail(task, "SUMMARY_" + error.failure.name());
            } catch (SourceException error) {
                tasks.fail(task, error.code().name());
            } catch (RuntimeException error) {
                tasks.fail(task, "TASK_INTERNAL_ERROR");
            }
        });
    }
}
