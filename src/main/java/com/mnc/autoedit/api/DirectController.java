package com.mnc.autoedit.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mnc.autoedit.jobs.JobRecord;
import com.mnc.autoedit.jobs.JobRepository;
import com.mnc.autoedit.storage.LocalStorageService;
import com.mnc.autoedit.storage.S3StorageConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
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

@RestController
@RequestMapping("/v1/direct")
@Profile("!nodb")
public class DirectController {

    private final JobRepository jobs;
    private final LocalStorageService localStorage;
    private final S3StorageConfig storageCfg;
    private final ObjectMapper om;

    private final int defaultPauseThresholdMs;
    private final int defaultPaddingMs;
    private final int defaultMinKeepSegmentMs;
    private final List<String> defaultFillers;

    public DirectController(
            JobRepository jobRepository,
            LocalStorageService localStorageService,
            S3StorageConfig s3StorageConfig,
            ObjectMapper objectMapper,
            @Value("${edit.pause-threshold-ms:1500}") int defaultPauseThresholdMs,
            @Value("${edit.padding-ms:120}") int defaultPaddingMs,
            @Value("${edit.min-keep-segment-ms:400}") int defaultMinKeepSegmentMs,
            @Value("${edit.filler-words:um,uh,ah,hmm,err}") String fillersCsv
    ) {
        this.jobs = jobRepository;
        this.localStorage = localStorageService;
        this.storageCfg = s3StorageConfig;
        this.om = objectMapper;
        this.defaultPauseThresholdMs = defaultPauseThresholdMs;
        this.defaultPaddingMs = defaultPaddingMs;
        this.defaultMinKeepSegmentMs = defaultMinKeepSegmentMs;
        this.defaultFillers = List.of(fillersCsv.split(","));
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file uploaded"));
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".mp4")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only MP4 files are accepted"));
        }

        try {
            UUID jobId = UUID.randomUUID();
            String idempotencyKey = jobId.toString();

            ObjectNode params = om.createObjectNode();
            params.put("pauseThresholdMs", defaultPauseThresholdMs);
            params.put("paddingMs", defaultPaddingMs);
            params.put("minKeepSegmentMs", defaultMinKeepSegmentMs);
            params.putPOJO("fillerWords", defaultFillers);

            String inputKey = storageCfg.inputKeyForJob(jobId.toString());
            localStorage.saveUpload(inputKey, file.getBytes());

            jobs.createJobWithId(jobId, idempotencyKey, inputKey, params);
            jobs.markUploaded(jobId);
            jobs.enqueue(jobId);

            return ResponseEntity.ok(Map.of(
                    "jobId", jobId.toString(),
                    "status", "QUEUED",
                    "statusUrl", "/v1/direct/status/" + jobId,
                    "message", "Video uploaded and queued for processing"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<Map<String, Object>> status(@PathVariable UUID jobId) {
        JobRecord job = jobs.findById(jobId).orElse(null);
        if (job == null) return ResponseEntity.notFound().build();

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("jobId", job.id().toString());
        body.put("status", job.status().name());
        body.put("attemptCount", job.attemptCount());
        if (job.errorMessage() != null) body.put("error", job.errorMessage());
        if (job.status().name().equals("SUCCEEDED")) {
            body.put("downloadUrl", "/v1/direct/download/" + jobId);
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping("/download/{jobId}")
    public ResponseEntity<Resource> download(@PathVariable UUID jobId) {
        JobRecord job = jobs.findById(jobId).orElse(null);
        if (job == null) return ResponseEntity.notFound().build();
        if (!"SUCCEEDED".equals(job.status().name())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        String outputKey = job.outputKey();
        if (outputKey == null) return ResponseEntity.notFound().build();

        Path outputPath = localStorage.resolve(outputKey);
        if (!Files.exists(outputPath)) return ResponseEntity.notFound().build();

        FileSystemResource resource = new FileSystemResource(outputPath);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("video/mp4"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cleaned_" + jobId + ".mp4\"")
                .body(resource);
    }
}
