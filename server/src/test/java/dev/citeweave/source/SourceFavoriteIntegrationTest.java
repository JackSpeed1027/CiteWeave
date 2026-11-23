package dev.citeweave.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** 在独立库验证收藏状态、唯一约束及原资料兼容。 */
@EnabledIfEnvironmentVariable(named = "CITEWEAVE_ISOLATED_PG_TEST", matches = "1")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:15434/citeweave_summary_qa",
        "spring.datasource.username=citeweave_summary_qa",
        "spring.datasource.password=citeweave_summary_qa_local"
})
class SourceFavoriteIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcClient jdbc;

    @Test
    void preservesOneRelationshipAndDoesNotModifySource() throws Exception {
        String sourceBody = """
                {"title":"收藏测试夹具","content":"保留原始正文。"}
                """;
        var created = mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON).content(sourceBody))
                .andExpect(status().isCreated()).andReturn().getResponse();
        long id = mapper.readTree(created.getContentAsString()).at("/source/id").asLong();
        String source = "/api/sources/" + id;
        String path = source + "/favorite";
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.favorited").value(false));
        mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content("{\"favorited\":\"true\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SOURCE_FAVORITE_INVALID"));
        assertEquals(0L, rows(id));

        try (var executor = Executors.newFixedThreadPool(8)) {
            var requests = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 8; i++) {
                requests.add(executor.submit(() -> mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"favorited\":true}"))
                        .andReturn().getResponse().getStatus()));
            }
            for (var request : requests) assertEquals(200, request.get());
        }
        assertEquals(1L, rows(id));
        mvc.perform(get("/api/sources").param("favorite", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == " + id + ")]").isNotEmpty());
        for (int i = 0; i < 2; i++) {
            mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content("{\"favorited\":false}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.favorited").value(false));
        }
        assertEquals(1L, rows(id));
        mvc.perform(get("/api/sources").param("favorite", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
        mvc.perform(get(source)).andExpect(status().isOk()).andExpect(jsonPath("$.content").value("保留原始正文。"));
        mvc.perform(get("/api/sources")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isNotEmpty());
        mvc.perform(put("/api/sources/-1/favorite").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorited\":true}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SOURCE_NOT_FOUND"));
    }

    private long rows(long id) {
        return jdbc.sql("SELECT count(*) FROM source_favorites WHERE source_id = :id")
                .param("id", id).query(Long.class).single();
    }
}
