package dev.citeweave.video.bilibili;

/** 表示调用者可以修正的 Bilibili 输入错误。 */
final class BilibiliInputException extends RuntimeException {
    BilibiliInputException(String message) {
        super(message);
    }
}
