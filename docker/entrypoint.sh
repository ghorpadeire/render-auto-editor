#!/usr/bin/env sh
set -eu

if [ "${APP_ROLE:-api}" = "worker" ]; then
  mkdir -p "$(dirname "${WHISPER_MODEL_PATH}")"
  if [ ! -f "${WHISPER_MODEL_PATH}" ]; then
    echo "Downloading whisper model to ${WHISPER_MODEL_PATH} ..."
    curl -L --fail "${WHISPER_MODEL_URL}" -o "${WHISPER_MODEL_PATH}"
  fi
fi

exec java -jar /app/app.jar

