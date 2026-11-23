package dev.citeweave.source;

/** 将已保存的笔记快照转成独立、只读的 Markdown。 */
final class TranscriptNoteMarkdown {
    private TranscriptNoteMarkdown() {}

    /** 保留完整引用定位，正文和原句按字面文本导出。 */
    static String render(TranscriptNoteRepository.Note note) {
        var value = new StringBuilder("# 字幕笔记\n\n## 标题\n\n");
        appendLiteral(value, note.note().title());
        value.append("笔记 ID：").append(note.note().id())
                .append("\n\n资料 ID：").append(note.note().sourceId())
                .append("\n\n保存时间：").append(note.note().createdAt())
                .append("\n\n## 摘要\n\n");
        if (note.snapshot().truncated()) value.append("摘要基于预算内的部分字幕。\n\n");
        appendLiteral(value, note.snapshot().summary());

        int index = 1;
        for (var citation : note.snapshot().citations()) {
            value.append("## 引用 ").append(index++).append(" · ")
                    .append(citation.startMs()).append("–").append(citation.endMs()).append(" ms\n\n");
            value.append("资料版本：").append(citation.sourceVersionId())
                    .append("\n\n证据 ID：").append(citation.evidenceId()).append("\n\n");
            appendLiteral(value, citation.text());
        }
        return value.toString();
    }

    static void appendLiteral(StringBuilder target, String text) {
        // 给每行加缩进，让模型文本和用户字幕中的 HTML、图片或链接按字面显示。
        text.lines().forEach(line -> target.append("    ").append(line).append('\n'));
        target.append('\n');
    }
}
