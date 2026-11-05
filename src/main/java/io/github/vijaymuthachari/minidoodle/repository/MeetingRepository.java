package io.github.vijaymuthachari.minidoodle.repository;

import io.github.vijaymuthachari.minidoodle.domain.Meeting;
import io.github.vijaymuthachari.minidoodle.domain.TimeRange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeetingRepository extends JpaRepository<Meeting, UUID> {

    @Query("""
            select m from Meeting m
            join fetch m.slot
            join fetch m.organizer
            left join fetch m.participants
            where m.id = :id
            """)
    Optional<Meeting> findDetailedById(@Param("id") UUID id);

    Optional<Meeting> findBySlotId(UUID slotId);

    /**
     * Time ranges of meetings in [from, to) that the user is invited to as a participant.
     * These count as busy time in the user's aggregated availability.
     */
    @Query("""
            select new io.github.vijaymuthachari.minidoodle.domain.TimeRange(s.startTime, s.endTime)
            from Meeting m
            join m.participants p
            join m.slot s
            where p.id = :userId
              and s.startTime < :to and s.endTime > :from
            order by s.startTime
            """)
    List<TimeRange> findParticipantBusyRanges(@Param("userId") UUID userId,
                                              @Param("from") Instant from,
                                              @Param("to") Instant to);
}
