package dev.citeweave.source;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 管理本机个人空间中从字幕摘要保存的笔记。 */
@Service
class TranscriptNoteService {
    enum ErrorCode { NOTE_NOT_FOUND, NOTE_SUMMARY_REQUIRED }
    static final class NoteException extends RuntimeException {
        final ErrorCode code;

        NoteException(ErrorCode code) {
            super(code.name());
            this.code = code;
        }
    }

    record Saved(TranscriptNoteRepository.Note note, boolean created) {}

    // 当前只开放既有本机个人空间。租户权限接入后从可信身份解析工作区。
    private static final String WORKSPACE = "local-personal";
    private final TranscriptNoteRepository notes;
    private final TranscriptSummaryService summaries;

    TranscriptNoteService(TranscriptNoteRepository notes, TranscriptSummaryService summaries) {
        this.notes = notes;
        this.summaries = summaries;
    }

    List<TranscriptNoteRepository.Header> list() {
        return notes.list(WORKSPACE);
    }

    TranscriptNoteRepository.Note get(UUID id) {
        return notes.find(WORKSPACE, id).orElseThrow(() -> new NoteException(ErrorCode.NOTE_NOT_FOUND));
    }

    /** 已发布摘要决定笔记的版本和引用，浏览器只提交资料 ID。 */
    Saved save(long sourceId) {
        var summary = summaries.find(sourceId)
                .orElseThrow(() -> new NoteException(ErrorCode.NOTE_SUMMARY_REQUIRED));
        String version = summary.citations().getFirst().sourceVersionId();
        Optional<TranscriptNoteRepository.Note> inserted = notes.insert(WORKSPACE, sourceId, version);
        // 写入和回读使用不同 SQL 语句，让败于唯一键竞争的请求能读到赢家的提交。
        return inserted.map(note -> new Saved(note, true))
                .orElseGet(() -> new Saved(notes.findByVersion(WORKSPACE, sourceId, version)
                        .orElseThrow(() -> new NoteException(ErrorCode.NOTE_SUMMARY_REQUIRED)), false));
    }
}
