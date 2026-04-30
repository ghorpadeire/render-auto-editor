package com.mnc.autoedit.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.mnc.autoedit.edit.CutPlan;
import com.mnc.autoedit.edit.CutPlanner;
import com.mnc.autoedit.edit.WordTimestamp;
import com.mnc.autoedit.jobs.JobRecord;
import com.mnc.autoedit.jobs.JobRepository;
import com.mnc.autoedit.storage.S3StorageConfig;
import com.mnc.autoedit.storage.StorageService;
import com.mnc.autoedit.tools.FfmpegService;
import com.mnc.autoedit.tools.WhisperService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
public class WorkerLoop {
    private final String role;
    private final long pollIntervalMs;
    private final long leaseSeconds;
    private final int maxAttempts;
    private final String workerId;
    private final Path localWorkDir;

    private final JobRepository jobs;
    private final StorageService storage;
    private final S3StorageConfig storageCfg;
    private final FfmpegService ffmpeg;
    private final WhisperService whisper;

    public WorkerLoop(
            @Value("${app.role:api}") String role,
            @Value("${worker.poll-interval-ms:1500}") long pollIntervalMs,
            @Value("${worker.lease-seconds:900}") long leaseSeconds,
            @Value("${worker.max-attempts:3}") int maxAttempts,
            @Value("${worker.local-workdir:/tmp/autoedit}") String localWorkDir,
            @Value("${worker.id:${HOSTNAME:worker}}") String workerId,
            JobRepository jobRepository,
            StorageService storageService,
            S3StorageConfig s3StorageConfig,
            FfmpegService ffmpegService,
            WhisperService whisperService
    ) {
        this.role = role == null ? "api" : role.trim().toLowerCase(Locale.ROOT);
        this.pollIntervalMs = pollIntervalMs;
        this.leaseSeconds = leaseSeconds;
        this.maxAttempts = maxAttempts;
        this.workerId = workerId;
        this.localWorkDir = Path.of(localWorkDir);

        this.jobs = jobRepository;
        this.storage = storageService;
        this.storageCfg = s3StorageConfig;
        this.ffmpeg = ffmpegService;
        this.whisper = whisperService;
    }

    public void maybeStart() {
        if (!"worker".equals(role)) {
            return;
        }

        Thread t = new Thread(this::runLoop, "worker-loop");
        t.setDaemon(true);
        t.start();
    }

    private void runLoop() {
        try {
            Files.createDirectories(localWorkDir);
        } catch (Exception e) {
            throw new RuntimeException("Unable to create local workdir: " + localWorkDir, e);
        }

        while (true) {
            try {
                jobs.claimNextQueued(workerId, maxAttempts, leaseSeconds)
                        .ifPresentOrElse(this::processJob, () -> sleep(pollIntervalMs));
            } catch (Exception e) {
                // If DB is temporarily unavailable, back off.
                sleep(Math.min(10_000, pollIntervalMs * 2));
            }
        }
    }

    private void processJob(JobRecord job) {
        UUID jobId = job.id();
        Path jobDir = localWorkDir.resolve(jobId.toString());
        Path inputMp4 = jobDir.resolve("input.mp4");
        Path audioWav = jobDir.resolve("audio.wav");
        Path outputMp4 = jobDir.resolve("cleaned.mp4");

        java.util.concurrent.ScheduledExecutorService hb = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "job-heartbeat");
            t.setDaemon(true);
            return t;
        });

        try {
            Files.createDirectories(jobDir);

            hb.scheduleAtFixedRate(
                    () -> jobs.heartbeat(jobId, workerId, leaseSeconds),
                    5,
                    15,
                    java.util.concurrent.TimeUnit.SECONDS
            );

            String inputKey = storageCfg.inputKeyForJob(jobId.toString());
            storage.downloadToFile(inputKey, inputMp4);

            ffmpeg.extractAudioWav16kMono(inputMp4, audioWav);

            String lang = job.params() != null && job.params().has("languageHint")
                    ? job.params().get("languageHint").asText()
                    : null;
            List<WordTimestamp> words = whisper.transcribeWords(audioWav, jobDir, lang);

            CutPlan plan = buildCutPlan(job.params(), words);

            ffmpeg.renderFromCutPlan(inputMp4, plan, outputMp4);

            String outputKey = storageCfg.outputKeyForJob(jobId.toString());
            storage.uploadFile(outputKey, outputMp4, "video/mp4");

            jobs.succeed(jobId, outputKey);
        } catch (Exception e) {
            jobs.fail(jobId, "PROCESSING_ERROR", safeMessage(e));
        } finally {
            hb.shutdownNow();
            try {
                deleteRecursively(jobDir);
            } catch (Exception ignored) {
            }
        }
    }

    private CutPlan buildCutPlan(JsonNode params, List<WordTimestamp> words) {
        int pauseMs = params != null && params.has("pauseThresholdMs") ? params.get("pauseThresholdMs").asInt(1500) : 1500;
        int paddingMs = params != null && params.has("paddingMs") ? params.get("paddingMs").asInt(120) : 120;
        int minKeepMs = params != null && params.has("minKeepSegmentMs") ? params.get("minKeepSegmentMs").asInt(400) : 400;

        List<String> fillers = List.of("um", "uh", "ah", "hmm", "err");
        if (params != null && params.has("fillerWords") && params.get("fillerWords").isArray()) {
            java.util.ArrayList<String> tmp = new java.util.ArrayList<>();
            for (JsonNode n : params.get("fillerWords")) tmp.add(n.asText());
            fillers = tmp;
        }

        Set<String> fillerSet = CutPlanner.parseFillerSet(fillers);
        CutPlanner planner = new CutPlanner();
        return planner.planCuts(
                words,
                pauseMs / 1000.0,
                paddingMs / 1000.0,
                minKeepMs / 1000.0,
                fillerSet
        );
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

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    private static String safeMessage(Throwable t) {
        String msg = t.getMessage();
        if (msg == null) return t.getClass().getSimpleName();
        return msg.length() > 5000 ? msg.substring(0, 5000) : msg;
    }
}

