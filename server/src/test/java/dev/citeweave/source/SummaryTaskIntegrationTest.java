package dev.citeweave.source;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.citeweave.source.TranscriptSummaryEngine.Draft;
import dev.citeweave.source.TranscriptSummaryEngine.Evidence;
import dev.citeweave.source.TranscriptSummaryEngine.Failure;
import dev.citeweave.source.TranscriptSummaryEngine.SummaryException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/** 隔离库中真实验证入队、失败重试和过期发布。 */
@EnabledIfEnvironmentVariable(named = "CITEWEAVE_ISOLATED_PG_TEST", matches = "1")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:15434/citeweave_summary_qa",
        "spring.datasource.username=citeweave_summary_qa",
        "spring.datasource.password=citeweave_summary_qa_local",
        "citeweave.tasks.worker-enabled=false"
})
class SummaryTaskIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SourceRepository sources;
    @Autowired SummaryTaskWorker worker;
    @Autowired SummaryTaskRepository tasks;
    @Autowired JdbcClient jdbc;
    @MockitoBean TranscriptSummaryEngine engine;
    @MockitoSpyBean TranscriptSummaryRepository summaries;

    @Test
    void failureCanRetryTheSameTaskAndPublishOneVerifiedSummary() throws Exception {
        long source = source("后台摘要夹具");
        String path = "/api/sources/" + source + "/summary-task";
        UUID id = enqueue(path);
        assertEquals(id, enqueue(path));
        verifyNoInteractions(engine);
        when(engine.generate(anyList())).thenThrow(new SummaryException(Failure.MODEL_RATE_LIMITED))
                .thenAnswer(call -> draft(call.getArgument(0)));
        worker.tick();
        mvc.perform(get("/api/summary-tasks/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.attempt").value(1))
                .andExpect(jsonPath("$.errorCode").value("SUMMARY_MODEL_RATE_LIMITED"));
        mvc.perform(post("/api/summary-tasks/" + id + "/retry")).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(id.toString()));
        worker.tick();
        mvc.perform(get("/api/summary-tasks/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED")).andExpect(jsonPath("$.attempt").value(2));
        mvc.perform(get("/api/sources/" + source + "/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.citations[0].startMs").value(1000));
        mvc.perform(post("/api/summary-tasks/" + id + "/retry")).andExpect(status().isConflict());
    }

    @Test
    void expiredAttemptDoesNotPublishOrOverwriteAResult() throws Exception {
        long source = source("过期摘要夹具");
        UUID id = enqueue("/api/sources/" + source + "/summary-task");
        when(engine.generate(anyList())).thenAnswer(call -> {
            assertEquals(1, jdbc.sql("UPDATE summary_tasks SET lease_until = clock_timestamp() WHERE id = :id")
                    .param("id", id).update());
            return draft(call.getArgument(0));
        });
        worker.tick();
        mvc.perform(get("/api/sources/" + source + "/summary")).andExpect(status().isNoContent());
        tasks.expire();
        assertEquals("TASK_LEASE_EXPIRED", tasks.find(id).orElseThrow().errorCode());
    }

    @Test
    void failureAfterSummaryInsertRollsBackUntilSameTaskRetries() throws Exception {
        long source = source("摘要事务回滚夹具");
        UUID id = enqueue("/api/sources/" + source + "/summary-task");
        when(engine.generate(anyList())).thenAnswer(call -> draft(call.getArgument(0)));
        // 首次在成功状态写入前中断，确认本事务确实已经执行过摘要插入。
        doAnswer(call -> {
            assertTrue(summaries.find(source, call.getArgument(1)).isPresent());
            throw new IllegalStateException("Synthetic failure after summary insert");
        }).doCallRealMethod().when(summaries).completeFromSaved(
                argThat(detail -> detail.source().id() == source), anyString(), argThat(task -> task.id().equals(id)));
        worker.tick();
        mvc.perform(get("/api/sources/" + source + "/summary")).andExpect(status().isNoContent());
        mvc.perform(get("/api/summary-tasks/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("TASK_INTERNAL_ERROR"));

        mvc.perform(post("/api/summary-tasks/" + id + "/retry")).andExpect(status().isAccepted());
        worker.tick();
        mvc.perform(get("/api/summary-tasks/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED")).andExpect(jsonPath("$.attempt").value(2));
        mvc.perform(get("/api/sources/" + source + "/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.citations[0].startMs").value(1000));
    }

    private long source(String title) {
        return sources.insert(title, "SRT", "task.srt",
                "1\n00:00:01,000 --> 00:00:02,400\nCache rebuild follows expiry.\n").source().id();
    }

    private UUID enqueue(String path) throws Exception {
        var response = mvc.perform(post(path)).andExpect(status().isAccepted()).andReturn().getResponse();
        return UUID.fromString(mapper.readTree(response.getContentAsString()).path("id").asText());
    }

    private static Draft draft(List<Evidence> evidence) {
        var ids = evidence.stream().map(Evidence::evidenceId).toList();
        return new Draft("合成摘要", ids, false, ids);
    }
}
