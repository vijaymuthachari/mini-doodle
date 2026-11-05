package io.github.vijaymuthachari.minidoodle.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A block of time in a calendar, half-open [startTime, endTime).
 * Non-overlap within a calendar is enforced by a database exclusion constraint;
 * concurrent modifications are detected through optimistic locking.
 */
@Entity
@Table(name = "time_slots")
public class TimeSlot {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calendar_id", nullable = false, updatable = false)
    private Calendar calendar;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SlotStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TimeSlot() {
        // for JPA
    }

    public TimeSlot(Calendar calendar, Instant startTime, Duration duration) {
        this.id = UUID.randomUUID();
        this.calendar = Objects.requireNonNull(calendar);
        setTimes(startTime, duration);
        this.status = SlotStatus.FREE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void reschedule(Instant newStart, Duration newDuration) {
        setTimes(newStart, newDuration);
    }

    public void markBusy() {
        this.status = SlotStatus.BUSY;
    }

    public void markFree() {
        this.status = SlotStatus.FREE;
    }

    public boolean isFree() {
        return status == SlotStatus.FREE;
    }

    private void setTimes(Instant start, Duration duration) {
        Objects.requireNonNull(start, "startTime");
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("Slot duration must be positive");
        }
        this.startTime = start;
        this.endTime = start.plus(duration);
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Calendar getCalendar() {
        return calendar;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public Duration getDuration() {
        return Duration.between(startTime, endTime);
    }

    public SlotStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimeSlot other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
