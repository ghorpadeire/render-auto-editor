package com.mnc.autoedit.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

public record CreateJobRequest(
        @Min(500) @Max(10_000) Integer pauseThresholdMs,
        @Min(0) @Max(2000) Integer paddingMs,
        @Min(100) @Max(2000) Integer minKeepSegmentMs,
        List<String> fillerWords,
        String languageHint
) {
}

