package com.mnc.autoedit.edit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CutPlanner {

    public CutPlan planCuts(
            List<WordTimestamp> words,
            double pauseThresholdSec,
            double paddingSec,
            double minKeepSegmentSec,
            Set<String> fillerWordsLowerAlpha
    ) {
        if (words == null || words.isEmpty()) {
            return new CutPlan(List.of());
        }

        // Removal intervals: filler words and long pauses (gaps).
        List<TimeRange> remove = new ArrayList<>();

        for (int i = 0; i < words.size(); i++) {
            WordTimestamp w = words.get(i);
            String norm = normalizeToken(w.text());
            if (!norm.isEmpty() && fillerWordsLowerAlpha.contains(norm)) {
                double s = Math.max(0, w.startSec() - paddingSec);
                double e = Math.max(s, w.endSec() + paddingSec);
                remove.add(new TimeRange(s, e));
            }
        }

        for (int i = 0; i < words.size() - 1; i++) {
            WordTimestamp a = words.get(i);
            WordTimestamp b = words.get(i + 1);
            double gap = b.startSec() - a.endSec();
            if (gap >= pauseThresholdSec) {
                double s = Math.max(0, a.endSec() - Math.min(paddingSec, 0.05)); // keep breath; tiny overlap
                double e = Math.max(s, b.startSec() + Math.min(paddingSec, 0.05));
                remove.add(new TimeRange(s, e));
            }
        }

        List<TimeRange> mergedRemove = merge(remove);

        double startOfMedia = 0.0;
        double endOfMedia = Math.max(words.get(words.size() - 1).endSec(), words.get(words.size() - 1).startSec());

        List<TimeRange> keep = invertRanges(mergedRemove, startOfMedia, endOfMedia);
        keep = keep.stream().filter(r -> r.durationSec() >= minKeepSegmentSec).toList();
        return new CutPlan(keep);
    }

    public static Set<String> parseFillerSet(List<String> fillers) {
        Set<String> set = new HashSet<>();
        if (fillers == null) return set;
        for (String f : fillers) {
            String norm = normalizeToken(f);
            if (!norm.isEmpty()) set.add(norm);
        }
        return set;
    }

    private static List<TimeRange> invertRanges(List<TimeRange> remove, double start, double end) {
        List<TimeRange> keep = new ArrayList<>();
        double cursor = start;
        for (TimeRange r : remove) {
            double rs = clamp(r.startSec(), start, end);
            double re = clamp(r.endSec(), start, end);
            if (rs > cursor) {
                keep.add(new TimeRange(cursor, rs));
            }
            cursor = Math.max(cursor, re);
        }
        if (cursor < end) {
            keep.add(new TimeRange(cursor, end));
        }
        return keep;
    }

    private static List<TimeRange> merge(List<TimeRange> ranges) {
        if (ranges == null || ranges.isEmpty()) return List.of();

        List<TimeRange> sorted = new ArrayList<>(ranges);
        sorted.sort(Comparator.comparingDouble(TimeRange::startSec).thenComparingDouble(TimeRange::endSec));

        List<TimeRange> out = new ArrayList<>();
        TimeRange cur = sorted.get(0);
        for (int i = 1; i < sorted.size(); i++) {
            TimeRange n = sorted.get(i);
            if (n.startSec() <= cur.endSec()) {
                cur = new TimeRange(cur.startSec(), Math.max(cur.endSec(), n.endSec()));
            } else {
                out.add(cur);
                cur = n;
            }
        }
        out.add(cur);
        return out;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static String normalizeToken(String token) {
        if (token == null) return "";
        String s = token.toLowerCase(Locale.ROOT).trim();
        s = s.replaceAll("[^a-z]", "");
        return s;
    }
}

