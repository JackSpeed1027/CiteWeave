package dev.citeweave.source;

import dev.citeweave.source.TranscriptSummaryEngine.Evidence;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;

/** 为摘要和问答从同一字幕解析器生成版本化证据。 */
@Component
class TranscriptEvidenceReader {
    private final SourceService sources;
    private final SourceParser parser;

    TranscriptEvidenceReader(SourceService sources, SourceParser parser) {
        this.sources = sources;
        this.parser = parser;
    }

    SourceRepository.Detail load(long id) {
        var detail = sources.find(id);
        if (!parser.isTranscript(detail.source().kind())) {
            throw new SourceException(SourceException.Code.SOURCE_TRANSCRIPT_NOT_FOUND, "这份资料没有字幕轨道。");
        }
        return detail;
    }

    List<Evidence> read(SourceRepository.Detail source, String version) {
        String sourceId = Long.toString(source.source().id());
        return parser.transcript(source.source().kind(), source.content()).stream()
                .map(segment -> new Evidence(sourceId + ":" + version + ":" + segment.sequence(),
                        sourceId, version, "transcript", segment.startMs(), segment.endMs(), segment.text()))
                .toList();
    }

    /** 保留既有哈希算法与版本前缀，旧摘要和笔记链接可继续读取。 */
    String version(SourceRepository.Detail source) {
        return transcriptVersion(source.source().kind(), source.content());
    }

    static String transcriptVersion(String kind, String content) {
        return version(kind, content, "transcript-v1");
    }

    /** 每种解析规则使用独立版本前缀，避免复用错误的定位。 */
    static String version(SourceRepository.Detail source, String parserVersion) {
        return version(source.source().kind(), source.content(), parserVersion);
    }

    private static String version(String kind, String content, String parserVersion) {
        String input = parserVersion + "\n" + kind + "\n" + content;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
