package io.github.vijaymuthachari.minidoodle.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests through the REST API against a real PostgreSQL
 * (the overlap rule lives in the database, so an in-memory DB would not do).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SchedulingApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mvc;

    @Test
    void createSlotsBookMeetingAndSeeAvailability() throws Exception {
        String alice = createUser("Alice");
        String bob = createUser("Bob");

        // 09:00-11:00 in 30 minute slots
        postJson("/api/v1/users/" + alice + "/slots/batch", """
                {"from": "2026-11-10T09:00:00Z", "to": "2026-11-10T11:00:00Z", "durationMinutes": 30}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(4)));

        String slotId = firstSlotId(alice, "2026-11-10T09:30:00Z", "2026-11-10T10:00:00Z");

        String meeting = postJson("/api/v1/users/" + alice + "/slots/" + slotId + "/meeting", """
                {"title": "Sprint planning", "description": "Plan next sprint", "participantIds": ["%s"]}
                """.formatted(bob))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizerId").value(alice))
                .andExpect(jsonPath("$.participantIds[0]").value(bob))
                .andReturn().getResponse().getContentAsString();

        // Alice: free 09:00-09:30 and 10:00-11:00, busy 09:30-10:00
        mvc.perform(get("/api/v1/users/" + alice + "/availability")
                        .param("from", "2026-11-10T08:00:00Z")
                        .param("to", "2026-11-10T12:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.free", hasSize(2)))
                .andExpect(jsonPath("$.free[0].start").value("2026-11-10T09:00:00Z"))
                .andExpect(jsonPath("$.free[0].end").value("2026-11-10T09:30:00Z"))
                .andExpect(jsonPath("$.free[1].start").value("2026-11-10T10:00:00Z"))
                .andExpect(jsonPath("$.free[1].end").value("2026-11-10T11:00:00Z"))
                .andExpect(jsonPath("$.busy", hasSize(1)))
                .andExpect(jsonPath("$.busy[0].start").value("2026-11-10T09:30:00Z"));

        // Bob has no slots of his own, but the meeting he is invited to shows as busy
        mvc.perform(get("/api/v1/users/" + bob + "/availability")
                        .param("from", "2026-11-10T08:00:00Z")
                        .param("to", "2026-11-10T12:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.free", hasSize(0)))
                .andExpect(jsonPath("$.busy", hasSize(1)));

        // cancelling frees the slot again
        String meetingId = JsonPath.read(meeting, "$.id");
        mvc.perform(delete("/api/v1/meetings/" + meetingId)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/users/" + alice + "/slots")
                        .param("from", "2026-11-10T09:30:00Z")
                        .param("to", "2026-11-10T10:00:00Z"))
                .andExpect(jsonPath("$.content[0].status").value("FREE"));
    }

    @Test
    void overlappingSlotIsRejected() throws Exception {
        String user = createUser("Carol");
        postJson("/api/v1/users/" + user + "/slots", """
                {"startTime": "2026-11-11T09:00:00Z", "durationMinutes": 60}
                """).andExpect(status().isCreated());

        postJson("/api/v1/users/" + user + "/slots", """
                {"startTime": "2026-11-11T09:30:00Z", "durationMinutes": 60}
                """).andExpect(status().isConflict());

        // back-to-back is fine
        postJson("/api/v1/users/" + user + "/slots", """
                {"startTime": "2026-11-11T10:00:00Z", "durationMinutes": 30}
                """).andExpect(status().isCreated());
    }

    @Test
    void bookedSlotCannotBeBookedTwiceOrChanged() throws Exception {
        String user = createUser("Dave");
        String slotId = JsonPath.read(postJson("/api/v1/users/" + user + "/slots", """
                {"startTime": "2026-11-12T14:00:00Z", "durationMinutes": 30}
                """).andReturn().getResponse().getContentAsString(), "$.id");

        String body = """
                {"title": "1:1"}
                """;
        postJson("/api/v1/users/" + user + "/slots/" + slotId + "/meeting", body).andExpect(status().isCreated());
        postJson("/api/v1/users/" + user + "/slots/" + slotId + "/meeting", body).andExpect(status().isConflict());

        mvc.perform(patch("/api/v1/users/" + user + "/slots/" + slotId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "FREE"}
                                """))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/users/" + user + "/slots/" + slotId)).andExpect(status().isConflict());
    }

    @Test
    void validationErrorsReturn400() throws Exception {
        String user = createUser("Eve");
        postJson("/api/v1/users/" + user + "/slots", """
                {"startTime": "2026-11-12T14:00:00Z", "durationMinutes": 0}
                """).andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/users/" + UUID.randomUUID() + "/availability")
                        .param("from", "2026-11-10T08:00:00Z")
                        .param("to", "2026-11-10T12:00:00Z"))
                .andExpect(status().isNotFound());
    }

    private String createUser(String name) throws Exception {
        String email = name.toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        String response = postJson("/api/v1/users", """
                {"name": "%s", "email": "%s"}
                """.formatted(name, email))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private String firstSlotId(String userId, String from, String to) throws Exception {
        String response = mvc.perform(get("/api/v1/users/" + userId + "/slots").param("from", from).param("to", to))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.content[0].id");
    }

    private ResultActions postJson(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
