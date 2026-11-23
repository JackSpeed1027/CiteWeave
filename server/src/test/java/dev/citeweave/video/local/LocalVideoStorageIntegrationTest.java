package dev.citeweave.video.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 隔离 PostgreSQL 和临时媒体目录中的真实 HTTP、FFprobe 和并发去重回归。 */
@SpringBootTest(properties = "citeweave.tasks.worker-enabled=false")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "CITEWEAVE_VIDEO_STORAGE_TEST", matches = "1")
class LocalVideoStorageIntegrationTest {
    private static final String VIDEOS = "/api/videos/local";
    @TempDir static Path temp;

    @DynamicPropertySource
    static void isolatedStorage(DynamicPropertyRegistry registry) {
        registry.add("citeweave.video.storage-root", () -> temp.resolve("storage").toString());
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcClient jdbc;
    @Autowired LocalVideoAsrService asr;
    @MockitoBean LocalVideoAsrRunner runner;

    @Test
    void parallelImportsPublishOneRecordAndOneObject() throws Exception {
        byte[] media = fixture();
        var ready = new CountDownLatch(2);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = workers.submit(() -> concurrentUpload(ready, "a.mp4", media));
            var second = workers.submit(() -> concurrentUpload(ready, "b.mp4", media));
            var responses = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertEquals(List.of(200, 201), responses.stream().map(r -> r.getResponse().getStatus()).sorted().toList());
            var firstBody = json.readTree(responses.getFirst().getResponse().getContentAsString());
            var secondBody = json.readTree(responses.getLast().getResponse().getContentAsString());
            assertEquals(firstBody, secondBody);
            mvc.perform(get(VIDEOS + "/" + firstBody.path("id").asText())).andExpect(status().isOk())
                    .andExpect(jsonPath("$.metadata.sizeBytes").value(media.length));
            mvc.perform(get(VIDEOS + "/" + UUID.randomUUID())).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("VIDEO_NOT_FOUND"));
            mvc.perform(multipart(VIDEOS).file(file("invalid.mp4", new byte[]{65})))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("VIDEO_INVALID"));
            try (var objects = Files.list(temp.resolve("storage"))) {
                var names = objects.map(path -> path.getFileName().toString()).toList();
                assertTrue(names.contains(firstBody.path("sha256").asText() + ".media"));
                assertTrue(names.stream().noneMatch(name -> name.startsWith("stage-")));
            }
        }
    }

    @Test
    void expiredTranscriptionCannotPublishAndSameJobCanRetry() throws Exception {
        when(runner.available()).thenReturn(true);
        var video = mvc.perform(multipart(VIDEOS).file(file("lecture.mp4", fixture(true))))
                .andExpect(status().isCreated()).andReturn();
        UUID videoId = UUID.fromString(json.readTree(video.getResponse().getContentAsString()).path("id").asText());
        String job = VIDEOS + "/" + videoId + "/transcription";
        mvc.perform(post(job)).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("QUEUED"));
        mvc.perform(post(job)).andExpect(status().isAccepted()).andExpect(jsonPath("$.attempt").value(0));
        String vtt = "WEBVTT\n\n00:00:00.100 --> 00:00:00.900\nMachine fixture.\n";
        when(runner.transcribe(any())).thenAnswer(call -> {
            jdbc.sql("UPDATE local_video_asr_tasks SET lease_until = clock_timestamp() WHERE video_id = :id")
                    .param("id", videoId).update();
            return vtt;
        }).thenReturn(vtt);

        asr.runNext();
        asr.runNext();
        mvc.perform(get(job)).andExpect(status().isOk())
                .andExpect(jsonPath("$.task.errorCode").value("ASR_LEASE_EXPIRED"));
        mvc.perform(get(VIDEOS + "/" + videoId + "/subtitles")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(post(job + "/retry")).andExpect(status().isAccepted());
        asr.runNext();
        var completed = mvc.perform(get(job)).andExpect(status().isOk())
                .andExpect(jsonPath("$.task.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.task.attempt").value(2)).andReturn();
        long sourceId = json.readTree(completed.getResponse().getContentAsString()).path("task").path("sourceId").asLong();
        mvc.perform(get("/api/sources/" + sourceId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.localVideoOrigin.trackSource").value("MACHINE_ASR"));
        mvc.perform(get(VIDEOS + "/" + videoId + "/subtitles")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(post(job + "/retry")).andExpect(status().isConflict());
    }

    @Test
    void bindsSubtitleWithinDurationAndKeepsItsOwnEvidenceIdentity() throws Exception {
        var video = mvc.perform(multipart(VIDEOS).file(file("lesson.mp4", fixture())))
                .andExpect(status().isCreated()).andReturn();
        String videoId = json.readTree(video.getResponse().getContentAsString()).path("id").asText();
        String tracks = VIDEOS + "/" + videoId + "/subtitles";
        byte[] valid = cue("900");
        var imported = mvc.perform(multipart(tracks).file(file("lesson.srt", valid)))
                .andExpect(status().isCreated()).andReturn();
        long id = json.readTree(imported.getResponse().getContentAsString()).path("sourceId").asLong();
        mvc.perform(multipart(tracks).file(file("renamed.srt", valid))).andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceId").value(id));
        mvc.perform(multipart(tracks).file(file("outside.srt", cue("999")))).andExpect(status().isCreated());
        mvc.perform(multipart(tracks).file(file("outside.srt",
                        "1\n00:00:00,100 --> 00:00:01,001\nLesson cue.\n".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("SOURCE_SUBTITLE_OUTSIDE_VIDEO"));
        mvc.perform(multipart(tracks).file(file("captions.vtt",
                        "WEBVTT\n\n00:00:00.100 --> 00:00:01.000\nVTT cue.\n".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.kind").value("VTT"));
        mvc.perform(get(tracks)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get("/api/sources/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.localVideoOrigin.videoId").value(videoId));
        mvc.perform(get("/api/sources/" + id + "/transcript")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].startMs").value(100)).andExpect(jsonPath("$[0].endMs").value(900));
        mvc.perform(multipart(VIDEOS + "/" + UUID.randomUUID() + "/subtitles").file(file("lesson.srt", valid)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("VIDEO_NOT_FOUND"));
    }

    private static byte[] cue(String endFraction) {
        return ("1\n00:00:00,100 --> 00:00:00," + endFraction + "\nLesson cue.\n").getBytes(StandardCharsets.UTF_8);
    }

    private MvcResult concurrentUpload(CountDownLatch ready, String name, byte[] media) throws Exception {
        ready.countDown();
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        return mvc.perform(multipart(VIDEOS).file(file(name, media))).andReturn();
    }

    private static MockMultipartFile file(String name, byte[] media) {
        return new MockMultipartFile("file", name, "application/octet-stream", media);
    }

    private static byte[] fixture() throws Exception {
        return fixture(false);
    }

    private static byte[] fixture(boolean audio) throws Exception {
        Path output = temp.resolve("fixture-" + UUID.randomUUID() + ".mp4");
        var command = new ArrayList<>(List.of("ffmpeg", "-n", "-v", "error", "-f", "lavfi", "-i",
                "color=c=blue:s=160x90:r=25"));
        if (audio) command.addAll(List.of("-f", "lavfi", "-i", "sine=frequency=440", "-c:a", "aac"));
        command.addAll(List.of("-t", "1", "-c:v", "mpeg4", "-metadata", "comment=" + UUID.randomUUID(),
                output.toString()));
        var ffmpeg = new ProcessBuilder(command).inheritIO().start();
        try {
            assertTrue(ffmpeg.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, ffmpeg.exitValue());
            return Files.readAllBytes(output);
        } finally {
            if (ffmpeg.isAlive()) ffmpeg.destroyForcibly();
        }
    }
}
