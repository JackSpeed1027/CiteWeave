package dev.citeweave.source;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 普通单行查询继承 BaseMapper，队列状态转换使用 PostgreSQL Mapper。 */
@Mapper
interface SummaryTaskMapper extends BaseMapper<SummaryTaskRow> {
    SummaryTaskRow insertQueued(@Param("id") UUID id, @Param("sourceId") long sourceId);

    List<SummaryTaskRow> listRecent(@Param("limit") int limit);

    int expire();

    SummaryTaskRow claim(@Param("leaseSeconds") int leaseSeconds);

    int fail(@Param("id") UUID id, @Param("attempt") int attempt, @Param("error") String error);

    SummaryTaskRow retry(@Param("id") UUID id);
}
