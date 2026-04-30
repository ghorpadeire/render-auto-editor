package com.mnc.autoedit.jobs;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record JobRecord(
        UUID id,
        String idempotencyKey,
        JobStatus status,
        String inputKey,
        String outputKey,
        JsonNode params,
        int attemptCount,
        String workerId,
        Instant leaseUntil,
        Instant heartbeatAt,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {}

