package dev.citeweave.video.bilibili;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 返回公开元数据及分 P，供用户在导入前确认。 */
@RestController
@RequestMapping("/api/video-imports/bilibili")
class BilibiliPreviewController {
    private final BilibiliInputParser parser;
    private final BilibiliMetadataClient metadata;

    BilibiliPreviewController(BilibiliInputParser parser, BilibiliMetadataClient metadata) {
        this.parser = parser;
        this.metadata = metadata;
    }

    /** 查询操作没有入库副作用。 */
    @PostMapping(path = "/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    BilibiliMetadataClient.Preview preview(@RequestBody BilibiliInputController.NormalizeRequest request) {
        return metadata.preview(parser.parse(request.input()));
    }
}
