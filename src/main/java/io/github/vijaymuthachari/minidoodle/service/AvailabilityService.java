package io.github.vijaymuthachari.minidoodle.service;

import io.github.vijaymuthachari.minidoodle.api.dto.Dtos.AvailabilityResponse;
import io.github.vijaymuthachari.minidoodle.api.dto.Dtos.Interval;
import io.github.vijaymuthachari.minidoodle.domain.Calendar;
import io.github.vijaymuthachari.minidoodle.domain.TimeRange;
import io.github.vijaymuthachari.minidoodle.domain.TimeSlot;
import io.github.vijaymuthachari.minidoodle.repository.MeetingRepository;
import io.github.vijaymuthachari.minidoodle.repository.TimeSlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Builds the free/busy view of a user for a time window.
 *
 * Busy = the user's own BUSY slots plus meetings in other calendars the user is invited to.
 * Free = the user's own FREE slots minus anything busy.
 * Time without any slot is neither free nor busy (the user hasn't offered it).
 */
@Service
public class AvailabilityService {

    static final Duration MAX_WINDOW = Duration.ofDays(31);

    private final UserService userService;
    private final TimeSlotRepository slots;
    private final MeetingRepository meetings;

    public AvailabilityService(UserService userService, TimeSlotRepository slots, MeetingRepository meetings) {
        this.userService = userService;
        this.slots = slots;
        this.meetings = meetings;
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse availability(UUID userId, Instant from, Instant to) {
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("'to' must be after 'from'");
        }
        if (Duration.between(from, to).compareTo(MAX_WINDOW) > 0) {
            throw new IllegalArgumentException("Time window can be at most 31 days");
        }
        Calendar calendar = userService.findCalendar(userId);

        List<TimeRange> free = new ArrayList<>();
        List<TimeRange> busy = new ArrayList<>();
        for (TimeSlot slot : slots.findAllInRange(calendar.getId(), from, to)) {
            TimeRange range = new TimeRange(slot.getStartTime(), slot.getEndTime());
            (slot.isFree() ? free : busy).add(range);
        }
        busy.addAll(meetings.findParticipantBusyRanges(userId, from, to));

        List<TimeRange> busyMerged = clipAll(Intervals.merge(busy), from, to);
        List<TimeRange> freeMerged = clipAll(Intervals.subtract(free, busyMerged), from, to);

        return new AvailabilityResponse(userId, from, to, toIntervals(freeMerged), toIntervals(busyMerged));
    }

    private static List<TimeRange> clipAll(List<TimeRange> ranges, Instant from, Instant to) {
        return ranges.stream().map(r -> Intervals.clip(r, from, to)).filter(Objects::nonNull).toList();
    }

    private static List<Interval> toIntervals(List<TimeRange> ranges) {
        return ranges.stream().map(Interval::from).toList();
    }
}
