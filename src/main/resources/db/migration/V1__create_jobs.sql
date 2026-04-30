CREATE TABLE IF NOT EXISTS jobs (
  id UUID PRIMARY KEY,
  idempotency_key TEXT NOT NULL,
  status TEXT NOT NULL,

  input_key TEXT,
  output_key TEXT,

  params JSONB NOT NULL DEFAULT '{}'::jsonb,

  attempt_count INT NOT NULL DEFAULT 0,
  worker_id TEXT,
  lease_until TIMESTAMPTZ,
  heartbeat_at TIMESTAMPTZ,

  error_code TEXT,
  error_message TEXT,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS jobs_idempotency_key_uq ON jobs (idempotency_key);
CREATE INDEX IF NOT EXISTS jobs_status_created_idx ON jobs (status, created_at);
CREATE INDEX IF NOT EXISTS jobs_lease_idx ON jobs (status, lease_until);

