package dev.citeweave.source;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SourceServiceTest {
    private final SourceRepository repository = mock(SourceRepository.class);
    private final SourceParser parser = new SourceParser();
    private final SourceService service = new SourceService(repository, parser);

    @Test
    void preservesSubtitleTimestampsAndRemovesBom() {
        String cue = "WEBVTT\n\n00:00:01.000 --> 00:00:03.000\n引用保留了上下文。";
        service.upload(null, new MockMultipartFile("file", "talk.VTT", "text/vtt",
                ("\ufeff" + cue).getBytes(StandardCharsets.UTF_8)));
        verify(repository).insert("talk", "VTT", "talk.VTT", cue);
    }

    @Test
    void extractsRealPdfText() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var document = new PDDocument()) {
            var page = new PDPage();
            document.addPage(page);
            try (var stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                stream.showText("Traceable source");
                stream.endText();
            }
            document.save(output);
        }
        assertThat(parser.file("source.pdf", output.toByteArray()).content()).isEqualTo("Traceable source");
    }

    @Test
    void rejectsInvalidEncodingWithoutInserting() {
        var upload = new MockMultipartFile("file", "broken.txt", "text/plain", new byte[]{(byte) 0xff});
        assertThatThrownBy(() -> service.upload(null, upload))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        verifyNoInteractions(repository);
    }

    @Test
    void keepsPastedParagraphsAndRejectsMissingContent() {
        service.paste("  Paper  ", " first\n\nsecond ");
        verify(repository).insert("Paper", "TEXT", null, "first\n\nsecond");
        assertThatThrownBy(() -> service.paste("Empty", "  ")).isInstanceOf(ResponseStatusException.class);
        verifyNoMoreInteractions(repository);
    }
}
