package dev.citeweave.video.bilibili;

import dev.citeweave.web.ApiProblems;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供无网络、无入库副作用的 Bilibili 输入规范化接口。 */
@RestController
@RequestMapping("/api/video-imports/bilibili")
class BilibiliInputController {
    record NormalizeRequest(String input) {}

    private final BilibiliInputParser parser;

    BilibiliInputController(BilibiliInputParser parser) {
        this.parser = parser;
    }

    @PostMapping(path = "/normalize", consumes = MediaType.APPLICATION_JSON_VALUE)
    BilibiliInputParser.Reference normalize(@RequestBody NormalizeRequest request) {
        return parser.parse(request.input());
    }

    @ExceptionHandler(BilibiliInputException.class)
    ProblemDetail invalidInput(BilibiliInputException exception) {
        return ApiProblems.create(HttpStatus.BAD_REQUEST, "BILIBILI_INPUT_INVALID", exception.getMessage());
    }
}
