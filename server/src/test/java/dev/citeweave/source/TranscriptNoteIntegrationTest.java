package dev.citeweave.source;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 仅在专用 PostgreSQL 复验并发提交与证据快照。 */
@EnabledIfEnvironmentVariable(named = "CITEWEAVE_ISOLATED_PG_TEST", matches = "1")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:15434/citeweave_summary_qa",
        "spring.datasource.username=citeweave_summary_qa",
        "spring.datasource.password=citeweave_summary_qa_local"
})
class TranscriptNoteIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean TranscriptSummaryEngine engine;

    @Test
    void copiesCitationsAndReusesOneNoteAcrossConcurrentSaves() throws Exception {
        when(engine.generate(anyList())).thenAnswer(invocation -> {
            List<TranscriptSummaryEngine.Evidence> cues = invocation.getArgument(0);
            String evidence = cues.getFirst().evidenceId();
            return new TranscriptSummaryEngine.Draft("笔记验收夹具摘要", List.of(evidence), false, List.of(evidence));
        });
        var file = new MockMultipartFile("file", "note-fixture.srt", "text/plain",
                "1\n00:00:01,000 --> 00:00:02,400\n笔记原句\n".getBytes(StandardCharsets.UTF_8));
        var source = mvc.perform(multipart("/api/sources").file(file))
                .andExpect(status().isCreated()).andReturn().getResponse();
        long sourceId = mapper.readTree(source.getContentAsString()).at("/source/id").asLong();
        String summary = "/api/sources/" + sourceId + "/summary";
        mvc.perform(post(summary + "/note")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTE_SUMMARY_REQUIRED"));
        var published = mvc.perform(post(summary)).andExpect(status().isOk()).andReturn().getResponse();

        List<MockHttpServletResponse> responses = new ArrayList<>();
        try (var workers = Executors.newFixedThreadPool(8)) {
            List<Future<MockHttpServletResponse>> requests = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                requests.add(workers.submit(() -> mvc.perform(post(summary + "/note")).andReturn().getResponse()));
            }
            for (var request : requests) responses.add(request.get());
        }

        assertEquals(1, responses.stream().filter(response -> response.getStatus() == 201).count());
        var first = mapper.readTree(responses.getFirst().getContentAsString());
        String id = first.at("/note/id").asText();
        assertDoesNotThrow(() -> UUID.fromString(id));
        for (var response : responses) {
            assertTrue(response.getStatus() == 200 || response.getStatus() == 201);
            assertEquals(first, mapper.readTree(response.getContentAsString()));
        }
        assertEquals(mapper.readTree(published.getContentAsString()), first.get("snapshot"));
        var created = responses.stream().filter(response -> response.getStatus() == 201).findFirst().orElseThrow();
        assertEquals("/api/notes/" + id, created.getHeader("Location"));
        mvc.perform(get("/api/notes/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.citations[0].startMs").value(1000));
        String path = "/api/notes/" + id + "/export/markdown";
        var markdown = mvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(content().contentType("text/markdown;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"citeweave-note-" + id + ".md\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(markdown.contains("笔记验收夹具摘要"));
        assertTrue(markdown.contains("1000–2400 ms"));
        assertTrue(markdown.contains("笔记原句"));
        mvc.perform(get("/api/notes/" + UUID.randomUUID() + "/export/markdown")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTE_NOT_FOUND"));
        mvc.perform(get("/api/notes")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')]").isNotEmpty());
        mvc.perform(get("/api/notes/" + UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTE_NOT_FOUND"));
    }
}
