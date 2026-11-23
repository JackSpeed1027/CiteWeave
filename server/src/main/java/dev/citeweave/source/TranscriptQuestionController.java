package dev.citeweave.source;

import com.fasterxml.jackson.databind.JsonNode;
import dev.citeweave.source.TranscriptSummaryEngine.Evidence;
import dev.citeweave.source.TranscriptSummaryEngine.SummaryException;
import dev.citeweave.web.ApiProblems;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 在指定资料内检索和回答，结果随请求返回。 */
@RestController
@RequestMapping("/api")
class TranscriptQuestionController {
    record Question(String question) {}

    private final TranscriptQuestionService questions;
    private final DocumentEvidenceReader documents;
    private final EvidenceLexicalIndex lexical;

    TranscriptQuestionController(TranscriptQuestionService questions, DocumentEvidenceReader documents,
                                 EvidenceLexicalIndex lexical) {
        this.questions = questions;
        this.documents = documents;
        this.lexical = lexical;
    }

    /** 只接受非空的问题，拒绝超长或包含 NUL 的输入。 */
    @PostMapping(path = "/sources/{id}/questions", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> ask(@PathVariable long id, @RequestBody Question input) {
        String text = questionText(input.question());
        if (text == null) return invalid("QUESTION_INVALID", "问题长度须为 1–500 字符。");
        return ResponseEntity.ok(questions.ask(id, text));
    }

    /** 返回可从文档问答定位的段落。 */
    @GetMapping("/sources/{id}/paragraphs")
    List<Evidence> paragraphs(@PathVariable long id) {
        return documents.read(id);
    }

    /** 新导入 PDF 证据保留原始页码，历史正文仍可通过资料详情读取。 */
    @GetMapping("/sources/{id}/pdf-pages")
    List<Evidence> pdfPages(@PathVariable long id) {
        return documents.pdfPages(id);
    }

    /** 文本问答复用词面检索和受控生成，不保存回答。 */
    @PostMapping(path = "/sources/{id}/document-questions", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> askDocument(@PathVariable long id, @RequestBody Question input) {
        String text = questionText(input.question());
        if (text == null) return invalid("QUESTION_INVALID", "问题长度须为 1–500 字符。");
        var candidates = TranscriptQuestionService.retrieve(text, documents.readForQuestion(id));
        return ResponseEntity.ok(questions.answer(text, candidates));
    }

    /** 批量问答只接受明确选中的 2–3 个不同字幕资料 ID。 */
    @PostMapping(path = "/transcript-questions", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> askMultiple(@RequestBody JsonNode body) {
        return askSelected(body, false);
    }

    /** 返回具备原文引用坐标的资料。 */
    @GetMapping("/source-questions/sources")
    List<SourceRepository.Summary> questionSources() {
        return documents.questionSources();
    }

    /** 支持字幕、段落和 PDF 页码证据混选，先校验全部资料再调用模型。 */
    @PostMapping(path = "/source-questions", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> askMixed(@RequestBody JsonNode body) {
        return askSelected(body, true);
    }

    /** 返回问答可用的首批候选。启用索引时按版本保存派生证据。 */
    @PostMapping(path = "/source-evidence/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> search(@RequestBody JsonNode body) {
        String query = questionText(body.path("query").textValue());
        if (query == null) return invalid("EVIDENCE_QUERY_INVALID", "检索词长度须为 1–500 字符。");
        var ids = selectedIds(body.path("sourceIds"), 1);
        if (ids.isEmpty()) return invalid("EVIDENCE_SOURCE_IDS_INVALID", "请选择 1–3 份不同的资料。");
        return ResponseEntity.ok(lexical.search(ids, query));
    }

    /** 通过资料及完整证据 ID 回读原句和位置。 */
    @GetMapping("/sources/{id}/evidence")
    Evidence sourceEvidence(@PathVariable long id, @RequestParam String evidenceId) {
        return documents.readEvidence(id, evidenceId);
    }

    private ResponseEntity<?> askSelected(JsonNode body, boolean mixed) {
        String text = questionText(body.path("question").textValue());
        if (text == null) return invalid("QUESTION_INVALID", "问题长度须为 1–500 字符。");
        var ids = selectedIds(body.path("sourceIds"), 2);
        if (ids.isEmpty()) return invalidSources(mixed);
        return ResponseEntity.ok(mixed ? questions.answer(text, lexical.search(ids, text))
                : questions.askMultiple(ids, text));
    }

    static List<Long> selectedIds(JsonNode sources, int minimum) {
        if (!sources.isArray() || sources.size() < minimum || sources.size() > 3) return List.of();
        var ids = new LinkedHashSet<Long>();
        for (JsonNode source : sources) {
            if (!source.isIntegralNumber() || !source.canConvertToLong()
                    || source.longValue() < 1 || !ids.add(source.longValue())) return List.of();
        }
        return List.copyOf(ids);
    }

    /** 按资料与当前完整版本核对证据，用于已保存笔记和问答的跳转。 */
    @GetMapping("/sources/{id}/transcript/evidence")
    Evidence evidence(@PathVariable long id, @RequestParam String evidenceId) {
        return questions.evidence(id, evidenceId);
    }

    static String questionText(String value) {
        if (value == null) return null;
        String text = value.strip();
        return text.isEmpty() || text.length() > 500 || text.indexOf(0) >= 0 ? null : text;
    }

    private static ResponseEntity<ProblemDetail> invalidSources(boolean mixed) {
        return invalid("QUESTION_SOURCE_IDS_INVALID", mixed ? "请选择 2–3 份不同的资料。" : "请选择 2–3 份不同的字幕资料。");
    }

    private static ResponseEntity<ProblemDetail> invalid(String code, String detail) {
        return ResponseEntity.badRequest().body(ApiProblems.create(HttpStatus.BAD_REQUEST, code, detail));
    }

    @ExceptionHandler(SummaryException.class)
    ProblemDetail engineFailed(SummaryException error) {
        String code = "QUESTION_" + error.failure.name();
        return switch (error.failure) {
            case MODEL_NOT_CONFIGURED -> ApiProblems.create(HttpStatus.SERVICE_UNAVAILABLE, code, "问答模型尚未配置。");
            case MODEL_RATE_LIMITED -> ApiProblems.create(HttpStatus.SERVICE_UNAVAILABLE, code, "模型限流，请稍后重试。");
            case MODEL_TIMEOUT -> ApiProblems.create(HttpStatus.GATEWAY_TIMEOUT, code, "问答模型响应超时。");
            case INVALID_CITATION -> ApiProblems.create(HttpStatus.BAD_GATEWAY, code, "回答引用了本次输入之外的证据。");
            case INVALID_OUTPUT -> ApiProblems.create(HttpStatus.BAD_GATEWAY, code, "模型返回的回答格式无效。");
            case EVIDENCE_INSUFFICIENT -> ApiProblems.create(HttpStatus.UNPROCESSABLE_ENTITY, code, "预算内没有完整的证据片段。");
            case AI_UNAVAILABLE, SOURCE_CHANGED -> ApiProblems.create(HttpStatus.BAD_GATEWAY, code, "问答服务暂时不可用。");
        };
    }
}
