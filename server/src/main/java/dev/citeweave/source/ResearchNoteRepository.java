package dev.citeweave.source;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.citeweave.source.TranscriptSummaryEngine.Evidence;
import dev.citeweave.source.ResearchNoteMapper.NoteRow;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** 研究笔记独立存储，兼容原来的字幕摘要快照。 */
@Repository
class ResearchNoteRepository {
    record Header(UUID id, UUID researchId, String title, long revision, OffsetDateTime updatedAt) {}
    record Note(Header note, String markdown, List<Evidence> citations) {}

    private static final int RECENT_LIMIT = 50;
    private static final TypeReference<List<Evidence>> CITATIONS = new TypeReference<>() {};
    private final ResearchNoteMapper notes;
    private final ObjectMapper mapper;

    ResearchNoteRepository(ResearchNoteMapper notes, ObjectMapper mapper) {
        this.notes = notes;
        this.mapper = mapper;
    }

    /** 查询最近编辑的个人研究笔记。 */
    List<Header> list(String workspace) {
        return notes.listRecent(workspace, RECENT_LIMIT).stream().map(NoteRow::header).toList();
    }

    Optional<Note> find(String workspace, UUID id) {
        return Optional.ofNullable(notes.selectOne(Wrappers.lambdaQuery(NoteRow.class)
                .eq(NoteRow::getWorkspaceKey, workspace).eq(NoteRow::getId, id))).map(this::note);
    }

    Optional<Note> findByResearch(String workspace, UUID id) {
        return Optional.ofNullable(notes.selectOne(Wrappers.lambdaQuery(NoteRow.class)
                .eq(NoteRow::getWorkspaceKey, workspace).eq(NoteRow::getResearchId, id))).map(this::note);
    }

    /** 一次研究只生成一份初稿，重复点击不会覆盖已编辑内容。 */
    Optional<Note> insert(String workspace, UUID researchId, String title, String markdown, List<Evidence> citations) {
        return Optional.ofNullable(notes.insertDraft(UUID.randomUUID(), workspace, researchId, title, markdown,
                encode(citations))).map(this::note);
    }

    /** 只更新浏览器读到的版本，避免旧标签页覆盖更新。引用保持为创建时的快照。 */
    Optional<Note> update(String workspace, UUID id, long revision, String title, String markdown) {
        return Optional.ofNullable(notes.updateRevision(workspace, id, revision, title, markdown)).map(this::note);
    }

    private Note note(NoteRow row) {
        try {
            return new Note(row.header(), row.markdown, mapper.readValue(row.citations, CITATIONS));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid research note citations", exception);
        }
    }

    private String encode(List<Evidence> citations) {
        try {
            return mapper.writeValueAsString(citations);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not encode research note citations", exception);
        }
    }
}
