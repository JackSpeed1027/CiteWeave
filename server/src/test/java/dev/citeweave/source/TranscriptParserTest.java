package dev.citeweave.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TranscriptParserTest {
    @Test
    void parsesSrtWithCrLfMultilineCaptionsAndOverlap() {
        String source = "12\r\n01:02:03,004 --> 01:02:05,000\r\n第一行\r\n第二行\r\n\r\n"
                + "15\r\n01:02:04,000 --> 01:02:06,000\r\n另一段";
        assertThat(TranscriptParser.parse("SRT", source)).containsExactly(
                new TranscriptParser.Segment(1, 3_723_004, 3_725_000, "第一行\n第二行"),
                new TranscriptParser.Segment(2, 3_724_000, 3_726_000, "另一段"));
    }

    @Test
    void parsesVttHeaderNoteIdentifierAndCueSettings() {
        var segments = TranscriptParser.parse("VTT", """
                WEBVTT 课程字幕

                NOTE 这一块不会进入时间轴
                第二行注释

                chapter-a
                00:01.010 --> 00:02.003 align:start
                第一段

                00:00:03.100 --> 00:00:04.000
                第二段
                """);
        assertThat(segments).containsExactly(
                new TranscriptParser.Segment(1, 1010, 2003, "第一段"),
                new TranscriptParser.Segment(2, 3100, 4000, "第二段"));
    }

    @Test
    void acceptsLongRunsOfBlankLinesBetweenCues() {
        String cue = "00:00:01,000 --> 00:00:03,000\n字幕";
        assertThat(TranscriptParser.parse("SRT", cue + "\n".repeat(20_001) + cue)).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "00:00:05,000 --> 00:00:01,000\n坏片段",
            "00:00:05,000 --> 00:00:05,000\n坏片段",
            "00:60:00,000 --> 01:01:00,000\n坏片段",
            "00:00:00.000 --> 00:00:01.000\n错误分隔符",
            "00:00:00,000 --> 00:00:01,000",
            "00:00:00,000 --> 00:00:01,000\n字幕\n2\n00:00:01,000 --> 00:00:02,000\n字幕"
    })
    void rejectsInvalidSrt(String source) {
        assertThatIllegalArgumentException().isThrownBy(() -> TranscriptParser.parse("SRT", source));
    }

    @ParameterizedTest
    @ValueSource(strings = {"NOT-VTT\n\n00:00.000 --> 00:01.000\n坏文件", "WEBVTT\n\nNOTE 只有注释"})
    void rejectsVttWithoutHeaderOrCues(String source) {
        assertThatIllegalArgumentException().isThrownBy(() -> TranscriptParser.parse("VTT", source));
    }
}
