package io.github.vijaymuthachari.minidoodle.api;

import io.github.vijaymuthachari.minidoodle.api.dto.Dtos.MeetingResponse;
import io.github.vijaymuthachari.minidoodle.service.MeetingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/meetings")
@Tag(name = "Meetings")
public class MeetingController {

    private final MeetingService meetingService;

    public MeetingController(MeetingService meetingService) {
        this.meetingService = meetingService;
    }

    @GetMapping("/{meetingId}")
    @Operation(summary = "Get a meeting")
    public MeetingResponse get(@PathVariable UUID meetingId) {
        return meetingService.get(meetingId);
    }

    @DeleteMapping("/{meetingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancel a meeting, its slot becomes free again")
    public void cancel(@PathVariable UUID meetingId) {
        meetingService.cancel(meetingId);
    }
}
