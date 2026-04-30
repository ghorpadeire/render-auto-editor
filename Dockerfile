FROM maven:3.9.9-eclipse-temurin-17 AS build-java
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM debian:bookworm AS build-whisper
RUN apt-get update && apt-get install -y --no-install-recommends \
    git build-essential cmake ca-certificates && rm -rf /var/lib/apt/lists/*
WORKDIR /w
RUN git clone --depth 1 https://github.com/ggml-org/whisper.cpp.git .
RUN cmake -S . -B build -DWHISPER_BUILD_TESTS=OFF -DWHISPER_BUILD_EXAMPLES=ON
RUN cmake --build build -j 2

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates curl ffmpeg && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build-java /app/target/render-auto-editor-0.1.0.jar /app/app.jar
COPY --from=build-whisper /w/build/bin/whisper-cli /usr/local/bin/whisper-cli

ENV WHISPER_MODEL_PATH=/opt/models/ggml-base.bin
ENV WHISPER_MODEL_URL=https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin

COPY docker/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]

