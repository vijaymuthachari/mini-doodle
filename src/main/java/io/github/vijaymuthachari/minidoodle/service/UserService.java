package io.github.vijaymuthachari.minidoodle.service;

import io.github.vijaymuthachari.minidoodle.api.dto.Dtos.CreateUserRequest;
import io.github.vijaymuthachari.minidoodle.api.dto.Dtos.UserResponse;
import io.github.vijaymuthachari.minidoodle.domain.Calendar;
import io.github.vijaymuthachari.minidoodle.domain.User;
import io.github.vijaymuthachari.minidoodle.exception.ConflictException;
import io.github.vijaymuthachari.minidoodle.exception.NotFoundException;
import io.github.vijaymuthachari.minidoodle.repository.CalendarRepository;
import io.github.vijaymuthachari.minidoodle.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository users;
    private final CalendarRepository calendars;

    public UserService(UserRepository users, CalendarRepository calendars) {
        this.users = users;
        this.calendars = calendars;
    }

    /** Every new user gets their personal calendar right away. */
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String email = request.email().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ConflictException("A user with this email already exists");
        }
        User user = users.saveAndFlush(new User(request.name(), email));
        calendars.save(new Calendar(user));
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID userId) {
        return UserResponse.from(findUser(userId));
    }

    User findUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId + " not found"));
    }

    Calendar findCalendar(UUID userId) {
        return calendars.findByOwnerId(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId + " not found"));
    }
}
