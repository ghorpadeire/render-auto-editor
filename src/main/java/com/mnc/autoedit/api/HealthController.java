package com.mnc.autoedit.api;

import com.mnc.autoedit.jobs.JobRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {
    private final JobRepository jobs;

    public HealthController(JobRepository jobRepository) {
        this.jobs = jobRepository;
    }

    @GetMapping("/healthz")
    public Map<String, Object> health() {
        return Map.of(
                "ok", true,
                "queueDepth", jobs.queueDepth(),
                "statusCounts", jobs.statusCounts()
        );
    }
}

