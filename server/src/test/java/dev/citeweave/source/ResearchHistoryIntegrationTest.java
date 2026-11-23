package dev.citeweave.source;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.citeweave.source.TranscriptSummaryEngine.CheckpointTarget;
import dev.citeweave.source.TranscriptSummaryEngine.ResearchDraft;
import dev.citeweave.source.TranscriptSummaryEngine.ResearchReply;
import dev.citeweave.source.TranscriptSummaryEngine.ResearchStep;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 在隔离 PostgreSQL 验证快照、资料范围和不调用模型的读回。 */
@EnabledIfEnvironmentVariable(named = "CITEWEAVE_ISOLATED_PG_TEST", matches = "1")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:15434/citeweave_summary_qa",
        "spring.datasource.username=citeweave_summary_qa",
        "spring.datasource.password=citeweave_summary_qa_local"
})
class ResearchHistoryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SourceRepository sources;
    @Autowired DocumentEvidenceReader documents;
    @Autowired ResearchHistoryRepository history;
    @Autowired JdbcClient jdbc;
    @MockitoBean TranscriptSummaryEngine engine;

    @Test
    void editsAndExportsResearchNoteWithoutReplacingItsCitations() throws Exception {
        long source = sources.insert("研究笔记夹具", "TEXT", null, "Cache rebuild follows expiry.").source().id();
        var evidence = documents.read(source).getFirst();
        var ids = List.of(evidence.evidenceId());
        var trace = List.of(new ResearchStep("search", "search_evidence", ids), new ResearchStep("read", "read_evidence", ids));
        String question = "研究笔记 " + UUID.randomUUID();
        when(engine.research(question, List.of(source))).thenReturn(new ResearchReply(200,
                new ResearchDraft("ANSWERED", "夹具答案", ids, trace, null)));
        var completed = mvc.perform(post("/api/research").contentType(MediaType.APPLICATION_JSON)
                        .content(body(source, question))).andExpect(status().isOk()).andReturn().getResponse();
        String researchId = mapper.readTree(completed.getContentAsString()).at("/record/id").asText();
        clearInvocations(engine);

        var created = mvc.perform(post("/api/research/" + researchId + "/note")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.note.revision").value(1)).andExpect(jsonPath("$.markdown", containsString("夹具答案")))
                .andReturn().getResponse();
        String id = mapper.readTree(created.getContentAsString()).at("/note/id").asText();
        String notePath = "/api/research-notes/" + id;
        String edit = mapper.writeValueAsString(new ResearchNoteService.Edit("改过的标题", "## 我的结论\n\n编辑后的正文", 1L));
        mvc.perform(put(notePath).contentType(MediaType.APPLICATION_JSON).content(edit))
                .andExpect(status().isOk()).andExpect(jsonPath("$.note.revision").value(2))
                .andExpect(jsonPath("$.citations[0].evidenceId").value(evidence.evidenceId()));
        mvc.perform(put(notePath).contentType(MediaType.APPLICATION_JSON).content(edit))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESEARCH_NOTE_REVISION_CONFLICT"));
        mvc.perform(post("/api/research/" + researchId + "/note")).andExpect(status().isOk())
                .andExpect(jsonPath("$.note.id").value(id)).andExpect(jsonPath("$.note.revision").value(2));
        mvc.perform(get(notePath)).andExpect(status().isOk())
                .andExpect(jsonPath("$.note.title").value("改过的标题"));
        mvc.perform(get(notePath + "/export/markdown")).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(id)))
                .andExpect(content().string(containsString("编辑后的正文")))
                .andExpect(content().string(containsString("第 1 段")))
                .andExpect(content().string(containsString(evidence.evidenceId())));
        verifyNoInteractions(engine);
    }

    @Test
    void savedResearchCanBeReopenedWithoutModelAndIsScopedToWorkspace() throws Exception {
        long source = sources.insert("研究历史文档夹具", "TEXT", null, "Original fixture only appears in source.").source().id();
        var evidence = documents.read(source).getFirst();
        var ids = List.of(evidence.evidenceId());
        var trace = List.of(new ResearchStep("call_search", "search_evidence", ids),
                new ResearchStep("call_read", "read_evidence", ids));
        String question = "读回历史 " + UUID.randomUUID();
        when(engine.research(question, List.of(source))).thenReturn(new ResearchReply(200,
                new ResearchDraft("ANSWERED", "独立夹具答案", ids, trace, null)));
        String submitted = mapper.writeValueAsString(new TranscriptSummaryEngine.ResearchRequest(question, List.of(source)));
        var posted = mvc.perform(post("/api/research").contentType(MediaType.APPLICATION_JSON).content(submitted))
                .andExpect(status().isOk()).andExpect(jsonPath("$.citations[0].text").value(evidence.text()))
                .andReturn().getResponse();
        UUID id = UUID.fromString(mapper.readTree(posted.getContentAsString()).at("/record/id").asText());
        clearInvocations(engine);
        String stored = jdbc.sql("SELECT snapshot::text FROM research_history WHERE id = :id")
                .param("id", id).query(String.class).single();
        assertFalse(stored.contains(evidence.text()));

        mvc.perform(get("/api/research")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()));
        mvc.perform(get("/api/research/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.record.question").value(question))
                .andExpect(jsonPath("$.sourceIds[0]").value(source))
                .andExpect(jsonPath("$.trace.length()").value(2))
                .andExpect(jsonPath("$.citations[0].text").value(evidence.text()));
        var foreign = history.insert("isolated-foreign", List.of(source), question + " foreign",
                new ResearchHistoryRepository.Snapshot("INSUFFICIENT_EVIDENCE", null, List.of(), List.of()));
        mvc.perform(get("/api/research/" + foreign.id())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESEARCH_RECORD_NOT_FOUND"));
        verifyNoInteractions(engine);
    }

    @Test
    void concurrentRetryReplaysOneRecordAndRejectsChangedPayload() throws Exception {
        long source = sources.insert("幂等研究夹具", "TEXT", null, "Cache rebuild.").source().id();
        UUID key = UUID.randomUUID();
        String question = "同一请求 " + key;
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(engine.research(eq(question), eq(List.of(source)), any(CheckpointTarget.class))).thenAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return new ResearchReply(200, new ResearchDraft(
                    "INSUFFICIENT_EVIDENCE", null, List.of(), List.of(), null));
        });
        String input = body(source, question);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> mvc.perform(post("/api/research").header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON).content(input)).andReturn().getResponse());
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                mvc.perform(post("/api/research").header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON).content(input))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("RESEARCH_REQUEST_IN_PROGRESS"));
            } finally {
                release.countDown();
            }
            var completed = first.get(10, TimeUnit.SECONDS);
            assertEquals(200, completed.getStatus());
            String id = mapper.readTree(completed.getContentAsString()).at("/record/id").asText();
            mvc.perform(post("/api/research").header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON).content(body(source, " " + question + " ")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.record.id").value(id));
            mvc.perform(post("/api/research").header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON).content(body(source, question + " 修改")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESEARCH_REQUEST_KEY_CONFLICT"));
        }
        verify(engine, times(1)).research(eq(question), eq(List.of(source)), any(CheckpointTarget.class));
        assertEquals(1, jdbc.sql("SELECT count(*) FROM research_history WHERE question = :question")
                .param("question", question).query(Integer.class).single());
    }

    @Test
    void knownFailureLeavesNoHistoryAndInvalidPublishRollsBackSnapshot() throws Exception {
        long source = sources.insert("失败研究夹具", "TEXT", null, "Cache rebuild.").source().id();
        UUID key = UUID.randomUUID();
        String question = "失败请求 " + key;
        String input = body(source, question);
        var unconfirmed = new ResearchStep("not_committed", "search_evidence",
                List.of(documents.read(source).getFirst().evidenceId()));
        when(engine.research(eq(question), eq(List.of(source)), any(CheckpointTarget.class))).thenReturn(new ResearchReply(502,
                new ResearchDraft(null, null, null, List.of(unconfirmed), "RESEARCH_EVIDENCE_OUT_OF_SCOPE")));
        mvc.perform(post("/api/research").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.trace").isEmpty());
        mvc.perform(post("/api/research").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESEARCH_REQUEST_PREVIOUS_FAILED"));
        mvc.perform(post("/api/research").header("Idempotency-Key", "1-1-1-1-1")
                        .contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESEARCH_REQUEST_KEY_INVALID"));
        verify(engine, times(1)).research(eq(question), eq(List.of(source)), any(CheckpointTarget.class));
        assertThrows(IllegalStateException.class, () -> history.complete("local-personal", key, List.of(source), question,
                new ResearchHistoryRepository.Snapshot("INSUFFICIENT_EVIDENCE", null, List.of(), List.of())));
        assertEquals(0, jdbc.sql("SELECT count(*) FROM research_history WHERE question = :question")
                .param("question", question).query(Integer.class).single());
    }

    @Test
    void executorBeyondDeadlineCannotPublishEvenWithoutAConcurrentExaminer() throws Exception {
        long source = sources.insert("过期研究夹具", "TEXT", null, "Cache rebuild.").source().id();
        UUID key = UUID.randomUUID();
        String question = "过期返回 " + key;
        when(engine.research(eq(question), eq(List.of(source)), any(CheckpointTarget.class))).thenAnswer(call -> {
            // 只推进这条合成请求的截止时间，模拟调用返回晚于约定期限。
            assertEquals(1, jdbc.sql("""
                    UPDATE research_requests SET expires_at = clock_timestamp() - interval '1 second'
                    WHERE workspace_key = 'local-personal' AND request_key = :key AND status = 'RUNNING'
                    """).param("key", key).update());
            mvc.perform(get("/api/research/requests/" + key)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UNKNOWN"));
            assertEquals("RUNNING", history.claimed("local-personal", key).orElseThrow().status());
            CheckpointTarget target = call.getArgument(2);
            checkpoint(target, target.token().toString(), List.of(new ResearchStep("late", "search_evidence", List.of())))
                    .andExpect(status().isConflict());
            return new ResearchReply(200, new ResearchDraft(
                    "INSUFFICIENT_EVIDENCE", null, List.of(), List.of(), null));
        });
        expectUnknown(source, question, key);
        expectUnknown(source, question, key);
        verify(engine, times(1)).research(eq(question), eq(List.of(source)), any(CheckpointTarget.class));
        assertUnknownWithoutHistory(key, question);
    }

    @Test
    void ambiguousUpstreamTimeoutPreservesKeyAndRequiresExplicitNewRequest() throws Exception {
        long source = sources.insert("超时研究夹具", "TEXT", null, "Cache rebuild.").source().id();
        UUID key = UUID.randomUUID();
        String question = "结果未知 " + key;
        when(engine.research(eq(question), eq(List.of(source)), any(CheckpointTarget.class))).thenReturn(
                new ResearchReply(504, new ResearchDraft(null, null, null, List.of(), "RESEARCH_TIMEOUT")),
                new ResearchReply(200, new ResearchDraft("INSUFFICIENT_EVIDENCE", null, List.of(), List.of(), null)));
        expectUnknown(source, question, key);
        expectUnknown(source, question, key);
        verify(engine, times(1)).research(eq(question), eq(List.of(source)), any(CheckpointTarget.class));
        assertUnknownWithoutHistory(key, question);
        mvc.perform(post("/api/research").header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(body(source, question)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.record.id").isNotEmpty());
        verify(engine, times(2)).research(eq(question), eq(List.of(source)), any(CheckpointTarget.class));
    }

    @Test
    void checkpointIsOrderedAuthorizedAndSurvivesLostUpstreamResult() throws Exception {
        long source = sources.insert("检查点研究夹具", "TEXT", null, "Cache rebuild.").source().id();
        String evidence = documents.read(source).getFirst().evidenceId();
        var search = new ResearchStep("first", "search_evidence", List.of(evidence));
        var read = new ResearchStep("second", "read_evidence", List.of(evidence));
        UUID key = UUID.randomUUID();
        String question = "检查点 " + key;
        when(engine.research(eq(question), eq(List.of(source)), any(CheckpointTarget.class))).thenAnswer(call -> {
            CheckpointTarget target = call.getArgument(2);
            String token = target.token().toString();
            checkpoint(target, UUID.randomUUID().toString(), List.of(search)).andExpect(status().isNotFound());
            checkpoint(target, token, List.of(search, read)).andExpect(status().isConflict());
            var foreign = new ResearchStep("foreign", "search_evidence", List.of("999999:" + "a".repeat(64) + ":1"));
            checkpoint(target, token, List.of(foreign)).andExpect(status().isBadRequest());
            checkpoint(target, token, List.of(search)).andExpect(status().isNoContent());
            checkpoint(target, token, List.of(search)).andExpect(status().isNoContent());
            var changed = new ResearchHistoryRepository.Action("Other terms", null, null);
            checkpoint(target, token, List.of(search), new ResearchHistoryRepository.Context(1, 2, List.of(changed)))
                    .andExpect(status().isConflict());
            checkpoint(target, token, List.of(search), new ResearchHistoryRepository.Context(1, 3, List.of(changed)))
                    .andExpect(status().isBadRequest());
            return new ResearchReply(504, new ResearchDraft(null, null, null, List.of(), "RESEARCH_TIMEOUT"));
        });
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/research").header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON).content(body(source, question)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("RESEARCH_REQUEST_OUTCOME_UNKNOWN"))
                    .andExpect(jsonPath("$.trace.length()").value(1))
                    .andExpect(jsonPath("$.trace[0].callId").value("first"));
        }
        assertUnknownWithoutHistory(key, question);
        var context = history.claimed("local-personal", key).orElseThrow().context();
        assertEquals(2, context.remainingToolSteps());
        assertEquals("Cache rebuild", context.actions().getFirst().query());
        verify(engine, times(1)).research(eq(question), eq(List.of(source)), any(CheckpointTarget.class));
        clearInvocations(engine);
        mvc.perform(get("/api/research/requests/" + key)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.question").value(question))
                .andExpect(jsonPath("$.sourceIds[0]").value(source))
                .andExpect(jsonPath("$.canResume").value(true))
                .andExpect(jsonPath("$.trace[0].callId").value("first"))
                .andExpect(jsonPath("$.tokenDigest").doesNotExist())
                .andExpect(jsonPath("$.context").doesNotExist());
        mvc.perform(get("/api/research/requests/" + UUID.randomUUID())).andExpect(status().isNotFound());
        verifyNoInteractions(engine);

        when(engine.resume(eq(question), eq(List.of(source)), any(CheckpointTarget.class),
                eq(new TranscriptSummaryEngine.Resume(List.of(search), context)))).thenReturn(new ResearchReply(200,
                new ResearchDraft("INSUFFICIENT_EVIDENCE", null, List.of(), List.of(search), null)));
        UUID next = UUID.randomUUID();
        resumed(source, question + " changed", next, key).andExpect(status().isConflict());
        var response = resumed(source, question, next, key).andExpect(status().isOk()).andReturn().getResponse();
        String record = mapper.readTree(response.getContentAsString()).at("/record/id").asText();
        resumed(source, question, next, key).andExpect(status().isOk()).andExpect(jsonPath("$.record.id").value(record));
        resumed(source, question, UUID.randomUUID(), key).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESEARCH_RESUME_UNAVAILABLE"));
        mvc.perform(get("/api/research/requests/" + key)).andExpect(status().isOk())
                .andExpect(jsonPath("$.nextRequestKey").value(next.toString()))
                .andExpect(jsonPath("$.canResume").value(false));
        mvc.perform(get("/api/research/requests/" + next)).andExpect(status().isOk())
                .andExpect(jsonPath("$.recordId").value(record))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        verify(engine, times(1)).resume(eq(question), eq(List.of(source)), any(CheckpointTarget.class), any());
    }

    private org.springframework.test.web.servlet.ResultActions resumed(long source, String question, UUID key, UUID parent)
            throws Exception {
        return mvc.perform(post("/api/research").header("Idempotency-Key", key).header("Research-Resume-From", parent)
                .contentType(MediaType.APPLICATION_JSON).content(body(source, question)));
    }

    private org.springframework.test.web.servlet.ResultActions checkpoint(
            CheckpointTarget target, String token, List<ResearchStep> trace) throws Exception {
        var actions = trace.stream().map(step -> "search_evidence".equals(step.tool())
                ? new ResearchHistoryRepository.Action("Cache rebuild", null, null)
                : new ResearchHistoryRepository.Action(null, step.evidenceIds().getFirst().split(":")[0],
                        step.evidenceIds().getFirst())).toList();
        return checkpoint(target, token, trace, new ResearchHistoryRepository.Context(1, 3 - trace.size(), actions));
    }

    private org.springframework.test.web.servlet.ResultActions checkpoint(
            CheckpointTarget target, String token, List<ResearchStep> trace,
            ResearchHistoryRepository.Context context) throws Exception {
        return mvc.perform(post("/internal/v1/research/" + target.requestKey() + "/checkpoints")
                .header("X-CiteWeave-Checkpoint-Token", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new ResearchController.Checkpoint(trace, context))));
    }

    private void expectUnknown(long source, String question, UUID key) throws Exception {
        mvc.perform(post("/api/research").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body(source, question)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESEARCH_REQUEST_OUTCOME_UNKNOWN"));
    }

    private void assertUnknownWithoutHistory(UUID key, String question) {
        assertEquals("UNKNOWN", history.claimed("local-personal", key).orElseThrow().status());
        assertEquals(0, jdbc.sql("SELECT count(*) FROM research_history WHERE question = :question")
                .param("question", question).query(Integer.class).single());
    }

    private String body(long source, String question) throws Exception {
        return mapper.writeValueAsString(new TranscriptSummaryEngine.ResearchRequest(question, List.of(source)));
    }
}
