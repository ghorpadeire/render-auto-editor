package com.mnc.autoedit.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mnc.autoedit.edit.WordTimestamp;
import com.mnc.autoedit.tools.FfmpegService;
import com.mnc.autoedit.tools.WhisperService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Synchronous transcription endpoint for Hermes Agent.
 * Accepts an MP4, extracts audio via FFmpeg, transcribes via whisper.cpp,
 * and returns word-level timestamps without any editing or job queue.
 * Requires the whisper model to be present (only available when APP_ROLE=worker).
 */
@RestController
@RequestMapping("/v1/transcribe")
public class TranscribeController {

    private final FfmpegService ffmpeg;
    private final WhisperService whisper;
    private final ObjectMapper om;
    private final String apiKey;

    public TranscribeController(
            FfmpegService ffmpegService,
            WhisperService whisperService,
            ObjectMapper objectMapper,
            @Value("${transcribe.api-key:}") String apiKey
    ) {
        this.ffmpeg = ffmpegService;
        this.whisper = whisperService;
        this.om = objectMapper;
        this.apiKey = apiKey;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> transcribe(
            @RequestHeader(value = "X-API-Key", required = false) String requestKey,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "languageHint", required = false) String languageHint
    ) {
        if (!apiKey.isBlank() && !apiKey.equals(requestKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid API key"));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file uploaded"));
        }

        Path jobDir = Path.of(System.getProperty("java.io.tmpdir"), "transcribe", UUID.randomUUID().toString());
        Path inputFile = jobDir.resolve("input.mp4");
        Path audioWav = jobDir.resolve("audio.wav");

        try {
            Files.createDirectories(jobDir);
            file.transferTo(inputFile.toFile());

            ffmpeg.extractAudioWav16kMono(inputFile, audioWav);
            List<WordTimestamp> words = whisper.transcribeWords(audioWav, jobDir, languageHint);

            StringBuilder transcript = new StringBuilder();
            ArrayNode wordsArray = om.createArrayNode();
            for (WordTimestamp w : words) {
                if (!transcript.isEmpty()) transcript.append(" ");
                transcript.append(w.text().trim());

                ObjectNode wn = om.createObjectNode();
                wn.put("word", w.text());
                wn.put("start", w.startSec());
                wn.put("end", w.endSec());
                wordsArray.add(wn);
            }

            ObjectNode result = om.createObjectNode();
            result.put("transcript", transcript.toString());
            result.set("words", wordsArray);
            result.put("word_count", words.size());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Transcription failed: " + e.getMessage()));
        } finally {
            try {
                deleteRecursively(jobDir);
            } catch (Exception ignored) {
            }
        }
    }

    private static void deleteRecursively(Path dir) throws Exception {
        if (dir == null || !Files.exists(dir)) return;
        Files.walk(dir)
                .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
    }
}
