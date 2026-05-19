#!/usr/bin/env sh
set -eu

# Map Render's DATABASE_URL (postgres://user:pass@host:port/db)
# to the JDBC_DATABASE_* vars our Spring config expects.
if [ -n "${DATABASE_URL:-}" ] && [ -z "${JDBC_DATABASE_URL:-}" ]; then
  proto_stripped=$(printf '%s' "$DATABASE_URL" | sed -E 's#^[a-zA-Z]+://##')
  creds=$(printf '%s' "$proto_stripped" | sed -E 's#@.*##')
  hostpart=$(printf '%s' "$proto_stripped" | sed -E 's#^[^@]*@##')
  user=$(printf '%s' "$creds" | sed -E 's#:.*##')
  pass=$(printf '%s' "$creds" | sed -E 's#^[^:]*:##')
  host_port_db=$(printf '%s' "$hostpart" | sed -E 's#\?.*##')
  export JDBC_DATABASE_URL="jdbc:postgresql://${host_port_db}"
  export JDBC_DATABASE_USERNAME="$user"
  export JDBC_DATABASE_PASSWORD="$pass"
fi

# Auto-activate "nodb" profile when no database is configured,
# so the app boots cleanly on Render without Postgres provisioned.
if [ -z "${JDBC_DATABASE_URL:-}" ] && [ -z "${DATABASE_URL:-}" ]; then
  if [ -z "${SPRING_PROFILES_ACTIVE:-}" ]; then
    export SPRING_PROFILES_ACTIVE="nodb"
    echo "No DATABASE_URL or JDBC_DATABASE_URL found — activating 'nodb' profile."
  fi
fi

# The synchronous /v1/transcribe API also needs the local whisper model,
# so ensure it is present for every role, not just the background worker.
mkdir -p "$(dirname "${WHISPER_MODEL_PATH}")"
if [ ! -f "${WHISPER_MODEL_PATH}" ]; then
  echo "Downloading whisper model to ${WHISPER_MODEL_PATH} ..."
  curl -L --fail "${WHISPER_MODEL_URL}" -o "${WHISPER_MODEL_PATH}"
fi

exec java -jar /app/app.jar
