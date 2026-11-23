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

/** 研究快照查询及请求、断点的条件写入。 */
@Mapper
interface ResearchHistoryMapper extends BaseMapper<ResearchHistoryMapper.HistoryRow> {
    HistoryRow insertSnapshot(@Param("id") UUID id, @Param("workspace") String workspace,
                              @Param("question") String question, @Param("sources") String sources,
                              @Param("snapshot") String snapshot);

    int claim(@Param("workspace") String workspace, @Param("key") UUID key, @Param("fingerprint") String fingerprint,
              @Param("digest") String digest, @Param("scope") String scope, @Param("question") String question);

    int claimResume(@Param("workspace") String workspace, @Param("key") UUID key, @Param("parent") UUID parent,
                    @Param("fingerprint") String fingerprint, @Param("digest") String digest);

    RequestViewRow requestView(@Param("workspace") String workspace, @Param("key") UUID key);

    ClaimRow claimed(@Param("workspace") String workspace, @Param("key") UUID key);

    int appendCheckpoint(@Param("workspace") String workspace, @Param("key") UUID key,
                         @Param("before") String before, @Param("priorContext") String priorContext,
                         @Param("after") String after, @Param("context") String context);

    int completeRequest(@Param("workspace") String workspace, @Param("key") UUID key,
                        @Param("recordId") UUID recordId, @Param("trace") String trace);

    int fail(@Param("workspace") String workspace, @Param("key") UUID key);

    void expireIfDue(@Param("workspace") String workspace, @Param("key") UUID key);

    void markUnknown(@Param("workspace") String workspace, @Param("key") UUID key);

    List<HistoryRow> recent(@Param("workspace") String workspace, @Param("limit") int limit);

    @TableName("research_history")
    class HistoryRow {
        @TableId(type = IdType.INPUT)
        UUID id;
        String workspaceKey;
        String question;
        OffsetDateTime createdAt;
        String sourceIds;
        String snapshot;

        public UUID getId() { return id; }
        public String getWorkspaceKey() { return workspaceKey; }

        ResearchHistoryRepository.Header header() {
            return new ResearchHistoryRepository.Header(id, question, createdAt);
        }
    }

    class RequestViewRow {
        String status;
        String question;
        String checkpointScope;
        String checkpointTrace;
        UUID recordId;
        UUID nextKey;
        boolean hasCheckpoint;
    }

    class ClaimRow {
        String fingerprint;
        String inputFingerprint;
        String status;
        UUID recordId;
        String checkpointTokenDigest;
        String checkpointScope;
        String checkpointTrace;
        String checkpointContext;
        boolean active;
    }
}
