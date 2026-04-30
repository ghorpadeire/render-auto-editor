package com.mnc.autoedit.tools;

import com.mnc.autoedit.edit.CutPlan;
import com.mnc.autoedit.edit.TimeRange;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class FfmpegService {
    private final ToolConfig cfg;
    private final ProcessRunner runner;

    public FfmpegService(ToolConfig config, ProcessRunner processRunner) {
        this.cfg = config;
        this.runner = processRunner;
    }

    public Path extractAudioWav16kMono(Path inputMp4, Path outWav) throws Exception {
        List<String> cmd = List.of(
                cfg.getFfmpegPath(),
                "-y",
                "-i", inputMp4.toString(),
                "-vn",
                "-ac", "1",
                "-ar", "16000",
                "-f", "wav",
                outWav.toString()
        );
        ProcessRunner.ProcessResult r = runner.run(cmd, Duration.ofMinutes(10));
        if (r.exitCode() != 0) {
            throw new RuntimeException("ffmpeg extract failed: " + r.output());
        }
        return outWav;
    }

    public Path renderFromCutPlan(Path inputMp4, CutPlan plan, Path outputMp4) throws Exception {
        Path concatFile = outputMp4.getParent().resolve("concat.txt");
        writeConcatDemuxerFile(concatFile, inputMp4, plan);

        List<String> cmd = new ArrayList<>();
        cmd.add(cfg.getFfmpegPath());
        cmd.add("-y");
        cmd.add("-f");
        cmd.add("concat");
        cmd.add("-safe");
        cmd.add("0");
        cmd.add("-i");
        cmd.add(concatFile.toString());
        cmd.add("-c:v");
        cmd.add("libx264");
        cmd.add("-preset");
        cmd.add("veryfast");
        cmd.add("-crf");
        cmd.add("23");
        cmd.add("-c:a");
        cmd.add("aac");
        cmd.add("-b:a");
        cmd.add("128k");
        cmd.add(outputMp4.toString());

        ProcessRunner.ProcessResult r = runner.run(cmd, Duration.ofMinutes(60));
        if (r.exitCode() != 0) {
            throw new RuntimeException("ffmpeg render failed: " + r.output());
        }
        return outputMp4;
    }

    private static void writeConcatDemuxerFile(Path concatFile, Path inputMp4, CutPlan plan) throws Exception {
        String src = inputMp4.toAbsolutePath().toString().replace("\\", "/");
        String escaped = src.replace("'", "'\\''");
        StringBuilder sb = new StringBuilder();

        for (TimeRange r : plan.keepRanges()) {
            sb.append("file '").append(escaped).append("'\n");
            sb.append("inpoint ").append(String.format(java.util.Locale.ROOT, "%.3f", r.startSec())).append("\n");
            sb.append("outpoint ").append(String.format(java.util.Locale.ROOT, "%.3f", r.endSec())).append("\n");
        }

        Files.writeString(concatFile, sb.toString(), StandardCharsets.UTF_8);
    }
}

