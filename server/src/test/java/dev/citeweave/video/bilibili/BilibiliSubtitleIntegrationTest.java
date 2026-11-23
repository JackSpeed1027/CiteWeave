package dev.citeweave.video.bilibili;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 只在指定的独立 PostgreSQL 运行，平台与模型结果为确定性合成数据。 */
@EnabledIfEnvironmentVariable(named = "CITEWEAVE_ISOLATED_PG_TEST", matches = "1")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:15434/citeweave_summary_qa",
        "spring.datasource.username=citeweave_summary_qa",
        "spring.datasource.password=citeweave_summary_qa_local"
})
class BilibiliSubtitleIntegrationTest {
    private static final String BV = "BV1xx411c7mD";
    private static final String ROUTE = "/api/video-imports/bilibili/subtitles";
    private static final String FIRST_CID = "9007199254740993";
    private static final String SECOND_CID = "12";
    private static final AtomicReference<String> FORCED_CITATION = new AtomicReference<>();
    private static HttpServer ai;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcClient jdbc;
    @MockitoBean BilibiliMetadataClient metadata;

    @DynamicPropertySource
    static void engine(DynamicPropertyRegistry properties) throws IOException {
        var mapper = new ObjectMapper();
        ai = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ai.createContext("/internal/v1/transcript-summaries", exchange -> {
            try (exchange) {
                var segments = mapper.readTree(exchange.getRequestBody()).get("segments");
                String actual = segments.get(0).get("evidenceId").asText();
                String forced = FORCED_CITATION.get();
                byte[] json = mapper.writeValueAsBytes(Map.of(
                        "summary", "夹具摘要", "truncated", false, "inputEvidenceIds", List.of(actual),
                        "evidenceIds", List.of(forced == null ? actual : forced)));
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, json.length);
                exchange.getResponseBody().write(json);
            }
        });
        ai.start();
        properties.add("citeweave.ai.base-url", () -> "http://127.0.0.1:" + ai.getAddress().getPort());
    }

    @AfterAll
    static void closeEngine() {
        if (ai != null) ai.stop(0);
    }

    @Test
    void bindsTwoPartsAndRejectsCrossTrackCitation() throws Exception {
        when(metadata.preview(any())).thenReturn(new BilibiliMetadataClient.Preview(BV, "合成视频", List.of(
                new BilibiliMetadataClient.Part(1, FIRST_CID, "第一集", 90),
                new BilibiliMetadataClient.Part(2, SECOND_CID, "第二集", 90)), 1));

        long before = countSources();
        mvc.perform(multipart(ROUTE).file(srt("第一集原句"))
                        .param("bvid", BV).param("page", "2").param("cid", FIRST_CID))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BILIBILI_PART_CHANGED"));
        mvc.perform(multipart(ROUTE).file(new MockMultipartFile("file", "invalid.txt", "text/plain", new byte[]{65}))
                        .param("bvid", BV).param("page", "2").param("cid", SECOND_CID))
                .andExpect(status().isUnsupportedMediaType());
        assertEquals(before, countSources());

        long first = attach(1, FIRST_CID, "第一集原句");
        long second = attach(2, SECOND_CID, "第二集原句");
        assertNotEquals(first, second);
        assertEquals(before + 2, countSources());

        mvc.perform(get("/api/sources/" + first))
                .andExpect(jsonPath("$.videoOrigin.cid").value(FIRST_CID));
        mvc.perform(get("/api/sources/" + second))
                .andExpect(jsonPath("$.videoOrigin.page").value(2))
                .andExpect(jsonPath("$.videoOrigin.cid").value(SECOND_CID))
                .andExpect(jsonPath("$.videoOrigin.trackSource").value("USER_UPLOAD"));
        String firstSummary = mvc.perform(post("/api/sources/" + first + "/summary"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        String secondSummary = "/api/sources/" + second + "/summary";
        FORCED_CITATION.set(mapper.readTree(firstSummary).at("/citations/0/evidenceId").asText());
        try {
            mvc.perform(post(secondSummary)).andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.code").value("SUMMARY_INVALID_CITATION"));
            mvc.perform(get(secondSummary)).andExpect(status().isNoContent());
        } finally {
            FORCED_CITATION.set(null);
        }

        mvc.perform(post(secondSummary)).andExpect(status().isOk());
        mvc.perform(get(secondSummary)).andExpect(status().isOk())
                .andExpect(jsonPath("$.citations[0].sourceId").value(Long.toString(second)))
                .andExpect(jsonPath("$.citations[0].text").value("第二集原句"))
                .andExpect(jsonPath("$.citations[0].startMs").value(1000))
                .andExpect(jsonPath("$.citations[0].endMs").value(2400));
    }

    private long attach(int page, String cid, String text) throws Exception {
        var response = mvc.perform(multipart(ROUTE).file(srt(text))
                        .param("bvid", BV).param("page", Integer.toString(page)).param("cid", cid))
                .andExpect(status().isCreated()).andReturn().getResponse();
        long id = mapper.readTree(response.getContentAsString()).get("sourceId").asLong();
        assertEquals("/api/sources/" + id, response.getHeader("Location"));
        return id;
    }

    private MockMultipartFile srt(String text) {
        String body = "1\n00:00:01,000 --> 00:00:02,400\n" + text + "\n";
        return new MockMultipartFile("file", "part-fixture.srt", "text/plain", body.getBytes(StandardCharsets.UTF_8));
    }

    private long countSources() {
        return jdbc.sql("SELECT count(*) FROM sources").query(Long.class).single();
    }
}
