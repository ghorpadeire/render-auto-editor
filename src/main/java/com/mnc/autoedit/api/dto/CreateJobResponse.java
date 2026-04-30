package com.mnc.autoedit.api.dto;

import java.util.UUID;

public record CreateJobResponse(
        UUID jobId,
        String uploadUrl,
        String commitUrl,
        String statusUrl
) {}

