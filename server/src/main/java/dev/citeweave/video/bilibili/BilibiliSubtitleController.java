package dev.citeweave.video.bilibili;

import dev.citeweave.source.BilibiliSubtitleService;
import dev.citeweave.source.BilibiliSubtitleService.Origin;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 接收确认后的分 P 和用户上传字幕，平台复核完成后才进入数据库事务。 */
@RestController
@RequestMapping("/api/video-imports/bilibili")
class BilibiliSubtitleController {
    record Imported(long sourceId) {}

    private final BilibiliInputParser parser;
    private final BilibiliMetadataClient metadata;
    private final BilibiliSubtitleService subtitles;

    BilibiliSubtitleController(BilibiliInputParser parser, BilibiliMetadataClient metadata,
                               BilibiliSubtitleService subtitles) {
        this.parser = parser;
        this.metadata = metadata;
        this.subtitles = subtitles;
    }

    /** 重查所选分 P，防止变更后的平台编号与用户确认的 CID 不一致。 */
    @PostMapping(path = "/subtitles", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<Imported> upload(@RequestParam String bvid, @RequestParam int page,
                                    @RequestParam String cid, @RequestParam MultipartFile file) {
        var reference = parser.parse(bvid);
        if (reference.kind() != BilibiliInputParser.Kind.BV || !reference.value().equals(bvid)
                || page < 1 || !cid.matches("[1-9][0-9]{0,18}")) {
            throw new BilibiliInputException("请提交预览返回的 BV 编号、正整数分 P 和 CID。");
        }
        var preview = metadata.preview(reference);
        var part = preview.parts().stream().filter(item -> item.page() == page).findFirst();
        if (!bvid.equals(preview.bvid()) || part.isEmpty() || !part.get().cid().equals(cid)) {
            throw new BilibiliPreviewException(BilibiliPreviewException.Reason.PART_CHANGED);
        }
        long id = subtitles.upload(file, new Origin(preview.bvid(), page, part.get().cid(),
                preview.title(), part.get().title(), "USER_UPLOAD"));
        return ResponseEntity.created(URI.create("/api/sources/" + id)).body(new Imported(id));
    }
}
