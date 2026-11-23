package dev.citeweave.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.citeweave.source.TranscriptSummaryEngine.Evidence;
import dev.citeweave.source.TranscriptSummaryEngine.QuestionDraft;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;

class TranscriptQuestionTest {
    private static final String PATH = "/api/sources/1/questions";
    private static final String QUESTION = "缓存怎样失效";
    private final TranscriptEvidenceReader reader = mock(TranscriptEvidenceReader.class);
    private final SourceService sourceService = mock(SourceService.class);
    private final DocumentEvidenceReader documents =
            new DocumentEvidenceReader(sourceService, mock(SourceRepository.class), reader);
    private final TranscriptSummaryEngine engine = mock(TranscriptSummaryEngine.class);
    private final EvidenceLexicalIndex lexical = new EvidenceLexicalIndex(documents, mock(EvidenceLexicalMapper.class),
            mock(PlatformTransactionManager.class), false);
    private final Evidence match = new Evidence("1:v:1", "1", "v", "transcript", 1000, 2000, "缓存失效后重新加载");
    private final Evidence other = new Evidence("1:v:2", "1", "v", "transcript", 2000, 3000, "数据库事务提交");
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new TranscriptQuestionController(new TranscriptQuestionService(reader, engine, false), documents, lexical))
            .setControllerAdvice(new SourceErrorHandler()).build();

    @BeforeEach
    void setupSource() {
        var source = new SourceRepository.Detail(null, "fixture");
        when(reader.load(1L)).thenReturn(source);
        when(reader.version(source)).thenReturn("v");
        when(reader.read(source, "v")).thenReturn(List.of(match, other));
    }

    @Test
    void noKeywordMatchReturnsRefusalWithoutEngine() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"海洋珊瑚\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INSUFFICIENT_EVIDENCE"))
                .andExpect(jsonPath("$.citations").isEmpty());
        verifyNoInteractions(engine);
    }

    @Test
    void reusesCanonicalCueForAnswer() throws Exception {
        when(engine.answer(QUESTION, List.of(match))).thenReturn(
                new QuestionDraft("ANSWERED", "重新加载", List.of(match.evidenceId()), false, List.of(match.evidenceId())));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"" + QUESTION + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWERED"))
                .andExpect(jsonPath("$.citations[0].text").value(match.text()))
                .andExpect(jsonPath("$.citations[0].startMs").value(1000));
    }

    @Test
    void cannotCiteUnretrievedCueEvenFromSameSource() throws Exception {
        when(engine.answer(QUESTION, List.of(match))).thenReturn(
                new QuestionDraft("ANSWERED", "提交", List.of(other.evidenceId()), false, List.of(match.evidenceId())));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"" + QUESTION + "\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("QUESTION_INVALID_CITATION"));
    }

    @Test
    void ranksKeywordOverlapThenKeepsOrderForTiesAndCapsCandidates() {
        var cues = IntStream.rangeClosed(1, 7).mapToObj(index -> new Evidence(
                "1:v:" + index, "1", "v", "transcript", index * 1000L, index * 1000L + 500,
                index == 6 ? "缓存失效" : "缓存统计")).toList();
        var results = TranscriptQuestionService.retrieve("缓存如何失效", cues);
        assertEquals(List.of("1:v:6", "1:v:1", "1:v:2", "1:v:3", "1:v:4"),
                results.stream().map(Evidence::evidenceId).toList());
    }

    @Test
    void enabledAliasFallbackCallsEngineWithCanonicalCue() throws Exception {
        var srt = new SourceRepository.Detail(null, "fixture");
        var pagination = new Evidence("1:v:3", "1", "v", "transcript", 10, 20, "列表采用游标分页");
        when(reader.load(1L)).thenReturn(srt);
        when(reader.version(srt)).thenReturn("v");
        when(reader.read(srt, "v")).thenReturn(List.of(pagination, match));
        when(engine.answer("怎么翻页", List.of(pagination))).thenReturn(
                new QuestionDraft("ANSWERED", "使用游标", List.of(pagination.evidenceId()), false,
                        List.of(pagination.evidenceId())));
        var enabled = MockMvcBuilders.standaloneSetup(
                new TranscriptQuestionController(new TranscriptQuestionService(reader, engine, true), documents, lexical)).build();
        enabled.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"怎么翻页\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citations[0].text").value(pagination.text()));
    }

    @Test
    void aliasFallbackDoesNotReorderNonemptyLexicalResults() {
        var aliasCandidate = new Evidence("1:v:3", "1", "v", "transcript", 3000, 4000, "采用游标分页");
        var candidates = List.of(match, aliasCandidate);
        assertEquals(List.of(match), TranscriptQuestionService.retrieveWithAliases("缓存如何翻页", candidates));
    }

    @Test
    void comparesTwoSelectedSourcesAndAnchorsTheirOwnSameTimeCues() throws Exception {
        var second = secondSource();
        var input = List.of(match.evidenceId(), second.evidenceId());
        when(engine.answer(QUESTION, List.of(match, second)))
                .thenReturn(new QuestionDraft("ANSWERED", "分别加载与清除", input, false, input));
        mvc.perform(post("/api/transcript-questions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceIds\":[1,2],\"question\":\"" + QUESTION + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citations[0].sourceId").value("1"))
                .andExpect(jsonPath("$.citations[1].sourceId").value("2"));
        mvc.perform(get("/api/sources/2/transcript/evidence").param("evidenceId", second.evidenceId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.text").value(second.text()));
        mvc.perform(get("/api/sources/2/transcript/evidence").param("evidenceId", match.evidenceId()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SOURCE_EVIDENCE_NOT_FOUND"));
    }

    @Test
    void rejectsUnselectedCitationAndDuplicateSelections() throws Exception {
        var second = secondSource();
        when(engine.answer(QUESTION, List.of(match, second))).thenReturn(new QuestionDraft("ANSWERED", "越界",
                List.of("3:v:1"), false, List.of(match.evidenceId(), second.evidenceId())));
        mvc.perform(post("/api/transcript-questions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceIds\":[1,2],\"question\":\"" + QUESTION + "\"}"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("QUESTION_INVALID_CITATION"));
        clearInvocations(engine);
        for (String ids : List.of("[1,1]", "[1,\"2\"]", "[1,2,3,4]")) {
            mvc.perform(post("/api/transcript-questions").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sourceIds\":" + ids + ",\"question\":\"查询\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("QUESTION_SOURCE_IDS_INVALID"));
        }
        verifyNoInteractions(engine);
    }

    @Test
    void documentAnswerUsesParagraphIdentityAndCanonicalText() throws Exception {
        var source = new SourceRepository.Summary(4, "文档", "MARKDOWN", "fixture.md", java.time.OffsetDateTime.MIN);
        when(sourceService.find(4)).thenReturn(new SourceRepository.Detail(
                source, "# 测试\r\n \r\n\t\r\n缓存失效后重建。\r\n\r\n队列负责异步任务。"));
        var paragraphs = documents.read(4);
        assertEquals(List.of("# 测试", "缓存失效后重建。", "队列负责异步任务。"),
                paragraphs.stream().map(Evidence::text).toList());
        var cue = paragraphs.get(1);
        var ids = List.of(cue.evidenceId());
        when(engine.answer("缓存失效", List.of(cue)))
                .thenReturn(new QuestionDraft("ANSWERED", "重建", ids, false, ids));

        mvc.perform(get("/api/sources/4/paragraphs")).andExpect(status().isOk())
                .andExpect(jsonPath("$[1].paragraph").value(2))
                .andExpect(jsonPath("$[1].startMs").doesNotExist());
        mvc.perform(post("/api/sources/4/document-questions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"缓存失效\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citations[0].evidenceId").value(cue.evidenceId()))
                .andExpect(jsonPath("$.citations[0].text").value("缓存失效后重建。"));
    }

    private Evidence secondSource() {
        var detail = new SourceRepository.Detail(null, "second fixture");
        var cue = new Evidence("2:v:1", "2", "v", "transcript", 1000, 2000, "缓存失效后清除");
        when(reader.load(2L)).thenReturn(detail);
        when(reader.version(detail)).thenReturn("v");
        when(reader.read(detail, "v")).thenReturn(List.of(cue));
        return cue;
    }
}
