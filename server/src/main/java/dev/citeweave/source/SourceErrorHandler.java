package dev.citeweave.source;

import java.net.URI;
import java.util.Locale;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
            case SOURCE_NOT_FOUND, SOURCE_TRANSCRIPT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case SOURCE_TITLE_INVALID, SOURCE_FILE_INVALID, SOURCE_FILE_UNREADABLE -> HttpStatus.BAD_REQUEST;
            case SOURCE_FILE_TYPE_UNSUPPORTED -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case SOURCE_CONTENT_INVALID, SOURCE_TRANSCRIPT_INVALID -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return problem(status, exception.code().name(), exception.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail tooLarge() {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "UPLOAD_TOO_LARGE", "文件不能超过 10 MB。");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail malformedBody() {
        return problem(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "请求正文缺失或格式有误。");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ProblemDetail missingPart() {
        return problem(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "缺少所需的上传文件。");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail invalidParameter() {
        return problem(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "请求参数格式有误。");
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setType(URI.create("urn:citeweave:problem:" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        body.setProperty("code", code);
        return body;
    }
}
