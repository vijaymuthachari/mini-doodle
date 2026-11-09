package io.github.vijaymuthachari.minidoodle.api;

import io.github.vijaymuthachari.minidoodle.api.dto.Dtos.BookMeetingRequest;
import io.github.vijaymuthachari.minidoodle.api.dto.Dtos.CreateSlotRequest;
import io.github.vijaymuthachari.minidoodle.api.dto.Dtos.CreateSlotsRequest;
import io.github.vijaymuthachari.minidoodle.api.dto.Dtos.MeetingResponse;
import io.github.vijaymuthachari.minidoodle.api.dto.Dtos.PageResponse;
import io.github.vijaymuthachari.minidoodle.api.dto.Dtos.SlotResponse;
import io.github.vijaymuthachari.minidoodle.api.dto.Dtos.UpdateSlotRequest;
import io.github.vijaymuthachari.minidoodle.domain.SlotStatus;
import io.github.vijaymuthachari.minidoodle.service.MeetingService;
import io.github.vijaymuthachari.minidoodle.service.SlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Slots live in the user's calendar, so they are addressed through the user.
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/slots")
@Tag(name = "Slots")
public class SlotController {

    private final SlotService slotService;
    private final MeetingService meetingService;

    public SlotController(SlotService slotService, MeetingService meetingService) {
        this.slotService = slotService;
        this.meetingService = meetingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a slot")
    public SlotResponse create(@PathVariable UUID userId, @Valid @RequestBody CreateSlotRequest request) {
        return slotService.create(userId, request);
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create back-to-back slots between 'from' and 'to'")
    public List<SlotResponse> createMany(@PathVariable UUID userId, @Valid @RequestBody CreateSlotsRequest request) {
        return slotService.createMany(userId, request);
    }

    @GetMapping
    @Operation(summary = "List slots in a time window, optionally by status")
    public PageResponse<SlotResponse> list(
            @PathVariable UUID userId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) SlotStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return slotService.list(userId, from, to, status, page, size);
    }

    @PatchMapping("/{slotId}")
    @Operation(summary = "Change a slot's time or duration, or mark it FREE / BUSY")
    public SlotResponse update(@PathVariable UUID userId, @PathVariable UUID slotId,
                               @Valid @RequestBody UpdateSlotRequest request) {
        return slotService.update(userId, slotId, request);
    }

    @DeleteMapping("/{slotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a slot (only if it has no meeting)")
    public void delete(@PathVariable UUID userId, @PathVariable UUID slotId) {
        slotService.delete(userId, slotId);
    }

    @PostMapping("/{slotId}/meeting")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Book a free slot as a meeting")
    public MeetingResponse book(@PathVariable UUID userId, @PathVariable UUID slotId,
                                @Valid @RequestBody BookMeetingRequest request) {
        return meetingService.book(userId, slotId, request);
    }
}
