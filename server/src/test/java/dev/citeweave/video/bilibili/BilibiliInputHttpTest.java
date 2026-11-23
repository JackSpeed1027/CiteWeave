package dev.citeweave.video.bilibili;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BilibiliInputHttpTest {
    private static final String PATH = "/api/video-imports/bilibili/normalize";
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new BilibiliInputController(new BilibiliInputParser())).build();

    @Test
    void returnsNormalizedReferenceAndPartHint() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"input":"https://www.bilibili.com/video/BV1xx411c7mD/?p=2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("BV"))
                .andExpect(jsonPath("$.canonicalUrl").value("https://www.bilibili.com/video/BV1xx411c7mD"))
                .andExpect(jsonPath("$.requestedPart").value(2));
    }

    @Test
    void returnsStableProblemCodeForInvalidLink() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"input":"https://example.invalid/video/BV1xx411c7mD"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BILIBILI_INPUT_INVALID"));
    }
}
