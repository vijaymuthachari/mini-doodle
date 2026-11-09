package io.github.vijaymuthachari.minidoodle.service;

import io.github.vijaymuthachari.minidoodle.domain.TimeRange;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Small helpers for working with lists of time ranges.
 */
final class Intervals {

    private Intervals() {
    }

    /** Sorts and joins ranges that overlap or touch, e.g. 9-10 and 10-11 become 9-11. */
    static List<TimeRange> merge(List<TimeRange> ranges) {
        List<TimeRange> sorted = new ArrayList<>(ranges);
        sorted.sort(Comparator.comparing(TimeRange::start));

        List<TimeRange> result = new ArrayList<>();
        for (TimeRange next : sorted) {
            if (!result.isEmpty()) {
                TimeRange last = result.get(result.size() - 1);
                if (!next.start().isAfter(last.end())) {
                    Instant end = next.end().isAfter(last.end()) ? next.end() : last.end();
                    result.set(result.size() - 1, new TimeRange(last.start(), end));
                    continue;
                }
            }
            result.add(next);
        }
        return result;
    }

    /** Removes every part of {@code ranges} that is covered by {@code toRemove}. */
    static List<TimeRange> subtract(List<TimeRange> ranges, List<TimeRange> toRemove) {
        List<TimeRange> remaining = merge(ranges);
        for (TimeRange cut : merge(toRemove)) {
            List<TimeRange> next = new ArrayList<>();
            for (TimeRange range : remaining) {
                if (!range.overlaps(cut)) {
                    next.add(range);
                    continue;
                }
                if (range.start().isBefore(cut.start())) {
                    next.add(new TimeRange(range.start(), cut.start()));
                }
                if (range.end().isAfter(cut.end())) {
                    next.add(new TimeRange(cut.end(), range.end()));
                }
            }
            remaining = next;
        }
        return remaining;
    }

    /** Cuts a range down to the window, or returns null if it lies outside. */
    static TimeRange clip(TimeRange range, Instant from, Instant to) {
        Instant start = range.start().isBefore(from) ? from : range.start();
        Instant end = range.end().isAfter(to) ? to : range.end();
        return end.isAfter(start) ? new TimeRange(start, end) : null;
    }
}
