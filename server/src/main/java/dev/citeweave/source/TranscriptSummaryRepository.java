package dev.citeweave.source;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 负责摘要存储和 JSON 映射，业务校验留在摘要服务。 */
@Repository
class TranscriptSummaryRepository {
    private final TranscriptSummaryMapper mapper;
    private final ObjectMapper json;

    TranscriptSummaryRepository(TranscriptSummaryMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    Optional<TranscriptSummaryService.Result> find(long id, String version) {
        String stored = mapper.findResult(id, version);
        if (stored == null) return Optional.empty();
        try {
            return Optional.of(json.readValue(stored, TranscriptSummaryService.Result.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid stored transcript summary", exception);
        }
    }

    /** 按当前资料发布同步摘要。 */
    void publish(SourceRepository.Detail source, String version, TranscriptSummaryService.Result result,
                 List<String> inputIds) {
        publish(source, version, result, inputIds, null);
    }

    /** 摘要和任务终态一起提交。租约或资料变化时，撤回本次尝试写入的摘要。 */
    @Transactional
    void publishAndComplete(SourceRepository.Detail source, String version, TranscriptSummaryService.Result result,
                            List<String> inputIds, SummaryTaskRepository.Task task) {
        publish(source, version, result, inputIds, task);
        completeFromSaved(source, version, task);
    }

    /** 复用当前资料版本的摘要时，仍需凭任务租约提交成功。 */
    void completeFromSaved(SourceRepository.Detail source, String version, SummaryTaskRepository.Task task) {
        if (mapper.completeTask(source.source().id(), version, source.source().kind(), source.content(),
                task.id(), task.attempt()) != 1) {
            throw new TranscriptSummaryEngine.SummaryException(TranscriptSummaryEngine.Failure.SOURCE_CHANGED);
        }
    }

    private void publish(SourceRepository.Detail source, String version, TranscriptSummaryService.Result result,
                         List<String> inputIds, SummaryTaskRepository.Task task) {
        mapper.publish(source.source().id(), version, source.source().kind(), source.content(), encode(result),
                encode(inputIds), task == null ? null : task.id(), task == null ? 0 : task.attempt());
    }

    private String encode(Object result) {
        try {
            return json.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot encode transcript summary", exception);
        }
    }
}
