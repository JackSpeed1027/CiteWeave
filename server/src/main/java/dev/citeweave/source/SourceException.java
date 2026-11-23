package dev.citeweave.source;

final class SourceException extends RuntimeException {
    enum Code {
        SOURCE_NOT_FOUND,
        SOURCE_TRANSCRIPT_NOT_FOUND,
        SOURCE_EVIDENCE_NOT_FOUND,
        SOURCE_PARAGRAPHS_NOT_FOUND,
        SOURCE_PDF_PAGES_UNAVAILABLE,
        SOURCE_TITLE_INVALID,
        SOURCE_FILE_INVALID,
        SOURCE_FILE_UNREADABLE,
        SOURCE_FILE_TYPE_UNSUPPORTED,
        SOURCE_CONTENT_INVALID,
        SOURCE_TRANSCRIPT_INVALID,
        SOURCE_SUBTITLE_OUTSIDE_VIDEO
    }

    private final Code code;

    SourceException(Code code, String message) {
        this(code, message, null);
    }

    SourceException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    Code code() {
        return code;
    }
}
