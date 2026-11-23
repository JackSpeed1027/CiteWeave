package dev.citeweave.source;

import dev.citeweave.source.ResearchNoteRepository.Note;
import dev.citeweave.source.ResearchService.ResearchException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 从已验证的研究结果创建笔记，由用户编辑标题和 Markdown。 */
@Service
class ResearchNoteService {
    enum Failure { RESEARCH_NOTE_NOT_FOUND, RESEARCH_NOTE_RESEARCH_UNAVAILABLE, RESEARCH_NOTE_ANSWER_REQUIRED,
        RESEARCH_NOTE_INVALID, RESEARCH_NOTE_REVISION_CONFLICT }
    static final class NoteException extends RuntimeException {
        final Failure failure;

        NoteException(Failure failure) {
            super(failure.name());
            this.failure = failure;
        }
    }
    record Saved(Note note, boolean created) {}
    record Edit(String title, String markdown, Long revision) {}

    private static final String WORKSPACE = "local-personal";
    private final ResearchNoteRepository notes;
    private final ResearchHistoryService research;

    ResearchNoteService(ResearchNoteRepository notes, ResearchHistoryService research) {
        this.notes = notes;
        this.research = research;
    }

    List<ResearchNoteRepository.Header> list() {
        return notes.list(WORKSPACE);
    }

    Note get(UUID id) {
        return notes.find(WORKSPACE, id).orElseThrow(() -> new NoteException(Failure.RESEARCH_NOTE_NOT_FOUND));
    }

    /** 先找已编辑笔记；首次才从服务端核验过的回答与引用生成初稿。 */
    Saved create(UUID researchId) {
        var existing = notes.findByResearch(WORKSPACE, researchId);
        if (existing.isPresent()) return new Saved(existing.get(), false);
        ResearchHistoryService.Saved source;
        try {
            source = research.get(researchId);
        } catch (ResearchException error) {
            throw new NoteException(Failure.RESEARCH_NOTE_RESEARCH_UNAVAILABLE);
        }
        if (source.answer() == null) throw new NoteException(Failure.RESEARCH_NOTE_ANSWER_REQUIRED);
        String title = source.record().question();
        int end = title.offsetByCodePoints(0, Math.min(160, title.codePointCount(0, title.length())));
        // 初稿中的模型输出按字面文本保存；之后的 Markdown 由用户编辑。
        StringBuilder draft = new StringBuilder();
        TranscriptNoteMarkdown.appendLiteral(draft, source.answer());
        return notes.insert(WORKSPACE, researchId, title.substring(0, end), draft.toString(), source.citations())
                .map(note -> new Saved(note, true))
                .orElseGet(() -> new Saved(notes.findByResearch(WORKSPACE, researchId).orElseThrow(), false));
    }

    /** 校验正文上限并使用预期修订号保存。 */
    Note update(UUID id, Edit edit) {
        String title = edit.title() == null ? "" : edit.title().strip();
        if (title.isEmpty() || title.codePointCount(0, title.length()) > 160 || title.indexOf(0) >= 0
                || edit.markdown() == null || edit.markdown().length() > 50_000 || edit.markdown().indexOf(0) >= 0
                || edit.revision() == null || edit.revision() < 1) {
            throw new NoteException(Failure.RESEARCH_NOTE_INVALID);
        }
        return notes.update(WORKSPACE, id, edit.revision(), title, edit.markdown()).orElseGet(() -> {
            get(id);
            throw new NoteException(Failure.RESEARCH_NOTE_REVISION_CONFLICT);
        });
    }
}
