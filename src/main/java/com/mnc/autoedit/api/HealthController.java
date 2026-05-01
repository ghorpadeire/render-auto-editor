package com.mnc.autoedit.api;

import com.mnc.autoedit.jobs.JobRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {
    private final ObjectProvider<JobRepository> jobsProvider;

    public HealthController(ObjectProvider<JobRepository> jobsProvider) {
        this.jobsProvider = jobsProvider;
    }

    @GetMapping("/healthz")
    public Map<String, Object> health() {
        Map<String, Object> body = new HashMap<>();
        body.put("ok", true);
        JobRepository jobs = jobsProvider.getIfAvailable();
        if (jobs != null) {
            body.put("queueDepth", jobs.queueDepth());
            body.put("statusCounts", jobs.statusCounts());
        } else {
            body.put("db", "disabled");
        }
        return body;
    }
}
