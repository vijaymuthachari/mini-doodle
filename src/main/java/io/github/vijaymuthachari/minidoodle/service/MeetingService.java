package io.github.vijaymuthachari.minidoodle.service;

import io.github.vijaymuthachari.minidoodle.api.dto.Dtos.BookMeetingRequest;
import io.github.vijaymuthachari.minidoodle.api.dto.Dtos.MeetingResponse;
import io.github.vijaymuthachari.minidoodle.domain.Meeting;
import io.github.vijaymuthachari.minidoodle.domain.TimeSlot;
import io.github.vijaymuthachari.minidoodle.domain.User;
import io.github.vijaymuthachari.minidoodle.exception.ConflictException;
import io.github.vijaymuthachari.minidoodle.exception.NotFoundException;
import io.github.vijaymuthachari.minidoodle.repository.MeetingRepository;
import io.github.vijaymuthachari.minidoodle.repository.TimeSlotRepository;
import io.github.vijaymuthachari.minidoodle.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class MeetingService {

    private final UserService userService;
    private final SlotService slotService;
    private final UserRepository users;
    private final TimeSlotRepository slots;
    private final MeetingRepository meetings;
    private final Counter booked;
    private final Counter cancelled;

    public MeetingService(UserService userService, SlotService slotService, UserRepository users,
                          TimeSlotRepository slots, MeetingRepository meetings, MeterRegistry registry) {
        this.userService = userService;
        this.slotService = slotService;
        this.users = users;
        this.slots = slots;
        this.meetings = meetings;
        this.booked = Counter.builder("minidoodle.meetings.booked").register(registry);
        this.cancelled = Counter.builder("minidoodle.meetings.cancelled").register(registry);
    }

    /**
     * Books a free slot of the user's calendar as a meeting; the user is the organizer.
     * If two requests try to book the same slot at once, the version check on the slot
     * lets only one of them win (the other gets a 409).
     */
    @Transactional
    public MeetingResponse book(UUID userId, UUID slotId, BookMeetingRequest request) {
        User organizer = userService.findUser(userId);
        TimeSlot slot = slotService.findSlot(userId, slotId);
        if (!slot.isFree()) {
            throw new ConflictException("Slot is not free");
        }

        List<User> participants = loadParticipants(request.participantIds());

        slot.markBusy();
        slots.saveAndFlush(slot);
        Meeting meeting = meetings.saveAndFlush(
                new Meeting(slot, organizer, request.title(), request.description(), participants));
        booked.increment();
        return MeetingResponse.from(meeting);
    }

    @Transactional(readOnly = true)
    public MeetingResponse get(UUID meetingId) {
        return MeetingResponse.from(findMeeting(meetingId));
    }

    /** Cancelling a meeting makes its slot free again. */
    @Transactional
    public void cancel(UUID meetingId) {
        Meeting meeting = findMeeting(meetingId);
        TimeSlot slot = meeting.getSlot();
        meetings.delete(meeting);
        meetings.flush();
        slot.markFree();
        slots.saveAndFlush(slot);
        cancelled.increment();
    }

    private Meeting findMeeting(UUID meetingId) {
        return meetings.findDetailedById(meetingId)
                .orElseThrow(() -> new NotFoundException("Meeting " + meetingId + " not found"));
    }

    private List<User> loadParticipants(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<UUID> unique = new HashSet<>(ids);
        List<User> found = users.findAllById(unique);
        if (found.size() != unique.size()) {
            Set<UUID> missing = new HashSet<>(unique);
            found.forEach(u -> missing.remove(u.getId()));
            throw new NotFoundException("Unknown participants: " + missing);
        }
        return found;
    }
}
