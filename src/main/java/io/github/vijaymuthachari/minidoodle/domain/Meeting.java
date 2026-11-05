package io.github.vijaymuthachari.minidoodle.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A booked slot. Creating a meeting turns its slot BUSY; cancelling it frees the slot again.
 */
@Entity
@Table(name = "meetings")
public class Meeting {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id", nullable = false, unique = true, updatable = false)
    private TimeSlot slot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false, updatable = false)
    private User organizer;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "meeting_participants",
            joinColumns = @JoinColumn(name = "meeting_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<User> participants = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Meeting() {
        // for JPA
    }

    public Meeting(TimeSlot slot, User organizer, String title, String description,
                   Collection<User> participants) {
        this.id = UUID.randomUUID();
        this.slot = Objects.requireNonNull(slot);
        this.organizer = Objects.requireNonNull(organizer);
        this.title = Objects.requireNonNull(title);
        this.description = description;
        if (participants != null) {
            this.participants.addAll(participants);
        }
        this.createdAt = Instant.now();
    }

    public void updateDetails(String title, String description) {
        this.title = Objects.requireNonNull(title);
        this.description = description;
    }

    public void replaceParticipants(Collection<User> newParticipants) {
        this.participants.clear();
        this.participants.addAll(newParticipants);
    }

    public UUID getId() {
        return id;
    }

    public TimeSlot getSlot() {
        return slot;
    }

    public User getOrganizer() {
        return organizer;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Set<User> getParticipants() {
        return Collections.unmodifiableSet(participants);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Meeting other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
