package dev.citeweave.video.local;

import dev.citeweave.source.LocalVideoSubtitleService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 无需数据库或网络，用真实 FFmpeg 合成媒体并走 HTTP 到 FFprobe。 */
@WebMvcTest(properties = "citeweave.video.probe.max-duration=2s", controllers = LocalVideoController.class)
@Import(LocalVideoProbe.class)
@EnabledIfEnvironmentVariable(named = "CITEWEAVE_LOCAL_VIDEO_TEST", matches = "1")
class LocalVideoPreviewIntegrationTest {
    private static final String PREVIEW = "/api/videos/local/preview";
    @TempDir Path temp;
    @Autowired MockMvc mvc;
    @MockitoBean LocalVideoService videos;
    @MockitoBean LocalVideoSubtitleService subtitles;
    @MockitoBean LocalVideoAsrService asr;

    @Test
    void readsRealTracksEvenWhenClientMimeTypeIsGeneric() throws Exception {
        var bytes = sample("sample.mp4", "1", true);
        mvc.perform(multipart(PREVIEW).file(part("sample.mp4", bytes))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.durationMs").value(1000))
                .andExpect(jsonPath("$.sizeBytes").value(bytes.length))
                .andExpect(jsonPath("$.width").value(160)).andExpect(jsonPath("$.height").value(90))
                .andExpect(jsonPath("$.videoCodec").value("mpeg4"))
                .andExpect(jsonPath("$.audioCodec").value("aac"));
    }

    @Test
    void rejectsRenamedTextAndAudioOnlyContainer() throws Exception {
        mvc.perform(multipart(PREVIEW).file(part("fake.mp4", new byte[]{65, 66, 67})))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("VIDEO_INVALID"));
        mvc.perform(multipart(PREVIEW).file(part("audio.mp4", sample("audio.mp4", "1", false))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("VIDEO_INVALID"));
    }

    @Test
    void rejectsVideoBeyondConfiguredDuration() throws Exception {
        mvc.perform(multipart(PREVIEW).file(part("long.mov", sample("long.mov", "3", true))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("VIDEO_DURATION_EXCEEDED"));
    }

    private MockMultipartFile part(String filename, byte[] bytes) {
        return new MockMultipartFile("file", filename, "application/octet-stream", bytes);
    }

    private byte[] sample(String filename, String seconds, boolean video) throws Exception {
        Path output = temp.resolve(filename);
        var command = new ArrayList<>(List.of("ffmpeg", "-v", "error", "-f", "lavfi", "-i",
                "sine=frequency=440:sample_rate=44100", "-c:a", "aac"));
        if (video) {
            command.addAll(List.of("-f", "lavfi", "-i", "color=c=blue:s=160x90:r=25", "-c:v", "mpeg4"));
        }
        command.addAll(List.of("-t", seconds, output.toString()));
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "FFmpeg fixture exceeded timeout");
            assertEquals(0, process.exitValue(), () -> {
                try {
                    return new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                } catch (java.io.IOException error) {
                    return error.toString();
                }
            });
            return Files.readAllBytes(output);
        } finally {
            process.destroyForcibly();
        }
    }
}
