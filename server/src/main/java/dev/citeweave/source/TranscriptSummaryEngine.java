package dev.citeweave.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 调用部署配置指定的内部 AI Engine，限制等待时间和响应大小。 */
@Component
class TranscriptSummaryEngine {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Evidence(String evidenceId, String sourceId, String sourceVersionId,
                    String locationType, Long startMs, Long endMs, String text, Integer paragraph, Integer page) {
        Evidence(String evidenceId, String sourceId, String sourceVersionId,
                 String locationType, long startMs, long endMs, String text) {
            this(evidenceId, sourceId, sourceVersionId, locationType, startMs, endMs, text, null, null);
        }
    }
    record Batch(List<Evidence> segments) {}
    record Draft(String summary, List<String> evidenceIds, Boolean truncated, List<String> inputEvidenceIds) {}
    record QuestionBatch(String question, List<Evidence> segments) {}
    record QuestionDraft(String status, String answer, List<String> evidenceIds,
                         Boolean truncated, List<String> inputEvidenceIds) {}
    record ResearchRequest(String question, List<Long> sourceIds) {}
    record CheckpointTarget(UUID requestKey, UUID token) {
        @Override
        public String toString() {
            return "CheckpointTarget[requestKey=" + requestKey + "]";
        }
    }
    record Resume(List<ResearchStep> trace, ResearchHistoryRepository.Context context) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ResearchRun(String question, List<Long> sourceIds, CheckpointTarget checkpoint, Resume resume) {}
    record ResearchStep(String callId, String tool, List<String> evidenceIds) {}
    record ResearchDraft(String status, String answer, List<String> evidenceIds,
                         List<ResearchStep> trace, String code) {}
    record ResearchReply(int status, ResearchDraft draft) {}

    enum Failure {
        MODEL_NOT_CONFIGURED, MODEL_TIMEOUT, MODEL_RATE_LIMITED, EVIDENCE_INSUFFICIENT,
        INVALID_CITATION, INVALID_OUTPUT, AI_UNAVAILABLE, SOURCE_CHANGED
    }

    static final class SummaryException extends RuntimeException {
        final Failure failure;

        SummaryException(Failure failure) {
            super(failure.name());
            this.failure = failure;
        }
    }

    private final RestClient client;
    private final ObjectMapper mapper;

    TranscriptSummaryEngine(@Value("${citeweave.ai.base-url:http://127.0.0.1:8091}") String baseUrl,
                            ObjectMapper mapper) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(32));
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.mapper = mapper;
    }

    /** 仅将已知的上游失败映射为业务错误。 */
    Draft generate(List<Evidence> evidence) {
        return request("/internal/v1/transcript-summaries", new Batch(evidence), Draft.class);
    }

    /** 向内部问答端点发送选出的字幕和用户问题。 */
    QuestionDraft answer(String question, List<Evidence> evidence) {
        return request("/internal/v1/transcript-questions", new QuestionBatch(question, evidence), QuestionDraft.class);
    }

    /** 内部研究成功和失败都可携带已完成轨迹，其内容交给业务服务复核。 */
    ResearchReply research(String question, List<Long> sourceIds) {
        return exchange("/internal/v1/research", new ResearchRequest(question, sourceIds),
                (status, payload) -> new ResearchReply(status, decode(payload, ResearchDraft.class)));
    }

    /** 有键运行附带一次性回调授权，由 Python 写完检查点再继续工具流程。 */
    ResearchReply research(String question, List<Long> sourceIds, CheckpointTarget checkpoint) {
        return resume(question, sourceIds, checkpoint, null);
    }

    /** 只把已落库的断点传给 Python 恢复。 */
    ResearchReply resume(String question, List<Long> sourceIds, CheckpointTarget checkpoint, Resume resume) {
        return exchange("/internal/v1/research", new ResearchRun(question, sourceIds, checkpoint, resume),
                (status, payload) -> new ResearchReply(status, decode(payload, ResearchDraft.class)));
    }

    private <T> T request(String endpoint, Object body, Class<T> responseType) {
        return exchange(endpoint, body, (status, payload) -> {
            if (status != 200) {
                String code = payload == null ? "" : payload.path("code").asText();
                throw new SummaryException(switch (code) {
                    case "SUMMARY_MODEL_NOT_CONFIGURED" -> Failure.MODEL_NOT_CONFIGURED;
                    case "MODEL_TIMEOUT" -> Failure.MODEL_TIMEOUT;
                    case "MODEL_RATE_LIMITED" -> Failure.MODEL_RATE_LIMITED;
                    case "SUMMARY_EVIDENCE_INSUFFICIENT" -> Failure.EVIDENCE_INSUFFICIENT;
                    case "SUMMARY_INVALID_CITATION" -> Failure.INVALID_CITATION;
                    case "SUMMARY_INVALID_OUTPUT" -> Failure.INVALID_OUTPUT;
                    default -> Failure.AI_UNAVAILABLE;
                });
            }
            return decode(payload, responseType);
        });
    }

    private <T> T decode(JsonNode payload, Class<T> responseType) {
        try {
            return mapper.treeToValue(payload, responseType);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new SummaryException(Failure.INVALID_OUTPUT);
        }
    }

    private <T> T exchange(String endpoint, Object body, BiFunction<Integer, JsonNode, T> decode) {
        try {
            return client.post().uri(endpoint)
                    .body(body)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        byte[] bytes = response.getBody().readNBytes(32769);
                        if (bytes.length > 32768) throw new SummaryException(Failure.INVALID_OUTPUT);
                        return decode.apply(status, mapper.readTree(bytes));
                    });
        } catch (RestClientException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                    throw new SummaryException(Failure.MODEL_TIMEOUT);
                }
            }
            throw new SummaryException(Failure.AI_UNAVAILABLE);
        }
    }
}
