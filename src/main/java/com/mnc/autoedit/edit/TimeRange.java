package com.mnc.autoedit.edit;

public record TimeRange(double startSec, double endSec) {
    public TimeRange {
        if (endSec < startSec) throw new IllegalArgumentException("endSec < startSec");
    }

    public double durationSec() {
        return endSec - startSec;
    }
}

