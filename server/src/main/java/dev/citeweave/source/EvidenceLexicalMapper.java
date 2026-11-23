package dev.citeweave.source;

import dev.citeweave.source.TranscriptSummaryEngine.Evidence;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 保存复合版本键及 PostgreSQL 词项交集查询。 */
@Mapper
interface EvidenceLexicalMapper {
    record Key(long sourceId, String sourceVersion, String indexVersion) {}
    record Chunk(int ordinal, Evidence evidence, String terms) {}

    boolean exists(@Param("key") Key key);
    int reserve(@Param("key") Key key);
    void insertChunks(@Param("key") Key key, @Param("chunks") List<Chunk> chunks);
    List<Evidence> search(@Param("key") Key key, @Param("terms") String terms, @Param("limit") int limit);
}
