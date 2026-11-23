package dev.citeweave.source;

import dev.citeweave.source.TranscriptSummaryEngine.SummaryException;
import dev.citeweave.web.ApiProblems;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 字幕摘要的生成与读回入口。 */
@RestController
@RequestMapping("/api/sources/{id}/summary")
class TranscriptSummaryController {
    private final TranscriptSummaryService service;

    TranscriptSummaryController(TranscriptSummaryService service) {
        this.service = service;
    }

    /** 无已发布摘要时返回 204。 */
    @GetMapping
    ResponseEntity<TranscriptSummaryService.Result> get(@PathVariable long id) {
        return service.find(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** 生成、保存或复用当前资料版本的摘要。 */
    @PostMapping
    TranscriptSummaryService.Result generate(@PathVariable long id) {
        return service.generate(id);
    }

    @ExceptionHandler(SummaryException.class)
    ProblemDetail failed(SummaryException exception) {
        String code = "SUMMARY_" + exception.failure.name();
        return switch (exception.failure) {
            case MODEL_NOT_CONFIGURED -> ApiProblems.create(HttpStatus.SERVICE_UNAVAILABLE, code, "摘要模型尚未配置。");
            case MODEL_TIMEOUT -> ApiProblems.create(HttpStatus.GATEWAY_TIMEOUT, code, "摘要模型响应超时，请稍后重试。");
            case MODEL_RATE_LIMITED -> ApiProblems.create(HttpStatus.SERVICE_UNAVAILABLE, code, "摘要模型限流，请稍后重试。");
            case EVIDENCE_INSUFFICIENT -> ApiProblems.create(HttpStatus.UNPROCESSABLE_ENTITY, code, "可用于摘要的字幕证据不足。");
            case INVALID_CITATION -> ApiProblems.create(HttpStatus.BAD_GATEWAY, code, "模型引用了本次输入之外的证据。");
            case INVALID_OUTPUT -> ApiProblems.create(HttpStatus.BAD_GATEWAY, code, "模型返回的摘要格式无效。");
            case AI_UNAVAILABLE -> ApiProblems.create(HttpStatus.BAD_GATEWAY, code, "摘要服务暂时不可用，请检查 AI Engine。");
            case SOURCE_CHANGED -> ApiProblems.create(HttpStatus.CONFLICT, code, "字幕版本已变化，请刷新后重试。");
        };
    }
}
