package dev.citeweave.source;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 将用户字幕及服务端核实的视频身份作为同一笔导入保存。 */
@Service
public class BilibiliSubtitleService {
    /** 手动附件的来源。字幕资料 ID 标识一条不可变轨道。 */
    public record Origin(String bvid, int page, String cid, String videoTitle, String partTitle, String trackSource) {}

    private final SourceService sources;
    private final SourceRepository repository;

    BilibiliSubtitleService(SourceService sources, SourceRepository repository) {
        this.sources = sources;
        this.repository = repository;
    }

    /** 两张表同事务写入，来源写入失败会回滚字幕资料。 */
    @Transactional
    public long upload(MultipartFile file, Origin origin) {
        var source = sources.uploadSubtitle(file);
        long id = source.source().id();
        repository.bindBilibili(id, origin);
        return id;
    }
}
