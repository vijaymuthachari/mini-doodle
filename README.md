# mini-doodle

A small meeting scheduling service. Users open up time slots in their calendar, others can be invited to meetings in those slots, and you can ask for anyone's free/busy time in a given window.

Built with Java 21, Spring Boot 3.5 and PostgreSQL.

## Run it

You only need Docker.

```bash
docker compose up --build
```

Then open:

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- Metrics (Prometheus format): http://localhost:8080/actuator/prometheus

If you also want Prometheus itself (on port 9090):

```bash
docker compose --profile monitoring up --build
```

To run the tests (Docker needs to be running, the tests start their own PostgreSQL with Testcontainers):

```bash
mvn verify
```

## Try it out

```bash
# create two users
curl -s -X POST localhost:8080/api/v1/users -H 'Content-Type: application/json' \
  -d '{"name":"Alice","email":"alice@example.com"}'
curl -s -X POST localhost:8080/api/v1/users -H 'Content-Type: application/json' \
  -d '{"name":"Bob","email":"bob@example.com"}'

ALICE=<alice id>
BOB=<bob id>

# Alice opens her morning in 30 minute slots
curl -s -X POST localhost:8080/api/v1/users/$ALICE/slots/batch -H 'Content-Type: application/json' \
  -d '{"from":"2026-11-10T09:00:00Z","to":"2026-11-10T12:00:00Z","durationMinutes":30}'

# book one of them and invite Bob
SLOT=<a slot id from the response>
curl -s -X POST localhost:8080/api/v1/users/$ALICE/slots/$SLOT/meeting -H 'Content-Type: application/json' \
  -d "{\"title\":\"Sprint planning\",\"participantIds\":[\"$BOB\"]}"

# free/busy view for the morning
curl -s "localhost:8080/api/v1/users/$ALICE/availability?from=2026-11-10T08:00:00Z&to=2026-11-10T13:00:00Z"
```

## API

| Method | Path | What it does |
|---|---|---|
| POST | `/api/v1/users` | Create a user |
| GET | `/api/v1/users/{userId}` | Get a user |
| POST | `/api/v1/users/{userId}/slots` | Create one slot (start time + duration) |
| POST | `/api/v1/users/{userId}/slots/batch` | Fill a time range with back-to-back slots |
| GET | `/api/v1/users/{userId}/slots?from=&to=&status=` | List slots in a window (paged) |
| PATCH | `/api/v1/users/{userId}/slots/{slotId}` | Move or resize a slot, or mark it FREE/BUSY |
| DELETE | `/api/v1/users/{userId}/slots/{slotId}` | Delete a slot |
| POST | `/api/v1/users/{userId}/slots/{slotId}/meeting` | Book a free slot as a meeting |
| GET | `/api/v1/meetings/{meetingId}` | Get a meeting |
| DELETE | `/api/v1/meetings/{meetingId}` | Cancel a meeting, the slot becomes free again |
| GET | `/api/v1/users/{userId}/availability?from=&to=` | Free and busy time in a window |

All times are UTC in ISO-8601 (`2026-11-10T09:00:00Z`). Errors come back as standard problem details (RFC 7807): 400 for bad input, 404 for unknown ids, 409 for conflicts like overlapping slots or a slot that is already booked.

## How it's modelled

```
User 1 --- 1 Calendar 1 --- * TimeSlot 1 --- 0..1 Meeting * --- * User (participants)
```

- Each user has exactly one calendar. The task says the calendar should only exist in the domain, so the API never exposes it: you work with slots through the user (`/users/{id}/slots`).
- A slot is a time range with a status, FREE or BUSY. A user can mark a slot BUSY by hand (for example to block time) without a meeting.
- A meeting is a booked slot. Booking turns the slot BUSY and cancelling turns it FREE again. A slot that has a meeting can't be moved, freed or deleted until the meeting is cancelled.

**Availability.** Busy time is the user's own BUSY slots plus any meeting they are invited to in someone else's calendar. Free time is their FREE slots minus anything busy. Neighbouring ranges are merged, so six free 30 minute slots come back as one 09:00 to 12:00 block. Time where the user has no slots at all is neither free nor busy, since they haven't offered it.

## Decisions worth mentioning

- **No overlapping slots, enforced by PostgreSQL.** An exclusion constraint on `(calendar_id, time range)` makes overlaps impossible even if two requests come in at the same moment. Checking only in Java would leave a race between the check and the insert. Slots that just touch (09:00 to 09:30 and 09:30 to 10:00) are fine.
- **No double booking.** Slots have a version column (optimistic locking), so if two people book the same slot at once only one succeeds and the other gets a 409. The unique constraint on `meetings.slot_id` backs this up.
- **Built for range queries.** Almost every read is "slots of calendar X between A and B", so there's an index on `(calendar_id, start_time, end_time)`. Slot lists are paged and the availability window is capped at 31 days so a single request can't pull everything.
- **Kept simple on purpose.** For hundreds of users and thousands of slots one PostgreSQL instance is plenty. I didn't add a cache or a message broker because they'd add moving parts without a real gain at this size. If it grew a lot, the first steps would be read replicas for availability queries and partitioning slots by time.
- **Flyway owns the schema.** Hibernate only validates it, so the entities can't silently drift from the database.
- **Tests run against real PostgreSQL** (Testcontainers), because the overlap rule lives in the database and an in-memory DB wouldn't test it.
- **Metrics.** Actuator and Micrometer expose the usual HTTP, JVM and connection pool metrics, plus counters for slots created and meetings booked and cancelled.

## Not done (yet)

- Authentication. Right now any caller can act for any user.
- Time zones per user and recurring slots.
- Notifying participants, and checking participants' own calendars for clashes when booking.
