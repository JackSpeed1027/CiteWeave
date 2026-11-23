package dev.citeweave.source;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.citeweave.source.TranscriptSummaryEngine.Evidence;
import dev.citeweave.source.TranscriptSummaryEngine.ResearchDraft;
import dev.citeweave.source.TranscriptSummaryEngine.ResearchReply;
import dev.citeweave.source.TranscriptSummaryEngine.ResearchStep;
import java.util.List;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 从 HTTP 边界验证范围预检、轨迹和最终引用复核。 */
class ResearchControllerTest {
    private static final String QUESTION = "Cache rebuild";
    private static final List<Long> SCOPE = List.of(7L, 8L);
    private static final String VERSION = "a".repeat(64);
    private static final String CUE = "7:" + VERSION + ":2";
    private static final String BODY = "{\"question\":\"Cache rebuild\",\"sourceIds\":[7,8]}";
    private final SourceRepository sources = mock(SourceRepository.class);
    private final DocumentEvidenceReader documents = mock(DocumentEvidenceReader.class);
    private final TranscriptSummaryEngine engine = mock(TranscriptSummaryEngine.class);
    private final ResearchHistoryRepository history = mock(ResearchHistoryRepository.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new ResearchController(new ResearchHistoryService(history, sources,
                    new ResearchService(sources, documents, engine))))
            .setControllerAdvice(new SourceErrorHandler()).build();

    @Test
    void payloadUsesCanonicalOriginalAfterACompletedRead() throws Exception {
        var evidence = new Evidence(CUE, "7", VERSION, "paragraph", null, null, "原始段落", 2, null);
        var trace = List.of(search(CUE), new ResearchStep("call_read", "read_evidence", List.of(CUE)));
        given(new ResearchReply(200, new ResearchDraft("ANSWERED", "夹具回答", List.of(CUE), trace, null)));
        when(documents.readEvidence(7L, CUE)).thenReturn(evidence);
        var snapshot = new ResearchHistoryRepository.Snapshot("ANSWERED", "夹具回答", List.of(CUE), trace);
        var record = new ResearchHistoryRepository.Header(UUID.randomUUID(), QUESTION, OffsetDateTime.now());
        when(history.insert("local-personal", SCOPE, QUESTION, snapshot)).thenReturn(record);
        mvc.perform(post("/api/research").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.record.id").value(record.id().toString()))
                .andExpect(jsonPath("$.trace[1].tool").value("read_evidence"))
                .andExpect(jsonPath("$.citations[0].paragraph").value(2))
                .andExpect(jsonPath("$.citations[0].text").value("原始段落"));
        verify(documents).readEvidence(7L, CUE);
    }

    @Test
    void outOfScopeErrorExposesOnlyCompletedSteps() throws Exception {
        given(new ResearchReply(502, new ResearchDraft(null, null, null, List.of(search(CUE)),
                "RESEARCH_EVIDENCE_OUT_OF_SCOPE")));
        mvc.perform(post("/api/research").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("RESEARCH_EVIDENCE_OUT_OF_SCOPE"))
                .andExpect(jsonPath("$.trace.length()").value(1));
        verifyNoInteractions(documents, history);
    }

    @Test
    void refusesCitationThatOnlyAppearedInSearch() throws Exception {
        given(new ResearchReply(200, new ResearchDraft("ANSWERED", "夹具回答", List.of(CUE),
                List.of(search(CUE)), null)));
        mvc.perform(post("/api/research").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("RESEARCH_CITATION_INVALID"));
        verifyNoInteractions(documents);
    }

    @Test
    void refusesTraceWithForeignSource() throws Exception {
        given(new ResearchReply(502, new ResearchDraft(null, null, null,
                List.of(search("9:" + VERSION + ":2")), "RESEARCH_EVIDENCE_OUT_OF_SCOPE")));
        mvc.perform(post("/api/research").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("RESEARCH_INVALID_RESPONSE"))
                .andExpect(jsonPath("$.trace").isEmpty());
        verifyNoInteractions(documents);
    }

    @Test
    void invalidOrUnavailableSelectionsDoNotCallPython() throws Exception {
        mvc.perform(post("/api/research").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Cache rebuild\",\"sourceIds\":[7,7]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESEARCH_REQUEST_INVALID"));
        mvc.perform(post("/api/research").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESEARCH_SOURCE_UNAVAILABLE"));
        verifyNoInteractions(engine, documents);
    }

    private void given(ResearchReply reply) {
        when(sources.allQuestionSourcesAvailable(SCOPE)).thenReturn(true);
        when(engine.research(QUESTION, SCOPE)).thenReturn(reply);
    }

    private static ResearchStep search(String cue) {
        return new ResearchStep("call_search", "search_evidence", List.of(cue));
    }
}
