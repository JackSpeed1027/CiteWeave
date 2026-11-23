package dev.citeweave.video.bilibili;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BilibiliInputParserTest {
    private final BilibiliInputParser parser = new BilibiliInputParser();

    @Test
    void acceptsBareIdsWithoutChangingBvPayloadCase() {
        assertThat(parser.parse("  bv1xx411c7mD  ")).isEqualTo(new BilibiliInputParser.Reference(
                BilibiliInputParser.Kind.BV, "BV1xx411c7mD", "https://www.bilibili.com/video/BV1xx411c7mD", null));
        assertThat(parser.parse("AV12345")).isEqualTo(new BilibiliInputParser.Reference(
                BilibiliInputParser.Kind.AV, "av12345", "https://www.bilibili.com/video/av12345", null));
    }

    @Test
    void keepsPartHintAndRebuildsUrlWithoutTracking() {
        var reference = parser.parse("http://m.bilibili.com/video/BV1xx411c7mD/?spm_id_from=abc&p=3#position");

        assertThat(reference.canonicalUrl()).isEqualTo("https://www.bilibili.com/video/BV1xx411c7mD");
        assertThat(reference.requestedPart()).isEqualTo(3);
    }

    @Test
    void identifiesShortLinkWithoutResolvingIt() {
        assertThat(parser.parse("https://b23.tv/Example1?share_source=copy")).isEqualTo(
                new BilibiliInputParser.Reference(BilibiliInputParser.Kind.SHORT, "Example1",
                        "https://b23.tv/Example1", null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ", "bv123", "av0", "12345",
            "https://example.invalid/video/BV1xx411c7mD",
            "https://bilibili.com.example.invalid/video/BV1xx411c7mD",
            "https://demo@b23.tv/Example1", "https://b23.tv:443/Example1",
            "file:///video/BV1xx411c7mD", "https://b23.tv/a/b", "https://b23.tv/%41",
            "https://www.bilibili.com/video/BV1xx411c7mD?p=0",
            "https://www.bilibili.com/video/BV1xx411c7mD?p=1&p=2",
            "https://www.bilibili.com/video/BV1xx411c7mD?p=2147483648"
    })
    void rejectsInvalidInput(String input) {
        assertThatThrownBy(() -> parser.parse(input)).isInstanceOf(BilibiliInputException.class);
    }

    @Test
    void rejectsOversizedInput() {
        assertThatThrownBy(() -> parser.parse("x".repeat(2049))).isInstanceOf(BilibiliInputException.class);
    }
}
