# mini-doodle

A meeting scheduling service built with Spring Boot: users manage time slots in their personal calendar, book slots as meetings with participants, and query an aggregated free/busy view for any time frame.

> Status: project scaffold. Domain model, database schema and runtime setup are in place; the REST API, services and tests are being added in the next commits.

## Tech stack

| Concern | Choice |
|---|---|
| Runtime | Java 21, Spring Boot 3.5 |
| Persistence | PostgreSQL 16, Spring Data JPA, Flyway migrations |
| API docs | springdoc-openapi (Swagger UI) |
| Metrics | Spring Boot Actuator, Micrometer, Prometheus endpoint |
| Tests | JUnit 5, Testcontainers (real PostgreSQL) |
| Packaging | Multi-stage Docker build, docker-compose |

## Running locally

Requirements: Docker with Compose v2.

```bash
docker compose up --build
```

| What | URL |
|---|---|
| API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI spec | http://localhost:8080/v3/api-docs |
| Health | http://localhost:8080/actuator/health |
| Prometheus metrics | http://localhost:8080/actuator/prometheus |

With Prometheus as well (UI on http://localhost:9090):

```bash
docker compose --profile monitoring up --build
```

Running without Docker for the app (PostgreSQL still from compose):

```bash
docker compose up -d postgres
mvn spring-boot:run
```

Tests (Docker must be running, Testcontainers starts PostgreSQL):

```bash
mvn test
```

## Domain model

```
User 1 ──── 1 Calendar 1 ──── * TimeSlot 1 ──── 0..1 Meeting * ──── * User (participants)
                                                     │
                                                     └── organizer: User
```

- **User**: a person using the platform.
- **Calendar**: each user owns exactly one personal calendar, which holds their time slots. As the task asks, "calendar" lives only in the domain: the API addresses slots through the user (`/users/{userId}/slots`) and never exposes calendar IDs.
- **TimeSlot**: a half-open interval `[start, end)` with a configurable duration and a status of `FREE` or `BUSY`.
- **Meeting**: a booked slot with title, description, organizer and participants. Booking turns the slot `BUSY`; cancelling frees it again.

## Design decisions

**Non-overlapping slots are enforced by the database.** A PostgreSQL exclusion constraint (`EXCLUDE USING gist (calendar_id WITH =, tstzrange(start_time, end_time, '[)') WITH &&)`) guarantees that slots in one calendar never overlap, even under concurrent requests. A check in application code alone would have a race between "check" and "insert". Ranges are half-open, so back-to-back slots such as 09:00-09:30 and 09:30-10:00 are valid.

**Double booking is prevented twice.** Time slots carry a JPA `@Version` (optimistic locking), so two concurrent bookings of the same slot cannot both succeed; the loser gets `409 Conflict`. A unique constraint on `meetings.slot_id` is a second safety net.

**Indexed range queries.** Every read ("slots between A and B", "availability for a week") is a range query on one calendar, served by an index on `(calendar_id, start_time, end_time)`. List endpoints are paginated. At the expected scale (hundreds of users, thousands of slots) a single PostgreSQL instance handles this comfortably, so no cache or message broker is added; that would add operational cost without a measurable benefit.

**Times are UTC.** All timestamps are stored as `timestamptz` and exchanged as ISO-8601 instants. Time zone conversion is a client concern.

**Schema is owned by Flyway.** Hibernate runs with `ddl-auto: validate`, so the entities and the migration must agree, and the context-load test fails fast if they drift.

## Planned API

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/users` | Create a user (and their calendar) |
| GET | `/api/v1/users/{userId}` | Get a user |
| POST | `/api/v1/users/{userId}/slots` | Create a slot (start + duration in minutes) |
| POST | `/api/v1/users/{userId}/slots/batch` | Create consecutive slots in a window, e.g. every 30 min from 09:00 to 17:00 |
| GET | `/api/v1/users/{userId}/slots?from=&to=&status=` | List slots in a time frame (paginated) |
| PATCH | `/api/v1/users/{userId}/slots/{slotId}` | Reschedule a slot or mark it FREE/BUSY |
| DELETE | `/api/v1/users/{userId}/slots/{slotId}` | Delete a slot (not allowed while booked) |
| POST | `/api/v1/users/{userId}/slots/{slotId}/meeting` | Book the slot as a meeting with title, description, participants |
| GET | `/api/v1/meetings/{meetingId}` | Get a meeting |
| DELETE | `/api/v1/meetings/{meetingId}` | Cancel a meeting (slot becomes FREE) |
| GET | `/api/v1/users/{userId}/availability?from=&to=` | Aggregated free/busy view for a time frame |

The availability view merges adjacent intervals with the same status and also treats meetings the user is invited to as busy time.

## Out of scope / next steps

- Authentication and authorization (currently any caller can act on any user)
- Per-user time zones and recurring slots
- Notifications to participants
- Checking participants' own calendars for conflicts when booking
