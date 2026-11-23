package dev.citeweave.source;

import com.fasterxml.jackson.databind.JsonNode;
import dev.citeweave.source.ResearchNoteRepository.Note;
import dev.citeweave.source.ResearchNoteService.Failure;
import dev.citeweave.source.ResearchNoteService.NoteException;
import dev.citeweave.web.ApiProblems;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 按现有个人空间边界读写及导出研究笔记。 */
@RestController
class ResearchNoteController {
    private final ResearchNoteService notes;

    ResearchNoteController(ResearchNoteService notes) {
        this.notes = notes;
    }

    /** 同一研究重复创建返回原笔记。 */
    @PostMapping("/api/research/{id}/note")
    ResponseEntity<Note> create(@PathVariable UUID id) {
        var saved = notes.create(id);
        var body = saved.note();
        var response = saved.created() ? ResponseEntity.created(URI.create("/api/research-notes/" + body.note().id()))
                : ResponseEntity.ok();
        return response.cacheControl(CacheControl.noStore()).body(body);
    }

    @GetMapping("/api/research-notes")
    ResponseEntity<List<ResearchNoteRepository.Header>> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(notes.list());
    }

    @GetMapping("/api/research-notes/{id}")
    ResponseEntity<Note> get(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(notes.get(id));
    }

    /** 浏览器只提交标题、正文和版本。原始引用由服务器保留。 */
    @PutMapping(path = "/api/research-notes/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Note> update(@PathVariable UUID id, @RequestBody JsonNode input) {
        var revision = input.path("revision");
        if (input.size() != 3 || !revision.isIntegralNumber() || !revision.canConvertToLong()) {
            throw new NoteException(Failure.RESEARCH_NOTE_INVALID);
        }
        var edit = new ResearchNoteService.Edit(input.path("title").textValue(), input.path("markdown").textValue(),
                revision.longValue());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(notes.update(id, edit));
    }

    /** 导出已保存的编辑稿和版本化原文引用。 */
    @GetMapping("/api/research-notes/{id}/export/markdown")
    ResponseEntity<String> markdown(@PathVariable UUID id) {
        Note note = notes.get(id);
        var output = new StringBuilder("# 研究笔记\n\n## 标题\n\n");
        TranscriptNoteMarkdown.appendLiteral(output, note.note().title());
        output.append("研究 ID：").append(note.note().researchId()).append("\n\n## 笔记正文\n\n")
                .append(note.markdown()).append("\n\n## 原文引用\n\n");
        int index = 1;
        for (var citation : note.citations()) {
            String location = switch (citation.locationType()) {
                case "transcript" -> citation.startMs() + "–" + citation.endMs() + " ms";
                case "paragraph" -> "第 " + citation.paragraph() + " 段";
                case "page" -> "第 " + citation.page() + " 页";
                default -> throw new IllegalStateException("Unknown citation location");
            };
            output.append("### 引用 ").append(index++).append(" · ").append(location).append("\n\n")
                    .append("资料 ID：").append(citation.sourceId()).append("\n\n资料版本：")
                    .append(citation.sourceVersionId()).append("\n\n证据 ID：").append(citation.evidenceId()).append("\n\n");
            TranscriptNoteMarkdown.appendLiteral(output, citation.text());
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(new MediaType("text", "markdown", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("citeweave-research-note-" + id + ".md").build().toString())
                .body(output.toString());
    }

    @ExceptionHandler(NoteException.class)
    ProblemDetail failure(NoteException error) {
        var status = switch (error.failure) {
            case RESEARCH_NOTE_NOT_FOUND, RESEARCH_NOTE_RESEARCH_UNAVAILABLE -> HttpStatus.NOT_FOUND;
            case RESEARCH_NOTE_ANSWER_REQUIRED, RESEARCH_NOTE_REVISION_CONFLICT -> HttpStatus.CONFLICT;
            case RESEARCH_NOTE_INVALID -> HttpStatus.BAD_REQUEST;
        };
        var detail = switch (error.failure) {
            case RESEARCH_NOTE_NOT_FOUND -> "找不到这条研究笔记。";
            case RESEARCH_NOTE_RESEARCH_UNAVAILABLE -> "找不到原研究或其证据已不可用。";
            case RESEARCH_NOTE_ANSWER_REQUIRED -> "有据的研究回答才可以保存为笔记。";
            case RESEARCH_NOTE_REVISION_CONFLICT -> "此笔记已在别处保存。请复制你的改动，再重新加载已保存版本。";
            case RESEARCH_NOTE_INVALID -> "标题须为 1–160 字符，正文最多 50,000 字符，并提交有效修订号。";
        };
        return ApiProblems.create(status, error.failure.name(), detail);
    }
}
