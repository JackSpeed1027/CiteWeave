package dev.citeweave.source;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.citeweave.source.TranscriptNoteMapper.NoteRow;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** 按工作区保存已发布摘要的笔记快照。 */
@Repository
class TranscriptNoteRepository {
    record Header(UUID id, long sourceId, String title, OffsetDateTime createdAt) {}
    record Note(Header note, TranscriptSummaryService.Result snapshot) {}

    private final TranscriptNoteMapper notes;
    private final ObjectMapper mapper;

    TranscriptNoteRepository(TranscriptNoteMapper notes, ObjectMapper mapper) {
        this.notes = notes;
        this.mapper = mapper;
    }

    List<Header> list(String workspace) {
        return notes.selectList(Wrappers.lambdaQuery(NoteRow.class)
                .select(NoteRow::getId, NoteRow::getSourceId, NoteRow::getTitle, NoteRow::getCreatedAt)
                .eq(NoteRow::getWorkspaceKey, workspace)
                .orderByDesc(NoteRow::getCreatedAt, NoteRow::getId))
                .stream().map(NoteRow::header).toList();
    }

    Optional<Note> find(String workspace, UUID id) {
        return Optional.ofNullable(notes.selectOne(Wrappers.lambdaQuery(NoteRow.class)
                .eq(NoteRow::getWorkspaceKey, workspace).eq(NoteRow::getId, id))).map(this::mapNote);
    }

    /** 用数据库唯一约束处理相同资料版本的并发保存。只复制已发布的服务器结果。 */
    Optional<Note> insert(String workspace, long sourceId, String version) {
        return Optional.ofNullable(notes.insertSnapshot(UUID.randomUUID(), workspace, sourceId, version))
                .map(this::mapNote);
    }

    Optional<Note> findByVersion(String workspace, long sourceId, String version) {
        return Optional.ofNullable(notes.selectOne(Wrappers.lambdaQuery(NoteRow.class)
                .eq(NoteRow::getWorkspaceKey, workspace).eq(NoteRow::getSourceId, sourceId)
                .eq(NoteRow::getSourceVersion, version))).map(this::mapNote);
    }

    private Note mapNote(NoteRow row) {
        try {
            return new Note(row.header(), mapper.readValue(row.snapshot, TranscriptSummaryService.Result.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid transcript note snapshot", exception);
        }
    }
}
