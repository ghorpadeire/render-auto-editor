# RenderAutoEditor (MP4 → MP4)

Spring Boot backend that:

- Accepts **MP4 uploads**
- Removes **filler words** (via **whisper.cpp** word timestamps)
- Removes **long pauses**
- Outputs a cleaned **MP4**

Designed for **Render (Basic plan)** using:

- **FFmpeg** for audio extraction + final render
- **whisper.cpp (`whisper-cli`)** for speech-to-text timestamps
- **PostgreSQL** as a job queue (`SKIP LOCKED` + leases)
- **S3-compatible storage** (Cloudflare R2 recommended) for direct uploads/downloads via pre-signed URLs

## How it runs

One codebase / one Docker image, two roles:

- **API**: `APP_ROLE=api`
- **Worker**: `APP_ROLE=worker`

## API (async job flow)

### Create job

`POST /v1/jobs`  
Headers: `Idempotency-Key: <uuid>`

Optional JSON body:

```json
{
  "pauseThresholdMs": 1500,
  "paddingMs": 120,
  "minKeepSegmentMs": 400,
  "fillerWords": ["um","uh","ah","hmm","err"],
  "languageHint": "en"
}
```

Response:

```json
{
  "jobId": "…",
  "uploadUrl": "https://…",
  "commitUrl": "/v1/jobs/<jobId>/commit",
  "statusUrl": "/v1/jobs/<jobId>"
}
```

### Upload MP4

Upload the MP4 directly to `uploadUrl` (HTTP PUT, content-type `video/mp4`).

### Commit upload (enqueue)

`POST /v1/jobs/<jobId>/commit` → returns `202 Accepted`.

### Poll status + download

`GET /v1/jobs/<jobId>`

When `status=SUCCEEDED`, you’ll receive a `downloadUrl` to the cleaned MP4.

## Local run (dev)

Prereqs:

- Java 17
- PostgreSQL
- An S3-compatible bucket (or use R2)

Run API locally:

```bash
cd render-auto-editor
mvn -q -DskipTests package
APP_ROLE=api java -jar target/render-auto-editor-0.1.0.jar
```

Run Worker locally (requires ffmpeg + whisper-cli in PATH):

```bash
APP_ROLE=worker java -jar target/render-auto-editor-0.1.0.jar
```

## Docker

Build:

```bash
docker build -t render-auto-editor:local ./render-auto-editor
```

Run API:

```bash
docker run --rm -p 8080:8080 -e APP_ROLE=api render-auto-editor:local
```

Run Worker:

```bash
docker run --rm -e APP_ROLE=worker render-auto-editor:local
```

## Required env vars (Render)

- `APP_ROLE`: `api` or `worker`
- `DATABASE_URL` (Render Postgres connection string — entrypoint converts it to the `JDBC_DATABASE_*` vars Spring needs)
  - Or set `JDBC_DATABASE_URL`, `JDBC_DATABASE_USERNAME`, `JDBC_DATABASE_PASSWORD` directly
- `STORAGE_BUCKET`, `STORAGE_REGION`, `STORAGE_ENDPOINT`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`
  - If storage is left blank the app will still start; storage-dependent endpoints return `503` until configured.

Optional:

- `WHISPER_MODEL_URL`, `WHISPER_MODEL_PATH`

## One-click deploy on Render (Blueprint)

This repo ships a `render.yaml` that creates the API web service and the Worker background service. **The Postgres database is currently commented out** in the blueprint so you can verify the deploy works end-to-end first; both services boot in the `nodb` Spring profile and only expose `/healthz`.

1. In Render → **New → Blueprint**, point it at this GitHub repo.
2. Render reads `render.yaml` and creates:
   - `render-auto-editor-api` (web)
   - `render-auto-editor-worker` (background worker)
3. After the first deploy, open each service → **Environment** and fill the storage secrets:
   - `STORAGE_BUCKET`, `STORAGE_ENDPOINT`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`
   - For Cloudflare R2: `STORAGE_REGION=auto`, `STORAGE_ENDPOINT=https://<accountid>.r2.cloudflarestorage.com`
   - For AWS S3: `STORAGE_REGION=us-east-1` (or your region), leave `STORAGE_ENDPOINT` blank
4. Trigger a redeploy on both services. The API exposes `/healthz`; storage-only endpoints work, but job creation will fail until the database is enabled.

### Enabling the database later

Once you're ready to take the system fully live:

1. In `render.yaml`, uncomment the `databases:` block and the two `DATABASE_URL` env vars.
2. Remove the `SPRING_PROFILES_ACTIVE=nodb` env vars on both services.
3. Commit/push and trigger a redeploy. Render provisions the DB and rewires `DATABASE_URL` to both services automatically.

