package com.mnc.autoedit.api.dto;

import com.mnc.autoedit.jobs.JobStatus;

import java.time.Instant;
import java.util.UUID;

public record JobStatusResponse(
        UUID jobId,
        JobStatus status,
        int attemptCount,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt,
        String downloadUrl
) {}

