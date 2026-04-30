package com.mnc.autoedit.edit;

import java.util.List;

public record CutPlan(
        List<TimeRange> keepRanges
) {}

