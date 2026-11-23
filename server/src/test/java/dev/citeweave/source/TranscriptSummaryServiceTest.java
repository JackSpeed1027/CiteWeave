package dev.citeweave.source;

import static org.junit.jupiter.api.Assertions.*;

import dev.citeweave.source.TranscriptSummaryEngine.Draft;
import dev.citeweave.source.TranscriptSummaryEngine.Evidence;
import dev.citeweave.source.TranscriptSummaryEngine.Failure;
import dev.citeweave.source.TranscriptSummaryEngine.SummaryException;
import java.util.List;
import org.junit.jupiter.api.Test;

class TranscriptSummaryServiceTest {
    private final Evidence first = new Evidence("s:v:1", "s", "v", "transcript", 100, 900, "原句");
    private final Evidence second = new Evidence("s:v:2", "s", "v", "transcript", 900, 1500, "裁剪片段");

    @Test
    void resolvesCanonicalTextAndRange() {
        var result = TranscriptSummaryService.verify(List.of(first, second),
                new Draft("摘要", List.of("s:v:1"), true, List.of("s:v:1")));
        assertEquals(List.of(first), result.citations());
        assertTrue(result.truncated());
    }

    @Test
    void rejectsEvidenceThatWasAuthorizedButNotSent() {
        var error = assertThrows(SummaryException.class, () -> TranscriptSummaryService.verify(List.of(first, second),
                new Draft("摘要", List.of("s:v:2"), true, List.of("s:v:1"))));
        assertEquals(Failure.INVALID_CITATION, error.failure);
    }
}
