package dev.citeweave.video.bilibili;

import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

class BilibiliPreviewHttpTest {
    private static final String PATH = "/api/video-imports/bilibili/preview";
    private static final String UPSTREAM = "https://api.bilibili.com/x/web-interface/view?";
    private static final String BV_QUERY = UPSTREAM + "bvid=BV1xx411c7mD";
    private static final String BODY = """
            {"code":0,"data":{"bvid":"BV1xx411c7mD","title":"合成视频样例","pages":[
              {"page":1,"cid":9007199254740993,"part":"上篇","duration":61},
              {"page":2,"cid":12,"part":"下篇","duration":82}
            ]}}
            """;

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer upstream = MockRestServiceServer.bindTo(builder).build();
    private final BilibiliMetadataClient metadata = new BilibiliMetadataClient(new ObjectMapper(), builder.build());
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new BilibiliPreviewController(new BilibiliInputParser(), metadata))
            .setControllerAdvice(new BilibiliImportErrors()).build();

    @Test
    void returnsPartsAndKeepsCidPrecision() throws Exception {
        upstream.expect(requestTo(BV_QUERY)).andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));
        mvc.perform(preview("https://www.bilibili.com/video/BV1xx411c7mD?p=2&from=tracking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedPart").value(2))
                .andExpect(jsonPath("$.parts[0].cid").value("9007199254740993"))
                .andExpect(jsonPath("$.parts[1].durationSeconds").value(82));
        upstream.verify();
    }

    @Test
    void usesVideoTitleWhenTheOnlyPartHasNoTitle() throws Exception {
        String response = """
                {"code":0,"data":{"bvid":"BV1xx411c7mD","title":"合成视频样例","pages":[
                  {"page":1,"cid":62131,"part":"","duration":2055}
                ]}}
                """;
        upstream.expect(requestTo(BV_QUERY)).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        mvc.perform(preview("BV1xx411c7mD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedPart").value(1))
                .andExpect(jsonPath("$.parts[0].title").value("合成视频样例"));
        upstream.verify();
    }

    @Test
    void rejectsMissingPartAndMapsAvToAid() throws Exception {
        upstream.expect(requestTo(UPSTREAM + "aid=170001"))
                .andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));
        mvc.perform(preview("https://www.bilibili.com/video/av170001?p=3"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("BILIBILI_PART_NOT_FOUND"));
        upstream.verify();
    }

    @Test
    void rejectsShortLinksBeforeNetworkIo() throws Exception {
        upstream.expect(never(), requestTo("https://b23.tv/hello"));
        mvc.perform(preview("https://b23.tv/hello"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("BILIBILI_SHORT_LINK_UNSUPPORTED"));
        upstream.verify();
    }

    @Test
    void reportsMissingVideo() throws Exception {
        upstream.expect(requestTo(BV_QUERY)).andRespond(withSuccess("{\"code\":-404}", MediaType.APPLICATION_JSON));
        mvc.perform(preview("BV1xx411c7mD"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BILIBILI_VIDEO_NOT_FOUND"));
    }

    @Test
    void reportsRateLimit() throws Exception {
        upstream.expect(requestTo(BV_QUERY)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        mvc.perform(preview("BV1xx411c7mD"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("BILIBILI_RATE_LIMITED"));
    }

    @Test
    void reportsMalformedResponse() throws Exception {
        upstream.expect(requestTo(BV_QUERY)).andRespond(withSuccess("{", MediaType.APPLICATION_JSON));
        mvc.perform(preview("BV1xx411c7mD"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("BILIBILI_UPSTREAM_UNAVAILABLE"));
    }

    @Test
    void reportsTimeout() throws Exception {
        upstream.expect(requestTo(BV_QUERY)).andRespond(request -> {
            throw new HttpTimeoutException("sample timeout");
        });
        mvc.perform(preview("BV1xx411c7mD"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("BILIBILI_UPSTREAM_TIMEOUT"));
    }

    private static RequestBuilder preview(String input) {
        return post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"input\":\"" + input + "\"}");
    }
}
