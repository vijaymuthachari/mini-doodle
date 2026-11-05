package io.github.vijaymuthachari.minidoodle.repository;

import io.github.vijaymuthachari.minidoodle.domain.Calendar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CalendarRepository extends JpaRepository<Calendar, UUID> {

    Optional<Calendar> findByOwnerId(UUID ownerId);
}
