package com.mnc.autoedit.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JobRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper om;

    public JobRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbc = jdbcTemplate;
        this.om = objectMapper;
    }

    private final RowMapper<JobRecord> mapper = new RowMapper<>() {
        @Override
        public JobRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            UUID id = rs.getObject("id", UUID.class);
            String idempotencyKey = rs.getString("idempotency_key");
            JobStatus status = JobStatus.valueOf(rs.getString("status"));
            String inputKey = rs.getString("input_key");
            String outputKey = rs.getString("output_key");
            String paramsStr = rs.getString("params");
            JsonNode params;
            try {
                params = paramsStr == null ? om.createObjectNode() : om.readTree(paramsStr);
            } catch (Exception e) {
                params = om.createObjectNode();
            }
            int attemptCount = rs.getInt("attempt_count");
            String workerId = rs.getString("worker_id");
            Instant leaseUntil = rs.getTimestamp("lease_until") == null ? null : rs.getTimestamp("lease_until").toInstant();
            Instant heartbeatAt = rs.getTimestamp("heartbeat_at") == null ? null : rs.getTimestamp("heartbeat_at").toInstant();
            String errorCode = rs.getString("error_code");
            String errorMessage = rs.getString("error_message");
            Instant createdAt = rs.getTimestamp("created_at").toInstant();
            Instant updatedAt = rs.getTimestamp("updated_at").toInstant();

            return new JobRecord(
                    id, idempotencyKey, status, inputKey, outputKey, params,
                    attemptCount, workerId, leaseUntil, heartbeatAt, errorCode, errorMessage,
                    createdAt, updatedAt
            );
        }
    };

    public Optional<JobRecord> findById(UUID id) {
        try {
            JobRecord rec = jdbc.queryForObject("SELECT * FROM jobs WHERE id = ?", mapper, id);
            return Optional.ofNullable(rec);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<JobRecord> findByIdempotencyKey(String idempotencyKey) {
        try {
            JobRecord rec = jdbc.queryForObject("SELECT * FROM jobs WHERE idempotency_key = ?", mapper, idempotencyKey);
            return Optional.ofNullable(rec);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public JobRecord createJob(String idempotencyKey, String inputKey, JsonNode params) {
        jdbc.update(
                "INSERT INTO jobs (idempotency_key, status, input_key, params) VALUES (?, ?, ?, ?::jsonb)",
                idempotencyKey, JobStatus.CREATED.name(), inputKey, params.toString()
        );
        return findByIdempotencyKey(idempotencyKey).orElseThrow();
    }

    public JobRecord createJobWithId(UUID jobId, String idempotencyKey, String inputKey, JsonNode params) {
        jdbc.update(
                "INSERT INTO jobs (id, idempotency_key, status, input_key, params) VALUES (?, ?, ?, ?, ?::jsonb)",
                jobId, idempotencyKey, JobStatus.CREATED.name(), inputKey, params.toString()
        );
        return findById(jobId).orElseThrow();
    }

    public void markUploaded(UUID jobId) {
        jdbc.update(
                "UPDATE jobs SET status = ?, updated_at = now() WHERE id = ? AND status IN (?, ?)",
                JobStatus.UPLOADED.name(), jobId, JobStatus.CREATED.name(), JobStatus.UPLOADED.name()
        );
    }

    public void enqueue(UUID jobId) {
        jdbc.update(
                "UPDATE jobs SET status = ?, updated_at = now() WHERE id = ? AND status IN (?, ?, ?)",
                JobStatus.QUEUED.name(), jobId, JobStatus.CREATED.name(), JobStatus.UPLOADED.name(), JobStatus.QUEUED.name()
        );
    }

    public Optional<JobRecord> claimNextQueued(String workerId, int maxAttempts, long leaseSeconds) {
        // Claim one job atomically. SKIP LOCKED enables competing consumers.
        String sql = """
            WITH cte AS (
              SELECT id
              FROM jobs
              WHERE status = ?
                AND attempt_count < ?
              ORDER BY created_at
              FOR UPDATE SKIP LOCKED
              LIMIT 1
            )
            UPDATE jobs j
            SET status = ?,
                worker_id = ?,
                attempt_count = attempt_count + 1,
                lease_until = now() + (? * interval '1 second'),
                heartbeat_at = now(),
                updated_at = now(),
                error_code = NULL,
                error_message = NULL
            FROM cte
            WHERE j.id = cte.id
            RETURNING j.*;
            """;

        try {
            JobRecord rec = jdbc.queryForObject(
                    sql,
                    mapper,
                    JobStatus.QUEUED.name(),
                    maxAttempts,
                    JobStatus.PROCESSING.name(),
                    workerId,
                    leaseSeconds
            );
            return Optional.ofNullable(rec);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void heartbeat(UUID jobId, String workerId, long leaseSeconds) {
        jdbc.update(
                "UPDATE jobs SET heartbeat_at = now(), lease_until = now() + (? * interval '1 second'), updated_at = now() WHERE id = ? AND worker_id = ? AND status = ?",
                leaseSeconds, jobId, workerId, JobStatus.PROCESSING.name()
        );
    }

    public void succeed(UUID jobId, String outputKey) {
        jdbc.update(
                "UPDATE jobs SET status = ?, output_key = ?, updated_at = now() WHERE id = ?",
                JobStatus.SUCCEEDED.name(), outputKey, jobId
        );
    }

    public void fail(UUID jobId, String errorCode, String errorMessage) {
        jdbc.update(
                "UPDATE jobs SET status = ?, error_code = ?, error_message = ?, updated_at = now() WHERE id = ?",
                JobStatus.FAILED.name(), errorCode, errorMessage, jobId
        );
    }

    public int queueDepth() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM jobs WHERE status IN (?, ?)",
                Integer.class,
                JobStatus.QUEUED.name(),
                JobStatus.PROCESSING.name()
        );
        return count == null ? 0 : count;
    }

    public Map<String, Integer> statusCounts() {
        return jdbc.query("SELECT status, count(*) AS c FROM jobs GROUP BY status", rs -> {
            java.util.HashMap<String, Integer> map = new java.util.HashMap<>();
            while (rs.next()) {
                map.put(rs.getString("status"), rs.getInt("c"));
            }
            return map;
        });
    }
}

