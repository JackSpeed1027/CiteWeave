package dev.citeweave.video.bilibili;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 只识别输入，不请求平台。结果作为后续预览 Adapter 的输入。 */
@Component
class BilibiliInputParser {
    enum Kind { BV, AV, SHORT }

    record Reference(Kind kind, String value, String canonicalUrl, Integer requestedPart) {}

    private static final Pattern BV = Pattern.compile("(?i:BV)[A-Za-z0-9]{10}");
    private static final Pattern AV = Pattern.compile("(?i:av)[1-9][0-9]{0,18}");
    private static final Pattern VIDEO_PATH = Pattern.compile("/video/([^/]+)/?");
    private static final Pattern SHORT_PATH = Pattern.compile("/([A-Za-z0-9]{1,64})/?");
    private static final Pattern PART = Pattern.compile("[1-9][0-9]{0,9}");
    private static final Set<String> VIDEO_HOSTS = Set.of("www.bilibili.com", "bilibili.com", "m.bilibili.com");

    Reference parse(String input) {
        if (input == null || input.length() > 2048 || input.isBlank()) {
            throw new BilibiliInputException("请输入 BV/AV 编号或 Bilibili 视频链接，最多 2048 字符。");
        }
        String value = input.strip();
        if (BV.matcher(value).matches() || AV.matcher(value).matches()) {
            return videoReference(value, null);
        }

        URI uri = parseUrl(value);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        // 1. 只匹配允许的主机和路径，不解码路径或跟随短链。
        // 2. 重新构造 HTTPS 地址，避免把原始查询、片段或凭据传给后续 Adapter。
        if (host.equals("b23.tv")) {
            var path = SHORT_PATH.matcher(uri.getRawPath());
            if (!path.matches()) {
                throw new BilibiliInputException("请输入有效的 b23.tv 短链。");
            }
            String token = path.group(1);
            return new Reference(Kind.SHORT, token, "https://b23.tv/" + token, requestedPart(uri.getRawQuery()));
        }
        if (VIDEO_HOSTS.contains(host)) {
            var path = VIDEO_PATH.matcher(uri.getRawPath());
            if (path.matches()) {
                return videoReference(path.group(1), requestedPart(uri.getRawQuery()));
            }
        }
        throw new BilibiliInputException("只接受 Bilibili 视频页和 b23.tv 短链。");
    }

    private URI parseUrl(String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new BilibiliInputException("链接格式无效。");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
            throw new BilibiliInputException("链接必须使用 HTTP 或 HTTPS。");
        }
        if (uri.getHost() == null || !uri.getHost().equalsIgnoreCase(uri.getRawAuthority())) {
            throw new BilibiliInputException("链接必须包含白名单主机，且不能带认证信息或端口。");
        }
        return uri;
    }

    private Reference videoReference(String value, Integer part) {
        if (BV.matcher(value).matches()) {
            // BV 数据部分区分大小写，只规范编号前缀。
            String id = "BV" + value.substring(2);
            return new Reference(Kind.BV, id, "https://www.bilibili.com/video/" + id, part);
        }
        if (AV.matcher(value).matches()) {
            String id = "av" + value.substring(2);
            return new Reference(Kind.AV, id, "https://www.bilibili.com/video/" + id, part);
        }
        throw new BilibiliInputException("视频路径中的 BV/AV 编号无效。");
    }

    private Integer requestedPart(String query) {
        if (query == null) {
            return null;
        }

        Integer part = null;
        for (String parameter : query.split("&")) {
            if (!parameter.equals("p") && !parameter.startsWith("p=")) {
                continue;
            }
            String value = parameter.length() > 2 ? parameter.substring(2) : "";
            if (part != null || !PART.matcher(value).matches()) {
                throw invalidPart();
            }
            try {
                part = Integer.valueOf(value);
            } catch (NumberFormatException exception) {
                throw invalidPart();
            }
        }
        return part;
    }

    private BilibiliInputException invalidPart() {
        return new BilibiliInputException("分 P 参数 p 必须为单个正整数。");
    }
}
