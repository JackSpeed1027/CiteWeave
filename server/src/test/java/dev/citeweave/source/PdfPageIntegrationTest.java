package dev.citeweave.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 用真实合成 PDF 与独立 PostgreSQL 验证空白页和原始页码。 */
@EnabledIfEnvironmentVariable(named = "CITEWEAVE_ISOLATED_PG_TEST", matches = "1")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:15434/citeweave_summary_qa",
        "spring.datasource.username=citeweave_summary_qa",
        "spring.datasource.password=citeweave_summary_qa_local"
})
class PdfPageIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SourceRepository repository;
    @MockitoBean TranscriptSummaryEngine engine;

    @Test
    void retainsPageThreeAfterBlankPageAndKeepsLegacyReadable() throws Exception {
        var legacy = repository.insert("历史 PDF 验收样例", "PDF", "legacy.pdf", "Historical extracted text.");
        mvc.perform(get("/api/sources/" + legacy.source().id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Historical extracted text."));
        mvc.perform(get("/api/sources/" + legacy.source().id() + "/pdf-pages"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SOURCE_PDF_PAGES_UNAVAILABLE"));

        byte[] pdf = sample();
        Files.write(Path.of("target", "pdf-page-fixture.pdf"), pdf);
        var file = new MockMultipartFile("file", "pages.pdf", "application/pdf", pdf);
        var uploaded = mvc.perform(multipart("/api/sources").file(file))
                .andExpect(status().isCreated()).andReturn().getResponse();
        long id = mapper.readTree(uploaded.getContentAsString()).at("/source/id").asLong();
        String url = "/api/sources/" + id;
        mvc.perform(get(url + "/pdf-pages"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].page").value(1)).andExpect(jsonPath("$[1].page").value(3))
                .andExpect(jsonPath("$[1].text").value("Cache rebuild follows expiry."))
                .andExpect(jsonPath("$[1].startMs").doesNotExist());

        when(engine.answer(org.mockito.ArgumentMatchers.eq("Cache rebuild"), anyList())).thenAnswer(invocation -> {
            List<TranscriptSummaryEngine.Evidence> evidence = invocation.getArgument(1);
            assertEquals(1, evidence.size());
            assertEquals(3, evidence.getFirst().page());
            var ids = List.of(evidence.getFirst().evidenceId());
            return new TranscriptSummaryEngine.QuestionDraft("ANSWERED", "After expiry", ids, false, ids);
        });
        mvc.perform(post(url + "/document-questions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Cache rebuild\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.citations[0].locationType").value("page"))
                .andExpect(jsonPath("$.citations[0].page").value(3))
                .andExpect(jsonPath("$.citations[0].text").value("Cache rebuild follows expiry."));
    }

    @Test
    void mixedQuestionKeepsThreeCoordinatesAndChecksAllSourcesBeforeCallingEngine() throws Exception {
        var legacy = repository.insert("历史 PDF 验收样例", "PDF", "legacy.pdf", "Cache rebuild.");
        var subtitle = repository.insert("混选字幕夹具", "SRT", "mixed.srt",
                "1\n00:00:01,000 --> 00:00:02,000\nCache rebuild follows eviction.\n");
        var markdown = repository.insert("混选文档夹具", "MARKDOWN", "mixed.md",
                "# Scope\n\nCache rebuild follows a version update.");
        var uploaded = mvc.perform(multipart("/api/sources").file(
                        new MockMultipartFile("file", "mixed.pdf", "application/pdf", sample())))
                .andExpect(status().isCreated()).andReturn().getResponse();
        long pdfId = mapper.readTree(uploaded.getContentAsString()).at("/source/id").asLong();
        long legacyId = legacy.source().id();
        long subtitleId = subtitle.source().id();
        long markdownId = markdown.source().id();
        mvc.perform(get("/api/source-questions/sources")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + pdfId + ")]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.id == " + legacyId + ")]").isEmpty());

        mvc.perform(post("/api/source-questions").contentType(MediaType.APPLICATION_JSON)
                        .content(question(subtitleId + "," + legacyId, "Cache rebuild")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SOURCE_PDF_PAGES_UNAVAILABLE"));
        org.mockito.Mockito.verifyNoInteractions(engine);

        when(engine.answer(org.mockito.ArgumentMatchers.anyString(), anyList())).thenAnswer(invocation -> {
            List<TranscriptSummaryEngine.Evidence> evidence = invocation.getArgument(1);
            assertEquals(List.of("transcript", "paragraph", "page"),
                    evidence.stream().map(TranscriptSummaryEngine.Evidence::locationType).toList());
            var ids = evidence.stream().map(TranscriptSummaryEngine.Evidence::evidenceId).toList();
            var cited = invocation.getArgument(0, String.class).endsWith("wrong") ? List.of("unselected:version:1") : ids;
            return new TranscriptSummaryEngine.QuestionDraft("ANSWERED", "Fixture only", cited, false, ids);
        });
        String selected = subtitleId + "," + markdownId + "," + pdfId;
        mvc.perform(post("/api/source-questions").contentType(MediaType.APPLICATION_JSON)
                        .content(question(selected, "Cache rebuild")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citations[0].startMs").value(1000))
                .andExpect(jsonPath("$.citations[0].sourceId").value(Long.toString(subtitleId)))
                .andExpect(jsonPath("$.citations[1].paragraph").value(2))
                .andExpect(jsonPath("$.citations[1].sourceId").value(Long.toString(markdownId)))
                .andExpect(jsonPath("$.citations[2].page").value(3))
                .andExpect(jsonPath("$.citations[2].sourceId").value(Long.toString(pdfId)));
        mvc.perform(post("/api/source-questions").contentType(MediaType.APPLICATION_JSON)
                        .content(question(selected, "Cache rebuild wrong")))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("QUESTION_INVALID_CITATION"));
    }

    private static String question(String ids, String text) {
        return "{\"sourceIds\":[" + ids + "],\"question\":\"" + text + "\"}";
    }

    @Test
    void searchesOnlySelectedSourcesAndReadsBackSameVersionWithoutModel() throws Exception {
        long srt = repository.insert("证据范围字幕", "SRT", "scope.srt",
                "1\n00:00:01,000 --> 00:00:02,400\nCache rebuild follows eviction.\n").source().id();
        long markdown = repository.insert("证据范围文档", "MARKDOWN", "scope.md",
                "# Scope\n\nCache rebuild follows a version update.").source().id();
        long unrelated = repository.insert("证据范围干扰文档", "TEXT", null,
                "Cache rebuild follows expiry.").source().id();
        final String search = "/api/source-evidence/search";
        var response = mvc.perform(post(search).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceIds\":[" + srt + "," + markdown + "],\"query\":\"Cache rebuild\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].startMs").value(1000))
                .andExpect(jsonPath("$[1].paragraph").value(2))
                .andExpect(jsonPath("$[1].sourceId").value(Long.toString(markdown)))
                .andExpect(jsonPath("$[?(@.sourceId == '" + unrelated + "')]").isEmpty())
                .andReturn().getResponse();
        String link = "/api/sources/" + markdown + "/evidence";
        var document = mapper.readTree(response.getContentAsString()).get(1);
        String evidenceId = document.path("evidenceId").asText();
        mvc.perform(get(link).param("evidenceId", evidenceId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceVersionId").value(document.path("sourceVersionId").asText()))
                .andExpect(jsonPath("$.text").value(document.path("text").asText()));
        mvc.perform(get(link).param("evidenceId", markdown + ":obsolete:2")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SOURCE_EVIDENCE_NOT_FOUND"));
        mvc.perform(get("/api/sources/" + srt + "/evidence").param("evidenceId", evidenceId))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SOURCE_EVIDENCE_NOT_FOUND"));
        mvc.perform(post(search).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceIds\":[" + srt + "],\"query\":\"ocean reef coral\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(post(search).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceIds\":[" + srt + "," + srt + "],\"query\":\"Cache rebuild\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EVIDENCE_SOURCE_IDS_INVALID"));
        mvc.perform(post(search).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceIds\":[" + srt + "],\"query\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EVIDENCE_QUERY_INVALID"));
        org.mockito.Mockito.verifyNoInteractions(engine);
    }

    private static byte[] sample() throws IOException {
        var output = new ByteArrayOutputStream();
        try (var document = new PDDocument()) {
            addTextPage(document, "Database snapshot stays readable.");
            document.addPage(new PDPage());
            addTextPage(document, "Cache rebuild follows expiry.");
            document.save(output);
        }
        return output.toByteArray();
    }

    private static void addTextPage(PDDocument document, String text) throws IOException {
        var page = new PDPage();
        document.addPage(page);
        try (var stream = new PDPageContentStream(document, page)) {
            stream.beginText();
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            stream.newLineAtOffset(72, 720);
            stream.showText(text);
            stream.endText();
        }
    }
}
