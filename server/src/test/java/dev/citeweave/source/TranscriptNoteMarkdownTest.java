package dev.citeweave.source;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.citeweave.source.TranscriptSummaryEngine.Evidence;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TranscriptNoteMarkdownTest {
    @Test
    void exportsUserAndModelMarkupAsLiteralText() {
        var header = new TranscriptNoteRepository.Header(
                UUID.fromString("38ddb4c6-cd53-4dd8-ad13-24430ceb283b"), 8, "中文 <标题>",
                OffsetDateTime.parse("2026-11-23T08:00:00+08:00"));
        var cue = new Evidence("8:version:2", "8", "version", "transcript", 1000, 2400, "<img src=x>\n# 原句");
        var snapshot = new TranscriptSummaryService.Result(
                "![图](https://example.invalid/track)\n\n# 伪造标题", List.of(cue), true);

        String content = TranscriptNoteMarkdown.render(new TranscriptNoteRepository.Note(header, snapshot));

        assertTrue(content.contains("\n    中文 <标题>\n"));
        assertTrue(content.contains("\n    ![图](https://example.invalid/track)\n    \n    # 伪造标题\n"));
        assertTrue(content.contains("\n    <img src=x>\n    # 原句\n"));
        assertTrue(content.contains("## 引用 1 · 1000–2400 ms"));
        assertTrue(content.contains("证据 ID：8:version:2"));
        assertTrue(content.contains("摘要基于预算内的部分字幕。"));
    }
}
