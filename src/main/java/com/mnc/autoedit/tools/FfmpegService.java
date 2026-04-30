package com.mnc.autoedit.tools;

import com.mnc.autoedit.edit.CutPlan;
import com.mnc.autoedit.edit.TimeRange;
import org.springframework.stereotype.Service;

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
        List<String> cmd = new ArrayList<>();
        cmd.add(cfg.getFfmpegPath());
        cmd.add("-y");
        cmd.add("-i");
        cmd.add(inputMp4.toString());

        String filter = buildTrimConcatFilter(plan);
        cmd.add("-filter_complex");
        cmd.add(filter);
        cmd.add("-map");
        cmd.add("[v]");
        cmd.add("-map");
        cmd.add("[a]");
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

    private static String buildTrimConcatFilter(CutPlan plan) {
        if (plan.keepRanges() == null || plan.keepRanges().isEmpty()) {
            // Produce a 0.5s black/silent clip to avoid ffmpeg errors (edge case).
            return "color=c=black:s=1280x720:d=0.5[v];anullsrc=r=48000:cl=stereo:d=0.5[a]";
        }

        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (TimeRange r : plan.keepRanges()) {
            String vs = String.format(java.util.Locale.ROOT, "%.3f", r.startSec());
            String ve = String.format(java.util.Locale.ROOT, "%.3f", r.endSec());
            sb.append("[0:v]trim=start=").append(vs).append(":end=").append(ve).append(",setpts=PTS-STARTPTS[v")
                    .append(i).append("];");
            sb.append("[0:a]atrim=start=").append(vs).append(":end=").append(ve).append(",asetpts=PTS-STARTPTS[a")
                    .append(i).append("];");
            i++;
        }

        for (int j = 0; j < i; j++) {
            sb.append("[v").append(j).append("][a").append(j).append("]");
        }
        sb.append("concat=n=").append(i).append(":v=1:a=1[v][a]");
        return sb.toString();
    }
}

