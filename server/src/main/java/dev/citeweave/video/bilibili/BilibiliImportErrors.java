package dev.citeweave.video.bilibili;

import dev.citeweave.web.ApiProblems;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 预览与字幕提交共享平台错误语义。 */
@RestControllerAdvice(assignableTypes = {BilibiliPreviewController.class, BilibiliSubtitleController.class})
class BilibiliImportErrors {
    @ExceptionHandler(BilibiliInputException.class)
    ProblemDetail invalidInput(BilibiliInputException exception) {
        return ApiProblems.create(HttpStatus.BAD_REQUEST, "BILIBILI_INPUT_INVALID", exception.getMessage());
    }

    @ExceptionHandler(BilibiliPreviewException.class)
    ProblemDetail previewFailed(BilibiliPreviewException exception) {
        var reason = exception.reason;
        HttpStatus status = switch (reason) {
            case SHORT_LINK_UNSUPPORTED, PART_NOT_FOUND -> HttpStatus.UNPROCESSABLE_ENTITY;
            case PART_CHANGED -> HttpStatus.CONFLICT;
            case VIDEO_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case RATE_LIMITED -> HttpStatus.SERVICE_UNAVAILABLE;
            case UPSTREAM_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case UPSTREAM_UNAVAILABLE -> HttpStatus.BAD_GATEWAY;
        };
        return ApiProblems.create(status, "BILIBILI_" + reason.name(), exception.getMessage());
    }
}
