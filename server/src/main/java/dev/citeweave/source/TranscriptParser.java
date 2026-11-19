package dev.citeweave.source;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class TranscriptParser {
    record Segment(int sequence, long startMs, long endMs, String text) {}

    private static final String SRT_TIME = "[0-9]{2,6}:[0-5][0-9]:[0-5][0-9],[0-9]{3}";
    private static final String VTT_TIME = "(?:[0-9]{2,6}:)?[0-5][0-9]:[0-5][0-9]\\.[0-9]{3}";
    private static final Pattern SRT_TIMING = timing(SRT_TIME);
    private static final Pattern VTT_TIMING = timing(VTT_TIME);
    private static final Pattern VTT_HEADER = Pattern.compile("WEBVTT(?:[ \\t].*)?");
    private static final Pattern VTT_METADATA = Pattern.compile("NOTE(?:[ \\t].*)?|STYLE|REGION");
    private static final Pattern SRT_SEQUENCE = Pattern.compile("[0-9]+");
    private static final int MAX_SEGMENTS = 10_000;

    private TranscriptParser() {}

    static List<Segment> parse(String kind, String content) {
        boolean vtt = kind.equals("VTT");
        Pattern timing = vtt ? VTT_TIMING : SRT_TIMING;
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        String[] blocks = normalized.split("\n[ \t]*+\n", -1);
        int firstCue = vtt ? afterVttHeader(blocks[0]) : 0;
        List<Segment> segments = new ArrayList<>();
        for (int index = firstCue; index < blocks.length; index++) {
            String block = blocks[index].stripLeading();
            if (block.isEmpty()) {
                continue;
            }
            String[] lines = block.split("\n");
            if (vtt && VTT_METADATA.matcher(lines[0]).matches()) {
                continue;
            }
            if (segments.size() == MAX_SEGMENTS) {
                throw new IllegalArgumentException("字幕最多支持 10000 个片段。");
            }
            int sequence = segments.size() + 1;
            segments.add(parseCue(lines, timing, vtt, sequence));
        }
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("字幕至少需要一个有效片段。");
        }
        return List.copyOf(segments);
    }

    private static int afterVttHeader(String header) {
        String firstLine = header.lines().findFirst().orElse("");
        if (!VTT_HEADER.matcher(firstLine).matches() || header.contains("-->")) {
            throw new IllegalArgumentException("VTT 需要 WEBVTT 文件头，并用空行与字幕片段分隔。");
        }
        return 1;
    }

    private static Segment parseCue(String[] lines, Pattern timing, boolean vtt, int sequence) {
        int timingLine = lines[0].contains("-->") ? 0 : 1;
        if (timingLine == 1 && !vtt && !SRT_SEQUENCE.matcher(lines[0].strip()).matches()) {
            throw invalidCue(sequence);
        }
        if (lines.length <= timingLine + 1) {
            throw invalidCue(sequence);
        }
        var times = timing.matcher(lines[timingLine].strip());
        if (!times.matches()) {
            throw invalidCue(sequence);
        }
        long start = milliseconds(times.group(1));
        long end = milliseconds(times.group(2));
        if (end <= start) {
            throw new IllegalArgumentException("第 " + sequence + " 个字幕片段的结束时间必须晚于开始时间。");
        }
        StringBuilder text = new StringBuilder();
        for (int line = timingLine + 1; line < lines.length; line++) {
            if (timing.matcher(lines[line].strip()).matches()) {
                throw invalidCue(sequence);
            }
            if (line > timingLine + 1) {
                text.append('\n');
            }
            text.append(lines[line]);
        }
        if (text.toString().isBlank()) {
            throw invalidCue(sequence);
        }
        return new Segment(sequence, start, end, text.toString());
    }

    private static Pattern timing(String time) {
        return Pattern.compile("(" + time + ")[ \\t]+-->[ \\t]+(" + time + ")(?:[ \\t]+.*)?");
    }

    private static long milliseconds(String time) {
        String[] parts = time.split("[:,.]");
        long totalSeconds = 0;
        for (int index = 0; index < parts.length - 1; index++) {
            totalSeconds = totalSeconds * 60 + Long.parseLong(parts[index]);
        }
        return totalSeconds * 1000 + Long.parseLong(parts[parts.length - 1]);
    }

    private static IllegalArgumentException invalidCue(int sequence) {
        return new IllegalArgumentException("第 " + sequence + " 个字幕片段缺少合法时间轴、正文或分隔空行。");
    }
}
