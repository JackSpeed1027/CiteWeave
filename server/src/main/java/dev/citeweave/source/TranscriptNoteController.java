package dev.citeweave.source;

import dev.citeweave.source.TranscriptNoteService.NoteException;
import dev.citeweave.web.ApiProblems;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 保存摘要并读取个人空间内的笔记。 */
@RestController
class TranscriptNoteController {
    private final TranscriptNoteService notes;

    TranscriptNoteController(TranscriptNoteService notes) {
        this.notes = notes;
    }

    /** 返回笔记标题和来源，正文由详情端点读取。 */
    @GetMapping("/api/notes")
    List<TranscriptNoteRepository.Header> list() {
        return notes.list();
    }

    @GetMapping("/api/notes/{id}")
    TranscriptNoteRepository.Note get(@PathVariable UUID id) {
        return notes.get(id);
    }

    /** 使用与笔记详情相同的工作区读取边界，输出 UTF-8 附件。 */
    @GetMapping("/api/notes/{id}/export/markdown")
    ResponseEntity<String> markdown(@PathVariable UUID id) {
        var note = notes.get(id);
        String filename = "citeweave-note-" + note.note().id() + ".md";
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(new MediaType("text", "markdown", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(TranscriptNoteMarkdown.render(note));
    }

    /** 首次保存返回 201，重复保存同一资料版本返回 200 和既有笔记。 */
    @PostMapping("/api/sources/{id}/summary/note")
    ResponseEntity<TranscriptNoteRepository.Note> save(@PathVariable long id) {
        var saved = notes.save(id);
        var note = saved.note();
        return saved.created()
                ? ResponseEntity.created(URI.create("/api/notes/" + note.note().id())).body(note)
                : ResponseEntity.ok(note);
    }

    @ExceptionHandler(NoteException.class)
    ProblemDetail failure(NoteException error) {
        return switch (error.code) {
            case NOTE_NOT_FOUND -> ApiProblems.create(HttpStatus.NOT_FOUND, error.code.name(), "找不到这条笔记。");
            case NOTE_SUMMARY_REQUIRED -> ApiProblems.create(HttpStatus.CONFLICT, error.code.name(), "请先生成并保存字幕摘要。");
        };
    }
}
