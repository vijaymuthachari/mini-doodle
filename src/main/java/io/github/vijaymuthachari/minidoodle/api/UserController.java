package io.github.vijaymuthachari.minidoodle.api;

import io.github.vijaymuthachari.minidoodle.api.dto.Dtos.AvailabilityResponse;
import io.github.vijaymuthachari.minidoodle.api.dto.Dtos.CreateUserRequest;
import io.github.vijaymuthachari.minidoodle.api.dto.Dtos.UserResponse;
import io.github.vijaymuthachari.minidoodle.service.AvailabilityService;
import io.github.vijaymuthachari.minidoodle.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
public class UserController {

    private final UserService userService;
    private final AvailabilityService availabilityService;

    public UserController(UserService userService, AvailabilityService availabilityService) {
        this.userService = userService;
        this.availabilityService = availabilityService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a user (a personal calendar is created with it)")
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get a user")
    public UserResponse get(@PathVariable UUID userId) {
        return userService.get(userId);
    }

    @GetMapping("/{userId}/availability")
    @Operation(summary = "Free and busy time of a user in a time window (max 31 days)")
    public AvailabilityResponse availability(
            @PathVariable UUID userId,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        return availabilityService.availability(userId, from, to);
    }
}
