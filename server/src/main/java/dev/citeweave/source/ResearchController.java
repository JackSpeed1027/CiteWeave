package dev.citeweave.source;

import com.fasterxml.jackson.databind.JsonNode;
import dev.citeweave.source.ResearchService.ResearchException;
import dev.citeweave.web.ApiProblems;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 正式 Web 的单次研究入口，沿用本机资料访问边界。 */
@RestController
class ResearchController {
    private final ResearchHistoryService research;

    ResearchController(ResearchHistoryService research) {
        this.research = research;
    }

    /** 接收显式资料选择，成功记录可在响应后重开。 */
    @PostMapping(path = "/api/research", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> run(@RequestBody JsonNode body,
                          @RequestHeader(name = "Idempotency-Key", required = false) String header,
                          @RequestHeader(name = "Research-Resume-From", required = false) String resumeHeader) {
        var ids = TranscriptQuestionController.selectedIds(body.path("sourceIds"), 1);
        String question = TranscriptQuestionController.questionText(body.path("question").textValue());
        if (ids.isEmpty() || question == null) {
            return ResponseEntity.badRequest().body(ApiProblems.create(HttpStatus.BAD_REQUEST,
                    "RESEARCH_REQUEST_INVALID", "请选择 1–3 份不同的资料，并输入 1–500 字符的问题。"));
        }
        UUID key = null;
        UUID parent = null;
        try {
            if (header != null) key = requestId(header);
            if (resumeHeader != null) parent = requestId(resumeHeader);
            if (parent != null && (key == null || key.equals(parent))) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(ApiProblems.create(HttpStatus.BAD_REQUEST,
                    "RESEARCH_REQUEST_KEY_INVALID", "请求键须为标准 UUID，续跑需要与原请求不同的新键。"));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(parent == null ? research.run(ids, question, key) : research.resume(ids, question, key, parent));
    }

    private static UUID requestId(String value) {
        UUID id = UUID.fromString(value);
        if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
        return id;
    }

    /** 返回该工作区最近的已完成研究。 */
    @GetMapping("/api/research")
    ResponseEntity<List<ResearchHistoryRepository.Header>> recent() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(research.recent());
    }

    /** 读回工作区内的一条终态快照及其原文。 */
    @GetMapping("/api/research/{id}")
    ResponseEntity<ResearchHistoryService.Saved> get(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(research.get(id));
    }

    /** 读回有键请求的恢复页面，无模型调用。 */
    @GetMapping("/api/research/requests/{key}")
    ResponseEntity<ResearchHistoryService.RequestStatus> status(@PathVariable UUID key) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(research.status(key));
    }

    record Checkpoint(List<TranscriptSummaryEngine.ResearchStep> trace, ResearchHistoryRepository.Context context) {}

    /** Python 回报已完成工具。单次运行的授权仅用于内部回调。 */
    @PostMapping(path = "/internal/v1/research/{id}/checkpoints", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> checkpoint(@PathVariable UUID id,
                                    @RequestHeader(name = "X-CiteWeave-Checkpoint-Token", required = false) String token,
                                    @RequestBody Checkpoint update) {
        return switch (research.checkpoint(id, token, update.trace(), update.context())) {
            case SAVED -> ResponseEntity.noContent().build();
            case NOT_FOUND -> ResponseEntity.notFound().build();
            case CONFLICT -> ResponseEntity.status(HttpStatus.CONFLICT).build();
            case INVALID -> ResponseEntity.badRequest().build();
        };
    }

    @ExceptionHandler(ResearchException.class)
    ProblemDetail failed(ResearchException error) {
        HttpStatus status = switch (error.failure) {
            case SOURCE_UNAVAILABLE, RECORD_NOT_FOUND, EVIDENCE_UNAVAILABLE -> HttpStatus.NOT_FOUND;
            case DISABLED, NOT_CONFIGURED, RATE_LIMITED -> HttpStatus.SERVICE_UNAVAILABLE;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case REQUEST_IN_PROGRESS, REQUEST_KEY_CONFLICT, REQUEST_PREVIOUS_FAILED, REQUEST_OUTCOME_UNKNOWN,
                 RESUME_UNAVAILABLE -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_GATEWAY;
        };
        String detail = switch (error.failure) {
            case SOURCE_UNAVAILABLE -> "所选资料不存在或缺少可引用的正文。";
            case RECORD_NOT_FOUND -> "找不到这条研究记录。";
            case DISABLED -> "本机研究功能尚未启用。";
            case NOT_CONFIGURED -> "研究模型尚未配置。";
            case RATE_LIMITED -> "模型限流，请稍后重试。";
            case TIMEOUT -> "研究超时，可缩小问题范围后重试。";
            case REQUEST_IN_PROGRESS -> "原研究请求仍在处理中。稍后用同一请求重试或查看最近研究。";
            case REQUEST_KEY_CONFLICT -> "该请求键已经用于另一组问题或资料。";
            case REQUEST_PREVIOUS_FAILED -> "该请求已失败。可重新开始一轮研究。";
            case REQUEST_OUTCOME_UNKNOWN -> "原研究请求结果未知。可以先查看最近研究，再决定是否重新运行。";
            case RESUME_UNAVAILABLE -> "该研究无法从此断点续跑。请检查资料范围，或查看最近研究。";
            case EVIDENCE_UNAVAILABLE -> "研究所需的原文证据已不可用。";
            case EVIDENCE_OUT_OF_SCOPE -> "研究因尝试读取所选范围之外的证据而停止。";
            case STEP_LIMIT -> "研究超出本轮预算，可缩小问题范围后重试。";
            case TOOL_REJECTED -> "模型提出的工具调用无效，研究已停止。";
            case CITATION_INVALID -> "研究回答引用了未成功读回的证据。";
            case INVALID_RESPONSE -> "研究返回的结果格式无效。";
            case ENGINE_UNAVAILABLE -> "研究服务暂时不可用，请稍后重试。";
        };
        var problem = ApiProblems.create(status, "RESEARCH_" + error.failure.name(), detail);
        problem.setProperty("trace", error.trace);
        return problem;
    }
}
