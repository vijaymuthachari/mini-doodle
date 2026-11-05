-- btree_gist lets a GiST exclusion constraint combine "=" on calendar_id
-- with "&&" (overlap) on the slot's time range.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE users (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email)
);

-- One personal calendar per user. The calendar is a domain concept only:
-- it is not exposed as its own resource in the API.
CREATE TABLE calendars (
    id          UUID        PRIMARY KEY,
    owner_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_calendars_owner UNIQUE (owner_id)
);

CREATE TABLE time_slots (
    id           UUID        PRIMARY KEY,
    calendar_id  UUID        NOT NULL REFERENCES calendars (id) ON DELETE CASCADE,
    start_time   TIMESTAMPTZ NOT NULL,
    end_time     TIMESTAMPTZ NOT NULL,
    status       VARCHAR(10) NOT NULL,
    version      BIGINT      NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_time_slots_range  CHECK (end_time > start_time),
    CONSTRAINT chk_time_slots_status CHECK (status IN ('FREE', 'BUSY')),
    -- Slots in the same calendar may never overlap. Enforced by the database,
    -- so it also holds under concurrent writes. Ranges are half-open [start, end),
    -- so back-to-back slots (09:00-09:30, 09:30-10:00) are allowed.
    CONSTRAINT ex_time_slots_no_overlap EXCLUDE USING gist (
        calendar_id WITH =,
        tstzrange(start_time, end_time, '[)') WITH &&
    )
);

-- Range queries ("slots of calendar X between A and B", ordered by start).
CREATE INDEX idx_time_slots_calendar_start ON time_slots (calendar_id, start_time, end_time);

-- A meeting is a booked slot: at most one meeting per slot.
CREATE TABLE meetings (
    id            UUID          PRIMARY KEY,
    slot_id       UUID          NOT NULL REFERENCES time_slots (id) ON DELETE RESTRICT,
    organizer_id  UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title         VARCHAR(200)  NOT NULL,
    description   VARCHAR(2000),
    created_at    TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_meetings_slot UNIQUE (slot_id)
);

CREATE INDEX idx_meetings_organizer ON meetings (organizer_id);

CREATE TABLE meeting_participants (
    meeting_id  UUID NOT NULL REFERENCES meetings (id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (meeting_id, user_id)
);

-- "Which meetings is this user invited to?" (used for the participant's busy view).
CREATE INDEX idx_meeting_participants_user ON meeting_participants (user_id);
