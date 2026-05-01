package com.mnc.autoedit.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mnc.autoedit.api.dto.CreateJobRequest;
import com.mnc.autoedit.api.dto.CreateJobResponse;
import com.mnc.autoedit.api.dto.JobStatusResponse;
import com.mnc.autoedit.jobs.JobRecord;
import com.mnc.autoedit.jobs.JobRepository;
import com.mnc.autoedit.jobs.JobStatus;
import com.mnc.autoedit.storage.S3StorageConfig;
import com.mnc.autoedit.storage.StorageService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/jobs")
@Profile("!nodb")
public class JobsController {
    private final JobRepository jobs;
    private final StorageService storage;
    private final S3StorageConfig storageCfg;
    private final ObjectMapper om;

    private final Duration uploadExpiry;
    private final Duration downloadExpiry;
    private final int maxQueueDepth;

    private final int defaultPauseThresholdMs;
    private final int defaultPaddingMs;
    private final int defaultMinKeepSegmentMs;
    private final List<String> defaultFillers;

    public JobsController(
            JobRepository jobRepository,
            StorageService storageService,
            S3StorageConfig s3StorageConfig,
            ObjectMapper objectMapper,
            @Value("${storage.presign.upload-minutes:60}") long uploadMinutes,
            @Value("${storage.presign.download-minutes:120}") long downloadMinutes,
            @Value("${api.max-queue-depth:50}") int maxQueueDepth,
            @Value("${edit.pause-threshold-ms:1500}") int defaultPauseThresholdMs,
            @Value("${edit.padding-ms:120}") int defaultPaddingMs,
            @Value("${edit.min-keep-segment-ms:400}") int defaultMinKeepSegmentMs,
            @Value("${edit.filler-words:um,uh,ah,hmm,err}") String fillersCsv
    ) {
        this.jobs = jobRepository;
        this.storage = storageService;
        this.storageCfg = s3StorageConfig;
        this.om = objectMapper;

        this.uploadExpiry = Duration.ofMinutes(uploadMinutes);
        this.downloadExpiry = Duration.ofMinutes(downloadMinutes);
        this.maxQueueDepth = maxQueueDepth;

        this.defaultPauseThresholdMs = defaultPauseThresholdMs;
        this.defaultPaddingMs = defaultPaddingMs;
        this.defaultMinKeepSegmentMs = defaultMinKeepSegmentMs;
        this.defaultFillers = List.of(fillersCsv.split(","));
    }

    @PostMapping
    public ResponseEntity<CreateJobResponse> createJob(
            @RequestHeader(name = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody(required = false) CreateJobRequest req
    ) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        if (jobs.queueDepth() >= maxQueueDepth) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("Retry-After", "60")
                    .build();
        }

        JobRecord job = jobs.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (job == null) {
            ObjectNode params = om.createObjectNode();
            int pauseMs = req != null && req.pauseThresholdMs() != null ? req.pauseThresholdMs() : defaultPauseThresholdMs;
            int paddingMs = req != null && req.paddingMs() != null ? req.paddingMs() : defaultPaddingMs;
            int minKeepMs = req != null && req.minKeepSegmentMs() != null ? req.minKeepSegmentMs() : defaultMinKeepSegmentMs;
            List<String> fillers = req != null && req.fillerWords() != null && !req.fillerWords().isEmpty() ? req.fillerWords() : defaultFillers;
            String lang = req != null ? req.languageHint() : null;

            params.put("pauseThresholdMs", pauseMs);
            params.put("paddingMs", paddingMs);
            params.put("minKeepSegmentMs", minKeepMs);
            params.putPOJO("fillerWords", fillers);
            if (StringUtils.hasText(lang)) params.put("languageHint", lang);

            UUID jobId = UUID.randomUUID();
            String inputKey = storageCfg.inputKeyForJob(jobId.toString());
            job = jobs.createJobWithId(jobId, idempotencyKey, inputKey, params);
        }

        String inputKey = job.inputKey() != null ? job.inputKey() : storageCfg.inputKeyForJob(job.id().toString());

        final URL uploadUrl;
        try {
            uploadUrl = storage.presignUpload(inputKey, uploadExpiry);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        return ResponseEntity.ok(new CreateJobResponse(
                job.id(),
                uploadUrl.toString(),
                "/v1/jobs/" + job.id() + "/commit",
                "/v1/jobs/" + job.id()
        ));
    }

    @PostMapping("/{jobId}/commit")
    public ResponseEntity<Void> commitUpload(@PathVariable UUID jobId) {
        JobRecord job = jobs.findById(jobId).orElse(null);
        if (job == null) return ResponseEntity.notFound().build();

        String inputKey = storageCfg.inputKeyForJob(jobId.toString());
        if (!storage.exists(inputKey)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        jobs.markUploaded(jobId);
        jobs.enqueue(jobId);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobStatusResponse> getJob(@PathVariable UUID jobId) {
        JobRecord job = jobs.findById(jobId).orElse(null);
        if (job == null) return ResponseEntity.notFound().build();

        String downloadUrl = null;
        if (job.status() == JobStatus.SUCCEEDED && job.outputKey() != null) {
            try {
                downloadUrl = storage.presignDownload(job.outputKey(), downloadExpiry).toString();
            } catch (IllegalStateException e) {
                downloadUrl = null;
            }
        }

        return ResponseEntity.ok(new JobStatusResponse(
                job.id(),
                job.status(),
                job.attemptCount(),
                job.errorCode(),
                job.errorMessage(),
                job.createdAt(),
                job.updatedAt(),
                downloadUrl
        ));
    }
}

