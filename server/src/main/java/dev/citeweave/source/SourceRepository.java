package dev.citeweave.source;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.annotation.JsonInclude;
import dev.citeweave.source.BilibiliSubtitleService.Origin;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class SourceRepository {
    record Summary(long id, String title, String kind, String filename, OffsetDateTime createdAt) {}
    record Detail(Summary source, String content, @JsonInclude(JsonInclude.Include.NON_NULL) Origin videoOrigin,
                  @JsonInclude(JsonInclude.Include.NON_NULL) LocalVideoSubtitleService.Origin localVideoOrigin) {
        Detail(Summary source, String content) {
            this(source, content, null, null);
        }
    }

    private final SourceMapper sources;
    private final BilibiliTrackMapper tracks;

    SourceRepository(SourceMapper sources, BilibiliTrackMapper tracks) {
        this.sources = sources;
        this.tracks = tracks;
    }

    List<Summary> list() {
        return sources.selectList(Wrappers.lambdaQuery(SourceRow.class)
                .select(SourceRow::getId, SourceRow::getTitle, SourceRow::getKind, SourceRow::getFilename,
                        SourceRow::getCreatedAt)
                .orderByDesc(SourceRow::getCreatedAt, SourceRow::getId))
                .stream().map(SourceRow::summary).toList();
    }

    /** 供跨资料问答选取。历史 PDF 没有原始页码，继续通过资料库查看。 */
    List<Summary> questionSources() {
        return sources.questionSources().stream().map(SourceRow::summary).toList();
    }

    /** 只查询本次选中的 ID，预检本机资料存在且可参与证据读取。 */
    boolean allQuestionSourcesAvailable(List<Long> ids) {
        return ids.isEmpty() || sources.countQuestionSources(ids) == ids.size();
    }

    /** 只查询当前工作区内已收藏的资料，排序与资料库一致。 */
    List<Summary> listFavorites(String workspace) {
        return sources.listFavorites(workspace).stream().map(SourceRow::summary).toList();
    }

    /** 资料不存在时返回空；没有收藏记录时返回 false。 */
    Optional<Boolean> favorite(String workspace, long id) {
        return Optional.ofNullable(sources.favorite(workspace, id));
    }

    /** 原子写入目标状态，重复请求和并发提交不会新建重复收藏。 */
    Optional<Boolean> setFavorite(String workspace, long id, boolean favorited) {
        return Optional.ofNullable(sources.setFavorite(workspace, id, favorited));
    }

    Optional<Detail> find(long id) {
        return Optional.ofNullable(sources.findWithVideo(id)).map(SourceRow::detail);
    }

    /** 附件绑定由调用方的资料导入事务保护。 */
    void bindBilibili(long sourceId, Origin origin) {
        tracks.insert(new BilibiliTrackMapper.TrackRow(sourceId, origin));
    }

    Detail insert(String title, String kind, String filename, String content) {
        return sources.insertReturning(title, kind, filename, content).detail();
    }

    /** 解析完成后再开启事务，正文和分页文本一起成功或回滚。 */
    @Transactional
    Detail insertPdf(String title, String filename, SourceParser.Parsed pdf) {
        var detail = insert(title, pdf.kind(), filename, pdf.content());
        if (!pdf.pdfPages().isEmpty()) {
            sources.insertPdfPages(detail.source().id(), pdf.pdfPages());
        }
        return detail;
    }

    List<SourceParser.PdfPage> pdfPages(long id) {
        return sources.pdfPages(id);
    }
}
