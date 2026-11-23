package dev.citeweave.source;

import dev.citeweave.web.ApiProblems;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
@Order(-1)
class SourceErrorHandler {
    @ExceptionHandler(SourceException.class)
    ProblemDetail sourceProblem(SourceException exception) {
        HttpStatus status = switch (exception.code()) {
            case SOURCE_NOT_FOUND, SOURCE_TRANSCRIPT_NOT_FOUND, SOURCE_EVIDENCE_NOT_FOUND,
                 SOURCE_PARAGRAPHS_NOT_FOUND, SOURCE_PDF_PAGES_UNAVAILABLE -> HttpStatus.NOT_FOUND;
            case SOURCE_TITLE_INVALID, SOURCE_FILE_INVALID, SOURCE_FILE_UNREADABLE -> HttpStatus.BAD_REQUEST;
            case SOURCE_FILE_TYPE_UNSUPPORTED -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case SOURCE_CONTENT_INVALID, SOURCE_TRANSCRIPT_INVALID, SOURCE_SUBTITLE_OUTSIDE_VIDEO ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ApiProblems.create(status, exception.code().name(), exception.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail tooLarge() {
        return ApiProblems.create(HttpStatus.PAYLOAD_TOO_LARGE, "UPLOAD_TOO_LARGE", "文件不能超过 10 MB。");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail malformedBody() {
        return ApiProblems.create(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "请求正文缺失或格式有误。");
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    ProblemDetail missingPart() {
        return ApiProblems.create(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "缺少所需的上传文件或请求参数。");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail invalidParameter() {
        return ApiProblems.create(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "请求参数格式有误。");
    }
}
