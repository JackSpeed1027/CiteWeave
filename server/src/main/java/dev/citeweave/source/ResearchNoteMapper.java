package dev.citeweave.source;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 查询研究笔记，按唯一约束或当前修订号提交写入。 */
@Mapper
interface ResearchNoteMapper extends BaseMapper<ResearchNoteMapper.NoteRow> {
    List<NoteRow> listRecent(@Param("workspace") String workspace, @Param("limit") int limit);

    NoteRow insertDraft(@Param("id") UUID id, @Param("workspace") String workspace,
                        @Param("researchId") UUID researchId, @Param("title") String title,
                        @Param("markdown") String markdown, @Param("citations") String citations);

    NoteRow updateRevision(@Param("workspace") String workspace, @Param("id") UUID id,
                           @Param("revision") long revision, @Param("title") String title,
                           @Param("markdown") String markdown);

    @TableName("research_notes")
    class NoteRow {
        @TableId(type = IdType.INPUT)
        UUID id;
        String workspaceKey;
        UUID researchId;
        String title;
        long revision;
        OffsetDateTime updatedAt;
        String markdown;
        String citations;

        public UUID getId() { return id; }
        public String getWorkspaceKey() { return workspaceKey; }
        public UUID getResearchId() { return researchId; }

        ResearchNoteRepository.Header header() {
            return new ResearchNoteRepository.Header(id, researchId, title, revision, updatedAt);
        }
    }
}
