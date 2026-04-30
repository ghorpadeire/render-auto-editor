package com.mnc.autoedit.tools;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tools")
public class ToolConfig {
    private String ffmpegPath = "ffmpeg";
    private String whisperPath = "whisper-cli";
    private String whisperModelPath = "/opt/models/ggml-base.bin";

    public String getFfmpegPath() { return ffmpegPath; }
    public void setFfmpegPath(String ffmpegPath) { this.ffmpegPath = ffmpegPath; }
    public String getWhisperPath() { return whisperPath; }
    public void setWhisperPath(String whisperPath) { this.whisperPath = whisperPath; }
    public String getWhisperModelPath() { return whisperModelPath; }
    public void setWhisperModelPath(String whisperModelPath) { this.whisperModelPath = whisperModelPath; }
}

