package dev.citeweave.source;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 读取字幕笔记及原子复制已发布摘要。 */
@Mapper
interface TranscriptNoteMapper extends BaseMapper<TranscriptNoteMapper.NoteRow> {
    NoteRow insertSnapshot(@Param("id") UUID id, @Param("workspace") String workspace,
                           @Param("sourceId") long sourceId, @Param("version") String version);

    @TableName("transcript_notes")
    class NoteRow {
        @TableId(type = IdType.INPUT)
        UUID id;
        String workspaceKey;
        Long sourceId;
        String sourceVersion;
        String title;
        OffsetDateTime createdAt;
        String snapshot;

        public UUID getId() { return id; }
        public String getWorkspaceKey() { return workspaceKey; }
        public Long getSourceId() { return sourceId; }
        public String getSourceVersion() { return sourceVersion; }
        public String getTitle() { return title; }
        public OffsetDateTime getCreatedAt() { return createdAt; }

        TranscriptNoteRepository.Header header() {
            return new TranscriptNoteRepository.Header(id, sourceId, title, createdAt);
        }
    }
}
