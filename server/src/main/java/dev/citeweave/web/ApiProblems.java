package dev.citeweave.web;

import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** 在 HTTP 边界构造带项目稳定错误码的 Problem Details。 */
public final class ApiProblems {
    private ApiProblems() {}

    public static ProblemDetail create(HttpStatus status, String code, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setType(URI.create("urn:citeweave:problem:" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        body.setProperty("code", code);
        return body;
    }
}
