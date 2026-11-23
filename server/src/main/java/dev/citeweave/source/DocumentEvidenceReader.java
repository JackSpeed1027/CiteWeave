package dev.citeweave.source;

import dev.citeweave.source.TranscriptSummaryEngine.Evidence;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 提供文档定位，并按资料类型读取混合证据。Markdown 保留源字符。 */
@Component
class DocumentEvidenceReader {
    record IndexSource(SourceRepository.Detail detail, String version) {
        long id() { return detail.source().id(); }
    }

    private static final Set<String> KINDS = Set.of("TEXT", "TXT", "MARKDOWN");
    private static final int PAGE_CHUNK_SIZE = 1400;
    private final SourceService sources;
    private final SourceRepository repository;
    private final TranscriptEvidenceReader transcripts;

    DocumentEvidenceReader(SourceService sources, SourceRepository repository, TranscriptEvidenceReader transcripts) {
        this.sources = sources;
        this.repository = repository;
        this.transcripts = transcripts;
    }

    /** 用空行分段，按原文顺序编号；时间戳只用于字幕。 */
    List<Evidence> read(long id) {
        return paragraphs(sources.find(id));
    }

    /** 根据资料类型读取同一版本的问答证据。 */
    List<Evidence> readForQuestion(long id) {
        var source = sources.find(id);
        return source.source().kind().equals("PDF") ? pdfPages(source) : paragraphs(source);
    }

    /** 按明确选中的资料读取证据，沿用各自的解析版本及引用坐标。 */
    List<Evidence> readForMixedQuestion(long id) {
        return readForMixedQuestion(indexSource(id));
    }

    /** 仅计算版本，索引命中时无需对正文重新切片。 */
    IndexSource indexSource(long id) {
        var detail = sources.find(id);
        String version = switch (detail.source().kind()) {
            case "SRT", "VTT" -> transcripts.version(detail);
            case "PDF" -> TranscriptEvidenceReader.version(detail, "pdf-pages-v1");
            default -> TranscriptEvidenceReader.version(detail, "paragraph-v1");
        };
        return new IndexSource(detail, version);
    }

    List<Evidence> readForMixedQuestion(IndexSource input) {
        return switch (input.detail().source().kind()) {
            case "SRT", "VTT" -> transcripts.read(input.detail(), input.version());
            case "PDF" -> pdfPages(input.detail(), input.version());
            default -> paragraphs(input.detail(), input.version());
        };
    }

    /** 只从指定资料的当前版本回读完整证据 ID。 */
    Evidence readEvidence(long id, String evidenceId) {
        if (evidenceId.length() <= 160 && evidenceId.startsWith(id + ":")) {
            return readForMixedQuestion(id).stream().filter(item -> item.evidenceId().equals(evidenceId))
                    .findFirst().orElseThrow(DocumentEvidenceReader::missingEvidence);
        }
        throw missingEvidence();
    }

    private static SourceException missingEvidence() {
        return new SourceException(SourceException.Code.SOURCE_EVIDENCE_NOT_FOUND, "找不到当前资料版本中的这条证据。");
    }

    /** 列出具备证据坐标的资料供选择。 */
    List<SourceRepository.Summary> questionSources() {
        return repository.questionSources();
    }

    List<Evidence> pdfPages(long id) {
        return pdfPages(sources.find(id));
    }

    private List<Evidence> paragraphs(SourceRepository.Detail detail) {
        return paragraphs(detail, TranscriptEvidenceReader.version(detail, "paragraph-v1"));
    }

    private List<Evidence> paragraphs(SourceRepository.Detail detail, String version) {
        if (!KINDS.contains(detail.source().kind())) {
            throw new SourceException(SourceException.Code.SOURCE_PARAGRAPHS_NOT_FOUND, "这份资料暂无可定位的文本段落。");
        }
        long id = detail.source().id();
        String content = detail.content().replace("\r\n", "\n").replace('\r', '\n');
        var result = new ArrayList<Evidence>();
        for (String paragraph : content.split("\\n(?:[ \\t]*+\\n)++")) {
            if (!paragraph.isBlank()) {
                int sequence = result.size() + 1;
                result.add(new Evidence(id + ":" + version + ":" + sequence, Long.toString(id), version,
                        "paragraph", null, null, paragraph, sequence, null));
            }
        }
        return List.copyOf(result);
    }

    private List<Evidence> pdfPages(SourceRepository.Detail detail) {
        return pdfPages(detail, TranscriptEvidenceReader.version(detail, "pdf-pages-v1"));
    }

    private List<Evidence> pdfPages(SourceRepository.Detail detail, String version) {
        if (!detail.source().kind().equals("PDF")) throw pagesUnavailable("这份资料不是 PDF。");
        long id = detail.source().id();
        var pages = repository.pdfPages(id);
        if (pages.isEmpty()) throw pagesUnavailable("这份 PDF 只有历史正文记录。重新导入可建立页码引用。");

        var result = new ArrayList<Evidence>();
        for (var page : pages) {
            int start = 0;
            String text = page.content();
            // 同一页可能超出候选长度，优先按换行分片并保护 UTF-16 代理对。
            while (start < text.length()) {
                int end = Math.min(start + PAGE_CHUNK_SIZE, text.length());
                if (end < text.length()) {
                    int newline = text.lastIndexOf('\n', end - 1);
                    if (newline >= start + PAGE_CHUNK_SIZE / 2) end = newline + 1;
                    else if (Character.isSurrogatePair(text.charAt(end - 1), text.charAt(end))) end--;
                }
                String fragment = text.substring(start, end);
                if (!fragment.isBlank()) {
                    String evidenceId = id + ":" + version + ":p" + page.number() + ":" + start;
                    result.add(new Evidence(evidenceId, Long.toString(id), version,
                            "page", null, null, fragment, null, page.number()));
                }
                start = end;
            }
        }
        return List.copyOf(result);
    }

    private static SourceException pagesUnavailable(String message) {
        return new SourceException(SourceException.Code.SOURCE_PDF_PAGES_UNAVAILABLE, message);
    }
}
