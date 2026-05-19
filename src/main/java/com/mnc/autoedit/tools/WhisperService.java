package com.mnc.autoedit.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnc.autoedit.edit.WordTimestamp;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

@Service
public class WhisperService {
    private final ToolConfig cfg;
    private final ProcessRunner runner;
    private final ObjectMapper om;

    public WhisperService(ToolConfig config, ProcessRunner processRunner, ObjectMapper objectMapper) {
        this.cfg = config;
        this.runner = processRunner;
        this.om = objectMapper;
    }

    /**
     * Runs whisper.cpp CLI and returns word timestamps.
     *
     * We invoke JSON-full output and then parse either:
     * - segment.words[] (preferred when present), or
     * - segment.tokens[] with t0/t1 (fallback).
     */
    public List<WordTimestamp> transcribeWords(Path audioWav, Path outputDir, String languageHint) throws Exception {
        String outPrefix = outputDir.resolve("whisper").toString();

        List<String> cmd = new ArrayList<>();
        cmd.add(cfg.getWhisperPath());
        cmd.add("-m");
        cmd.add(cfg.getWhisperModelPath());
        cmd.add("-f");
        cmd.add(audioWav.toString());
        cmd.add("-ojf");
        cmd.add("-of");
        cmd.add(outPrefix);
        cmd.add("-sow"); // split on word where possible
        if (languageHint != null && !languageHint.isBlank()) {
            cmd.add("-l");
            cmd.add(languageHint.trim().toLowerCase(Locale.ROOT));
        }

        ProcessRunner.ProcessResult r = runner.run(cmd, Duration.ofMinutes(60));
        if (r.exitCode() != 0) {
            throw new RuntimeException("whisper failed: " + r.output());
        }

        Path jsonPath = Path.of(outPrefix + ".json");
        JsonNode root = om.readTree(jsonPath.toFile());
        List<WordTimestamp> words = parseWordTimestamps(root);
        if (words.isEmpty()) {
            throw new RuntimeException("whisper produced 0 parsed words; json keys=" + fieldNames(root));
        }
        return words;
    }

    private static List<WordTimestamp> parseWordTimestamps(JsonNode root) {
        List<WordTimestamp> out = new ArrayList<>();
        if (root == null) return out;

        JsonNode segments = root.get("segments");
        if (segments == null || !segments.isArray()) {
            // Newer whisper.cpp JSON-full uses transcription[] instead of segments[].
            segments = root.get("transcription");
        }
        if (segments == null || !segments.isArray()) return out;

        for (JsonNode seg : segments) {
            // Preferred: words array
            JsonNode words = seg.get("words");
            if (words != null && words.isArray()) {
                for (JsonNode w : words) {
                    addIfValid(out, safeText(w, "word", "text"), safeDouble(w, "start"), safeDouble(w, "end"));
                }
                continue;
            }

            // Fallback: tokens with t0/t1 (10ms ticks in older whisper.cpp JSON-full)
            JsonNode tokens = seg.get("tokens");
            if (tokens != null && tokens.isArray()) {
                for (JsonNode t : tokens) {
                    String text = t.has("text") ? t.get("text").asText() : t.has("token") ? t.get("token").asText() : null;
                    Double start = ticksToSeconds(safeLong(t, "t0"));
                    Double end = ticksToSeconds(safeLong(t, "t1"));
                    if (start == null || end == null) {
                        JsonNode offsets = t.get("offsets");
                        if (offsets != null) {
                            start = millisToSeconds(safeLong(offsets, "from"));
                            end = millisToSeconds(safeLong(offsets, "to"));
                        }
                    }
                    addIfValid(out, text, start, end);
                }
                continue;
            }

            // Last resort: segment-level text and offsets.
            JsonNode offsets = seg.get("offsets");
            if (offsets != null) {
                addIfValid(out, safeText(seg, "text"), millisToSeconds(safeLong(offsets, "from")), millisToSeconds(safeLong(offsets, "to")));
            }
        }
        return out;
    }

    private static String safeText(JsonNode node, String... keys) {
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v != null && v.isTextual()) return v.asText();
        }
        return null;
    }

    private static Double safeDouble(JsonNode node, String key) {
        JsonNode v = node.get(key);
        if (v == null) return null;
        if (v.isNumber()) return v.asDouble();
        return null;
    }

    private static Long safeLong(JsonNode node, String key) {
        JsonNode v = node.get(key);
        if (v == null) return null;
        if (v.isNumber()) return v.asLong();
        return null;
    }

    private static Double ticksToSeconds(Long ticks) {
        if (ticks == null) return null;
        // whisper.cpp uses 10ms ticks in older JSON-full token timestamps
        return ticks / 100.0;
    }

    private static Double millisToSeconds(Long millis) {
        if (millis == null) return null;
        return millis / 1000.0;
    }

    private static void addIfValid(List<WordTimestamp> out, String text, Double start, Double end) {
        if (text == null || start == null || end == null || end < start) return;
        String cleaned = text.trim();
        if (cleaned.isBlank() || cleaned.startsWith("[_")) return;
        out.add(new WordTimestamp(text, start, end));
    }

    private static String fieldNames(JsonNode node) {
        if (node == null) return "[]";
        List<String> names = new ArrayList<>();
        Iterator<String> it = node.fieldNames();
        while (it.hasNext()) names.add(it.next());
        return names.toString();
    }
}

