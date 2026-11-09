package io.github.vijaymuthachari.minidoodle.service;

import io.github.vijaymuthachari.minidoodle.api.dto.Dtos.CreateSlotRequest;
import io.github.vijaymuthachari.minidoodle.api.dto.Dtos.CreateSlotsRequest;
import io.github.vijaymuthachari.minidoodle.api.dto.Dtos.PageResponse;
import io.github.vijaymuthachari.minidoodle.api.dto.Dtos.SlotResponse;
import io.github.vijaymuthachari.minidoodle.api.dto.Dtos.UpdateSlotRequest;
import io.github.vijaymuthachari.minidoodle.domain.Calendar;
import io.github.vijaymuthachari.minidoodle.domain.SlotStatus;
import io.github.vijaymuthachari.minidoodle.domain.TimeSlot;
import io.github.vijaymuthachari.minidoodle.exception.ConflictException;
import io.github.vijaymuthachari.minidoodle.exception.NotFoundException;
import io.github.vijaymuthachari.minidoodle.repository.MeetingRepository;
import io.github.vijaymuthachari.minidoodle.repository.TimeSlotRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SlotService {

    static final int MAX_SLOTS_PER_REQUEST = 500;

    private final UserService userService;
    private final TimeSlotRepository slots;
    private final MeetingRepository meetings;
    private final Counter slotsCreated;

    public SlotService(UserService userService, TimeSlotRepository slots, MeetingRepository meetings,
                       MeterRegistry registry) {
        this.userService = userService;
        this.slots = slots;
        this.meetings = meetings;
        this.slotsCreated = Counter.builder("minidoodle.slots.created").register(registry);
    }

    @Transactional
    public SlotResponse create(UUID userId, CreateSlotRequest request) {
        Calendar calendar = userService.findCalendar(userId);
        TimeSlot slot = new TimeSlot(calendar, request.startTime(), Duration.ofMinutes(request.durationMinutes()));
        // flush now so an overlap is reported here as a 409, not at commit time
        slots.saveAndFlush(slot);
        slotsCreated.increment();
        return SlotResponse.from(slot);
    }

    /** Fills [from, to) with back-to-back slots. All or nothing: one overlap fails the whole request. */
    @Transactional
    public List<SlotResponse> createMany(UUID userId, CreateSlotsRequest request) {
        if (!request.to().isAfter(request.from())) {
            throw new IllegalArgumentException("'to' must be after 'from'");
        }
        Calendar calendar = userService.findCalendar(userId);
        Duration duration = Duration.ofMinutes(request.durationMinutes());

        List<TimeSlot> created = new ArrayList<>();
        for (Instant start = request.from(); !start.plus(duration).isAfter(request.to()); start = start.plus(duration)) {
            if (created.size() == MAX_SLOTS_PER_REQUEST) {
                throw new IllegalArgumentException("At most " + MAX_SLOTS_PER_REQUEST + " slots per request");
            }
            created.add(new TimeSlot(calendar, start, duration));
        }
        slots.saveAllAndFlush(created);
        slotsCreated.increment(created.size());
        return created.stream().map(SlotResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<SlotResponse> list(UUID userId, Instant from, Instant to, SlotStatus status,
                                           int page, int size) {
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("'to' must be after 'from'");
        }
        Calendar calendar = userService.findCalendar(userId);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
        Page<TimeSlot> result = status == null
                ? slots.findInRange(calendar.getId(), from, to, pageRequest)
                : slots.findInRangeWithStatus(calendar.getId(), from, to, status, pageRequest);
        return PageResponse.from(result.map(SlotResponse::from));
    }

    @Transactional
    public SlotResponse update(UUID userId, UUID slotId, UpdateSlotRequest request) {
        TimeSlot slot = findSlot(userId, slotId);
        boolean changesTime = request.startTime() != null || request.durationMinutes() != null;

        if (isBooked(slot) && (changesTime || request.status() == SlotStatus.FREE)) {
            throw new ConflictException("Slot has a meeting, cancel the meeting first");
        }
        if (changesTime) {
            Instant start = request.startTime() != null ? request.startTime() : slot.getStartTime();
            Duration duration = request.durationMinutes() != null
                    ? Duration.ofMinutes(request.durationMinutes())
                    : slot.getDuration();
            slot.reschedule(start, duration);
        }
        if (request.status() == SlotStatus.BUSY) {
            slot.markBusy();
        } else if (request.status() == SlotStatus.FREE) {
            slot.markFree();
        }
        slots.saveAndFlush(slot);
        return SlotResponse.from(slot);
    }

    @Transactional
    public void delete(UUID userId, UUID slotId) {
        TimeSlot slot = findSlot(userId, slotId);
        if (isBooked(slot)) {
            throw new ConflictException("Slot has a meeting, cancel the meeting first");
        }
        slots.delete(slot);
    }

    TimeSlot findSlot(UUID userId, UUID slotId) {
        Calendar calendar = userService.findCalendar(userId);
        return slots.findByIdAndCalendarId(slotId, calendar.getId())
                .orElseThrow(() -> new NotFoundException("Slot " + slotId + " not found"));
    }

    private boolean isBooked(TimeSlot slot) {
        return meetings.existsBySlotId(slot.getId());
    }
}
