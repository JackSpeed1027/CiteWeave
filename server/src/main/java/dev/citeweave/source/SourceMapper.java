package dev.citeweave.source;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 资料基本查询继承 BaseMapper，跨表查询和原子更新由 XML 实现。 */
@Mapper
interface SourceMapper extends BaseMapper<SourceRow> {
    List<SourceRow> questionSources();
    long countQuestionSources(@Param("ids") List<Long> ids);
    List<SourceRow> listFavorites(@Param("workspace") String workspace);
    Boolean favorite(@Param("workspace") String workspace, @Param("id") long id);
    Boolean setFavorite(@Param("workspace") String workspace, @Param("id") long id,
                        @Param("favorited") boolean favorited);
    SourceRow findWithVideo(@Param("id") long id);
    SourceRow insertReturning(@Param("title") String title, @Param("kind") String kind,
                              @Param("filename") String filename, @Param("content") String content);
    void insertPdfPages(@Param("id") long id, @Param("pages") List<SourceParser.PdfPage> pages);
    List<SourceParser.PdfPage> pdfPages(@Param("id") long id);
}
