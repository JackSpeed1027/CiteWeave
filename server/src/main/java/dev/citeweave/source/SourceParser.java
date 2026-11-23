package dev.citeweave.source;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import static dev.citeweave.source.SourceException.Code.*;

@Component
class SourceParser {
    record PdfPage(int number, String content) {}
    record Parsed(String kind, String content, List<PdfPage> pdfPages) {}
    private static final int MAX_CONTENT = 500_000;
    private static final int MAX_PDF_PAGES = 500;

    Parsed file(String filename, byte[] bytes) {
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String kind = switch (extension) {
            case "pdf" -> "PDF";
            case "txt" -> "TXT";
            case "md", "markdown" -> "MARKDOWN";
            case "srt" -> "SRT";
            case "vtt" -> "VTT";
            default -> throw new SourceException(SOURCE_FILE_TYPE_UNSUPPORTED,
                    "支持 PDF、TXT、Markdown、SRT 和 VTT 文件。");
        };
        if (kind.equals("PDF")) return pdf(bytes);
        String content = text(utf8(bytes));
        if (isTranscript(kind)) {
            transcript(kind, content);
        }
        return new Parsed(kind, content, List.of());
    }

    boolean isTranscript(String kind) {
        return kind.equals("SRT") || kind.equals("VTT");
    }

    List<TranscriptParser.Segment> transcript(String kind, String content) {
        try {
            return TranscriptParser.parse(kind, text(content));
        } catch (IllegalArgumentException exception) {
            throw new SourceException(SOURCE_TRANSCRIPT_INVALID, exception.getMessage(), exception);
        }
    }

    String text(String value) {
        String content = value == null ? "" : value.strip();
        if (content.isEmpty()) {
            throw invalid("没有可读取的正文。扫描版 PDF 需要先进行 OCR。");
        }
        if (content.length() > MAX_CONTENT || content.indexOf(0) >= 0) {
            throw invalid("正文须为有效文本，长度不能超过 50 万字符。");
        }
        return content;
    }

    private String utf8(byte[] bytes) {
        try {
            String value = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            return value.startsWith("\ufeff") ? value.substring(1) : value;
        } catch (CharacterCodingException exception) {
            throw invalid("无法读取文本编码，请将文件保存为 UTF-8 后重试。");
        }
    }

    /** 一次解析同时保留聚合正文和带原始页码的文本；空白页不作为证据。 */
    private Parsed pdf(byte[] bytes) {
        try (var document = Loader.loadPDF(bytes)) {
            if (!document.getCurrentAccessPermission().canExtractContent()) {
                throw invalid("该 PDF 禁止提取正文。");
            }
            if (document.getNumberOfPages() > MAX_PDF_PAGES) {
                throw invalid("PDF 不能超过 500 页。");
            }
            var pageOutput = new StringWriter();
            var pages = new ArrayList<PdfPage>();
            var stripper = new PDFTextStripper() {
                private int pageContentLength;

                @Override
                protected void writePage() throws IOException {
                    int start = pageOutput.getBuffer().length();
                    super.writePage();
                    String page = pageOutput.getBuffer().substring(start).strip();
                    pageContentLength += page.length();
                    if (pageContentLength > MAX_CONTENT) {
                        throw invalid("正文须为有效文本，长度不能超过 50 万字符。");
                    }
                    if (!page.isEmpty()) pages.add(new PdfPage(getCurrentPageNo(), page));
                }
            };
            stripper.setSortByPosition(true);
            stripper.writeText(document, pageOutput);
            return new Parsed("PDF", text(pageOutput.toString()), List.copyOf(pages));
        } catch (InvalidPasswordException exception) {
            throw invalid("该 PDF 受密码保护，请上传可读取的版本。");
        } catch (IOException exception) {
            throw invalid("无法解析 PDF，请检查文件是否完整。");
        }
    }

    private SourceException invalid(String message) {
        return new SourceException(SOURCE_CONTENT_INVALID, message);
    }
}
