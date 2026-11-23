package dev.citeweave.video.bilibili;

/** 将平台查询失败映射为可供前端处理的稳定错误。 */
final class BilibiliPreviewException extends RuntimeException {
    enum Reason {
        SHORT_LINK_UNSUPPORTED("请打开短链后粘贴完整视频链接，或直接输入 BV/AV 编号。"),
        PART_NOT_FOUND("所选分 P 不存在，请检查链接中的 p 参数。"),
        PART_CHANGED("分 P 与确认时不一致，请重新预览再上传字幕。"),
        VIDEO_NOT_FOUND("未找到该视频，请检查编号或在 Bilibili 打开确认。"),
        RATE_LIMITED("Bilibili 当前限制了请求频率，请稍后重试。"),
        UPSTREAM_TIMEOUT("查询 Bilibili 超时，请稍后重试。"),
        UPSTREAM_UNAVAILABLE("目前无法获取该视频的公开元数据，请确认访问权限或稍后重试。");

        final String detail;

        Reason(String detail) {

            this.detail = detail;
        }
    }

    final Reason reason;

    BilibiliPreviewException(Reason reason) {
        super(reason.detail);
        this.reason = reason;
    }
}
