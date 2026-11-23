package dev.citeweave.source;

import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 管理复合版本键和 PostgreSQL 的摘要原子发布。 */
@Mapper
interface TranscriptSummaryMapper {
    String findResult(@Param("id") long id, @Param("version") String version);

    int publish(@Param("id") long id, @Param("version") String version, @Param("kind") String kind,
                @Param("content") String content, @Param("result") String result, @Param("inputIds") String inputIds,
                @Param("taskId") UUID taskId, @Param("attempt") int attempt);

    int completeTask(@Param("id") long id, @Param("version") String version, @Param("kind") String kind,
                     @Param("content") String content, @Param("taskId") UUID taskId, @Param("attempt") int attempt);
}
