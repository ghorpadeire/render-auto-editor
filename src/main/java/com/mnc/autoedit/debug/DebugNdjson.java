package com.mnc.autoedit.debug;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;

public final class DebugNdjson {
    private static final String SESSION_ID = "c5c838";
    private static final Path LOG_PATH = Path.of("debug-c5c838.log");
    private static final ObjectMapper OM = new ObjectMapper();

    private DebugNdjson() {}

    public static void log(String runId, String hypothesisId, String location, String message, ObjectNode data) {
        try {
            ObjectNode root = OM.createObjectNode();
            root.put("sessionId", SESSION_ID);
            root.put("id", "log_" + Instant.now().toEpochMilli() + "_" + UUID.randomUUID());
            root.put("timestamp", Instant.now().toEpochMilli());
            root.put("runId", runId);
            root.put("hypothesisId", hypothesisId);
            root.put("location", location);
            root.put("message", message);
            root.set("data", data == null ? OM.createObjectNode() : data);
            String line = OM.writeValueAsString(root) + "\n";
            Files.writeString(LOG_PATH, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // Never break app startup due to debug logging.
        }
    }

    public static ObjectNode data() {
        return OM.createObjectNode();
    }
}

