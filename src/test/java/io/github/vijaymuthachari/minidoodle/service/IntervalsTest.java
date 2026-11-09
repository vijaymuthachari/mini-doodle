package io.github.vijaymuthachari.minidoodle.service;

import io.github.vijaymuthachari.minidoodle.domain.TimeRange;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntervalsTest {

    private static TimeRange range(String start, String end) {
        return new TimeRange(at(start), at(end));
    }

    private static Instant at(String hhmm) {
        return Instant.parse("2026-11-10T" + hhmm + ":00Z");
    }

    @Test
    void mergeJoinsTouchingAndOverlappingRanges() {
        List<TimeRange> merged = Intervals.merge(List.of(
                range("10:00", "10:30"),
                range("09:00", "09:30"),
                range("09:30", "10:00"),
                range("12:00", "13:00"),
                range("12:30", "12:45")));

        assertThat(merged).containsExactly(
                range("09:00", "10:30"),
                range("12:00", "13:00"));
    }

    @Test
    void subtractCutsOutBusyParts() {
        List<TimeRange> free = List.of(range("09:00", "12:00"));
        List<TimeRange> busy = List.of(range("10:00", "10:30"), range("11:30", "13:00"));

        assertThat(Intervals.subtract(free, busy)).containsExactly(
                range("09:00", "10:00"),
                range("10:30", "11:30"));
    }

    @Test
    void subtractRemovesFullyCoveredRange() {
        assertThat(Intervals.subtract(List.of(range("09:00", "10:00")), List.of(range("08:00", "11:00"))))
                .isEmpty();
    }

    @Test
    void clipKeepsOnlyThePartInsideTheWindow() {
        assertThat(Intervals.clip(range("08:00", "10:00"), at("09:00"), at("12:00")))
                .isEqualTo(range("09:00", "10:00"));
        assertThat(Intervals.clip(range("06:00", "07:00"), at("09:00"), at("12:00")))
                .isNull();
    }
}
