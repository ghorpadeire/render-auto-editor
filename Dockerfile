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

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates curl ffmpeg && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build-java /app/target/render-auto-editor-0.1.0.jar /app/app.jar
COPY --from=build-whisper /w/build/bin/whisper-cli /usr/local/bin/whisper-cli
COPY --from=build-whisper /w/build/src/libwhisper.so.1.8.4 /usr/local/lib/libwhisper.so.1.8.4
COPY --from=build-whisper /w/build/ggml/src/libggml.so.0.9.8 /usr/local/lib/libggml.so.0.9.8
COPY --from=build-whisper /w/build/ggml/src/libggml-base.so.0.9.8 /usr/local/lib/libggml-base.so.0.9.8
COPY --from=build-whisper /w/build/ggml/src/libggml-cpu.so.0.9.8 /usr/local/lib/libggml-cpu.so.0.9.8

RUN ln -sf /usr/local/lib/libwhisper.so.1.8.4 /usr/local/lib/libwhisper.so.1 && \
    ln -sf /usr/local/lib/libggml.so.0.9.8 /usr/local/lib/libggml.so.0 && \
    ln -sf /usr/local/lib/libggml-base.so.0.9.8 /usr/local/lib/libggml-base.so.0 && \
    ln -sf /usr/local/lib/libggml-cpu.so.0.9.8 /usr/local/lib/libggml-cpu.so.0 && \
    ldconfig

ENV WHISPER_MODEL_PATH=/opt/models/ggml-base.bin
ENV WHISPER_MODEL_URL=https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin

COPY docker/entrypoint.sh /app/entrypoint.sh
RUN sed -i 's/\r$//' /app/entrypoint.sh && chmod +x /app/entrypoint.sh

EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]

