package io.github.vijaymuthachari.minidoodle.repository;

import io.github.vijaymuthachari.minidoodle.domain.SlotStatus;
import io.github.vijaymuthachari.minidoodle.domain.TimeSlot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, UUID> {

    Optional<TimeSlot> findByIdAndCalendarId(UUID id, UUID calendarId);

    /**
     * Paged slots of one calendar that overlap [from, to). Served by idx_time_slots_calendar_start.
     */
    @Query("""
            select s from TimeSlot s
            where s.calendar.id = :calendarId
              and s.startTime < :to and s.endTime > :from
            order by s.startTime
            """)
    Page<TimeSlot> findInRange(@Param("calendarId") UUID calendarId,
                               @Param("from") Instant from,
                               @Param("to") Instant to,
                               Pageable pageable);

    /**
     * Same as {@link #findInRange} but restricted to one status (FREE or BUSY).
     */
    @Query("""
            select s from TimeSlot s
            where s.calendar.id = :calendarId
              and s.startTime < :to and s.endTime > :from
              and s.status = :status
            order by s.startTime
            """)
    Page<TimeSlot> findInRangeWithStatus(@Param("calendarId") UUID calendarId,
                                         @Param("from") Instant from,
                                         @Param("to") Instant to,
                                         @Param("status") SlotStatus status,
                                         Pageable pageable);

    /**
     * All slots of one calendar overlapping [from, to), ordered by start.
     * Used to build the aggregated free/busy view; callers must bound the window.
     */
    @Query("""
            select s from TimeSlot s
            where s.calendar.id = :calendarId
              and s.startTime < :to and s.endTime > :from
            order by s.startTime
            """)
    List<TimeSlot> findAllInRange(@Param("calendarId") UUID calendarId,
                                  @Param("from") Instant from,
                                  @Param("to") Instant to);
}
