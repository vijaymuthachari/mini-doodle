package io.github.vijaymuthachari.minidoodle.api.dto;

import io.github.vijaymuthachari.minidoodle.domain.Meeting;
import io.github.vijaymuthachari.minidoodle.domain.SlotStatus;
import io.github.vijaymuthachari.minidoodle.domain.TimeRange;
import io.github.vijaymuthachari.minidoodle.domain.TimeSlot;
import io.github.vijaymuthachari.minidoodle.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Request and response bodies of the REST API, kept in one place.
 */
public final class Dtos {

    private Dtos() {
    }

    // ---- requests ----

    public record CreateUserRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 255) String email) {
    }

    public record CreateSlotRequest(
            @NotNull Instant startTime,
            @NotNull @Positive @Max(1440) Integer durationMinutes) {
    }

    /** Creates back-to-back slots of the same length between from and to. */
    public record CreateSlotsRequest(
            @NotNull Instant from,
            @NotNull Instant to,
            @NotNull @Positive @Max(1440) Integer durationMinutes) {
    }

    /** All fields optional: only what is sent gets changed. */
    public record UpdateSlotRequest(
            Instant startTime,
            @Positive @Max(1440) Integer durationMinutes,
            SlotStatus status) {
    }

    public record BookMeetingRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            List<UUID> participantIds) {
    }

    // ---- responses ----

    public record UserResponse(UUID id, String name, String email, Instant createdAt) {
        public static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt());
        }
    }

    public record SlotResponse(UUID id, Instant startTime, Instant endTime, long durationMinutes,
                               SlotStatus status) {
        public static SlotResponse from(TimeSlot slot) {
            return new SlotResponse(slot.getId(), slot.getStartTime(), slot.getEndTime(),
                    slot.getDuration().toMinutes(), slot.getStatus());
        }
    }

    public record MeetingResponse(UUID id, UUID slotId, UUID organizerId, String title, String description,
                                  Instant startTime, Instant endTime, List<UUID> participantIds) {
        public static MeetingResponse from(Meeting meeting) {
            TimeSlot slot = meeting.getSlot();
            List<UUID> participants = meeting.getParticipants().stream().map(User::getId).sorted().toList();
            return new MeetingResponse(meeting.getId(), slot.getId(), meeting.getOrganizer().getId(),
                    meeting.getTitle(), meeting.getDescription(), slot.getStartTime(), slot.getEndTime(),
                    participants);
        }
    }

    public record Interval(Instant start, Instant end) {
        public static Interval from(TimeRange range) {
            return new Interval(range.start(), range.end());
        }
    }

    public record AvailabilityResponse(UUID userId, Instant from, Instant to,
                                       List<Interval> free, List<Interval> busy) {
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }
}
