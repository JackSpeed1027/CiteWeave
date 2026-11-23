package dev.citeweave.video.bilibili;

import static dev.citeweave.video.bilibili.BilibiliPreviewException.Reason.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Bilibili 公共元数据 Adapter。仅向固定平台端点提交已识别的 BV/AV 编号。 */
@Component
class BilibiliMetadataClient {
    record Part(int page, String cid, String title, int durationSeconds) {}
    record Preview(String bvid, String title, List<Part> parts, int suggestedPart) {}

    private static final String VIEW_ENDPOINT = "https://api.bilibili.com/x/web-interface/view";
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private final ObjectMapper mapper;
    private final RestClient client;

    @Autowired
    BilibiliMetadataClient(ObjectMapper mapper) {
        this(mapper, createClient());
    }

    BilibiliMetadataClient(ObjectMapper mapper, RestClient client) {
        this.mapper = mapper;
        this.client = client;
    }

    /** 先校验输入类型，再读取平台响应并根据分 P 信息给出建议选项。 */
    Preview preview(BilibiliInputParser.Reference reference) {
        if (reference.kind() == BilibiliInputParser.Kind.SHORT) {
            throw new BilibiliPreviewException(SHORT_LINK_UNSUPPORTED);
        }
        boolean isBv = reference.kind() == BilibiliInputParser.Kind.BV;
        String parameter = isBv ? "bvid" : "aid";
        String id = isBv ? reference.value() : reference.value().substring(2);
        try {
            return client.get().uri(VIEW_ENDPOINT + "?" + parameter + "={id}", id)
                    .exchange((request, response) -> {
                        switch (response.getStatusCode().value()) {
                            case 404 -> throw new BilibiliPreviewException(VIDEO_NOT_FOUND);
                            case 429 -> throw new BilibiliPreviewException(RATE_LIMITED);
                            case 200 -> { }
                            default -> throw new BilibiliPreviewException(UPSTREAM_UNAVAILABLE);
                        }
                        byte[] body = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                        if (body.length > MAX_RESPONSE_BYTES) {
                            throw new BilibiliPreviewException(UPSTREAM_UNAVAILABLE);
                        }
                        return parsePreview(mapper.readTree(body), reference.requestedPart());
                    });
        } catch (RestClientException exception) {
            throw new BilibiliPreviewException(isTimeout(exception) ? UPSTREAM_TIMEOUT : UPSTREAM_UNAVAILABLE);
        }
    }

    private Preview parsePreview(JsonNode root, Integer requestedPart) {
        if (root == null || !root.path("code").isInt()) {
            throw new BilibiliPreviewException(UPSTREAM_UNAVAILABLE);
        }
        switch (root.path("code").intValue()) {
            case -404 -> throw new BilibiliPreviewException(VIDEO_NOT_FOUND);
            case 0 -> { }
            default -> throw new BilibiliPreviewException(UPSTREAM_UNAVAILABLE);
        }

        JsonNode data = root.path("data");
        JsonNode pages = data.path("pages");
        if (!pages.isArray() || pages.isEmpty()) {
            throw new BilibiliPreviewException(UPSTREAM_UNAVAILABLE);
        }

        String bvid = readText(data.path("bvid"));
        String title = readText(data.path("title"));
        var parts = new ArrayList<Part>();
        var pageNumbers = new HashSet<Integer>();
        for (JsonNode page : pages) {
            int number = readInt(page.path("page"), 1);
            if (!pageNumbers.add(number)) {
                throw new BilibiliPreviewException(UPSTREAM_UNAVAILABLE);
            }
            JsonNode cid = page.path("cid");
            if (!cid.isIntegralNumber() || !cid.canConvertToLong() || cid.longValue() <= 0) {
                throw new BilibiliPreviewException(UPSTREAM_UNAVAILABLE);
            }
            // 公开单 P 视频可能省略分 P 标题，用总标题保留可读名称。
            JsonNode partTitle = page.path("part");
            String fallbackTitle = pages.size() == 1 ? title : "P" + number;
            if (!partTitle.isMissingNode() && !partTitle.isNull() && !partTitle.isTextual()) {
                throw new BilibiliPreviewException(UPSTREAM_UNAVAILABLE);
            }
            String displayTitle = partTitle.asText("").isBlank() ? fallbackTitle : partTitle.textValue();
            parts.add(new Part(number, cid.asText(), displayTitle, readInt(page.path("duration"), 0)));
        }
        if (requestedPart != null && !pageNumbers.contains(requestedPart)) {
            throw new BilibiliPreviewException(PART_NOT_FOUND);
        }
        int suggestedPart = requestedPart == null ? parts.getFirst().page() : requestedPart;
        return new Preview(bvid, title, List.copyOf(parts), suggestedPart);
    }

    private static String readText(JsonNode node) {
        if (!node.isTextual() || node.textValue().isBlank()) {
            throw new BilibiliPreviewException(UPSTREAM_UNAVAILABLE);
        }
        return node.textValue();
    }

    private static int readInt(JsonNode node, int minimum) {
        if (!node.isIntegralNumber() || !node.canConvertToInt() || node.intValue() < minimum) {
            throw new BilibiliPreviewException(UPSTREAM_UNAVAILABLE);
        }
        return node.intValue();
    }

    private static boolean isTimeout(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static RestClient createClient() {
        // 平台重定向交给调用方处理为可见的失败，禁止客户端自动访问新地址。
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
