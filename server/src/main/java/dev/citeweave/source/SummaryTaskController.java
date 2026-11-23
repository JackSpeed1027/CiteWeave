package dev.citeweave.source;

import dev.citeweave.source.SummaryTaskRepository.Task;
import dev.citeweave.source.SummaryTaskService.TaskException;
import dev.citeweave.web.ApiProblems;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 个人空间任务入口。当前只接已入库字幕的后台摘要。 */
@RestController
class SummaryTaskController {
    private final SummaryTaskService tasks;

    SummaryTaskController(SummaryTaskService tasks) {
        this.tasks = tasks;
    }

    /** 先验证源资料，返回可轮询的任务链接。 */
    @PostMapping("/api/sources/{id}/summary-task")
    ResponseEntity<Task> enqueue(@PathVariable long id) {
        return accepted(tasks.enqueue(id));
    }

    @GetMapping("/api/summary-tasks")
    ResponseEntity<List<Task>> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tasks.list());
    }

    @GetMapping("/api/summary-tasks/{id}")
    ResponseEntity<Task> get(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tasks.get(id));
    }

    /** 重试沿用任务 ID，只有失败态可以重新入队。 */
    @PostMapping("/api/summary-tasks/{id}/retry")
    ResponseEntity<Task> retry(@PathVariable UUID id) {
        return accepted(tasks.retry(id));
    }

    private static ResponseEntity<Task> accepted(Task task) {
        return ResponseEntity.accepted().location(URI.create("/api/summary-tasks/" + task.id()))
                .cacheControl(CacheControl.noStore()).body(task);
    }

    @ExceptionHandler(TaskException.class)
    ProblemDetail failure(TaskException error) {
        return switch (error.failure) {
            case TASK_NOT_FOUND -> ApiProblems.create(HttpStatus.NOT_FOUND, error.failure.name(), "找不到这条摘要任务。");
            case TASK_NOT_RETRYABLE -> ApiProblems.create(HttpStatus.CONFLICT, error.failure.name(), "只有失败的任务可以重试。");
        };
    }
}
