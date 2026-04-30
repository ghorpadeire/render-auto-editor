# README for AI coding tools

This folder (`render-auto-editor/`) is a **new** Spring Boot project (separate from the older transcript JSON project).

## Goal

MP4 → MP4 async pipeline:

1. Client requests a job (idempotent)
2. Client uploads MP4 directly to S3/R2 via pre-signed URL
3. Worker downloads MP4, extracts audio (ffmpeg), transcribes (whisper.cpp), computes cut plan, renders cleaned MP4 (ffmpeg), uploads output
4. Client downloads via pre-signed URL

## Key constraints

- **O(n)** time in video duration (streaming, single-pass external tools)
- Do **not** load full video into memory
- Must run on **Render Basic**
- Uses native binaries via Docker: **ffmpeg** + **whisper-cli**

## Important files

- `pom.xml`: dependencies (Spring Boot Web/JDBC, Flyway, Postgres, AWS SDK S3)
- `src/main/resources/application.properties`: all configuration knobs (role, queue, edit params, storage, tool paths)
- `src/main/resources/db/migration/V1__create_jobs.sql`: DB schema (jobs table + indexes)
- `src/main/java/com/mnc/autoedit/api/JobsController.java`: API endpoints (`/v1/jobs/*`)
- `src/main/java/com/mnc/autoedit/worker/WorkerLoop.java`: worker claim + processing loop
- `src/main/java/com/mnc/autoedit/tools/FfmpegService.java`: audio extraction + final render
- `src/main/java/com/mnc/autoedit/tools/WhisperService.java`: runs whisper-cli and parses JSON
- `src/main/java/com/mnc/autoedit/edit/CutPlanner.java`: merges filler + pause removal → keep ranges
- `Dockerfile` + `docker/entrypoint.sh`: builds whisper-cli, installs ffmpeg, downloads model in worker mode

## How to add features safely

### Add a new “removal rule”

Update `CutPlanner.planCuts()` to add a new removal interval type, then make sure:

- intervals are merged (no overlaps)
- final keep segments are filtered by `minKeepSegmentMs`
- everything remains O(words)

### Add a new API parameter

1. Add field to `CreateJobRequest`
2. Persist into `jobs.params` JSON in `JobsController.createJob()`
3. Read it in `WorkerLoop.buildCutPlan()`

### Change output quality

Edit `FfmpegService.renderFromCutPlan()`:

- `preset` / `crf` / audio bitrate
- (future) add optional fast path (stream-copy) mode

## Reliability checklist

- Job creation must be idempotent (`Idempotency-Key`)
- Worker must be safe to restart:
  - DB claim uses `SKIP LOCKED`
  - Lease + heartbeat prevents “stuck” jobs
  - Deterministic keys prevent duplicate outputs
- Avoid proxying MP4 through API (always use pre-signed URLs)

