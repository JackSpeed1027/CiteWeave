package dev.citeweave.source;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SourceHttpContractTest {
    private final SourceRepository repository = mock(SourceRepository.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new SourceController(new SourceService(repository, new SourceParser())))
            .setControllerAdvice(new SourceErrorHandler()).build();

    @Test
    void returnsCreatedResourceAndLocation() throws Exception {
        var source = new SourceRepository.Summary(42, "资料", "TEXT", null,
                OffsetDateTime.parse("2026-11-20T12:00:00Z"));
        when(repository.insert("资料", "TEXT", null, "正文"))
                .thenReturn(new SourceRepository.Detail(source, "正文"));

        mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"资料","content":"正文"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/sources/42"))
                .andExpect(jsonPath("$.source.id").value(42))
                .andExpect(jsonPath("$.content").value("正文"));
    }

    @Test
    void returnsProblemWithStableCodeForMissingSource() throws Exception {
        mvc.perform(get("/api/sources/42"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:citeweave:problem:source-not-found"))
                .andExpect(jsonPath("$.code").value("SOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").isString());
    }

    @Test
    void returnsRequestInvalidForMalformedJson() throws Exception {
        mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"));
    }

    @Test
    void returnsContentInvalidWhenJsonContainsEmptyContent() throws Exception {
        mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"资料","content":""}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SOURCE_CONTENT_INVALID"));
    }
}
