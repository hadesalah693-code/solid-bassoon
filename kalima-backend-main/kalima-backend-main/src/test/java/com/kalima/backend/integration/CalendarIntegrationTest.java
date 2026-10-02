package com.kalima.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.domain.repository.PersonRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Calendar module — API integration")
class CalendarIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersonRepository personRepository;

    @Test
    void hearingCreate_appearsInCalendarWithDeepLink() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        String caseId = createCase(token);

        mockMvc.perform(post("/api/cases/" + caseId + "/hearings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "hearingDate": "2026-08-15T09:00:00",
                                  "courtName": "محكمة التقويم",
                                  "chamber": "1",
                                  "result": null,
                                  "notes": null,
                                  "nextHearingDate": null
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/calendar/events")
                        .param("from", "2026-08-01T00:00:00")
                        .param("to", "2026-08-31T23:59:59")
                        .param("eventType", "HEARING")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventType").value("HEARING"))
                .andExpect(jsonPath("$.content[0].editable").value(false))
                .andExpect(jsonPath("$.content[0].sourceHref").value(
                        "/cases/" + caseId + "?section=hearings"));
    }

    @Test
    void nativeEvent_createPatchDelete() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);

        MvcResult created = mockMvc.perform(post("/api/calendar/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "title": "اجتماع داخلي",
                                  "description": "مراجعة",
                                  "eventType": "MEETING",
                                  "startAt": "2026-09-10T14:00:00",
                                  "endAt": "2026-09-10T15:00:00",
                                  "assignedToId": "%s",
                                  "reminderOffsetsMinutes": [60]
                                }
                                """.formatted(lawyerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.eventType").value("MEETING"))
                .andReturn();

        String eventId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(patch("/api/calendar/events/" + eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {"title": "اجتماع داخلي — محدث", "status": "COMPLETED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("اجتماع داخلي — محدث"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/calendar/events/" + eventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void projectedEvent_patchReturnsConflict() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        String caseId = createCase(token);

        mockMvc.perform(post("/api/cases/" + caseId + "/hearings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "hearingDate": "2026-10-01T10:00:00",
                                  "courtName": "محكمة",
                                  "chamber": null,
                                  "result": null,
                                  "notes": null,
                                  "nextHearingDate": null
                                }
                                """))
                .andExpect(status().isCreated());

        MvcResult list = mockMvc.perform(get("/api/calendar/events")
                        .param("from", "2026-10-01T00:00:00")
                        .param("to", "2026-10-02T00:00:00")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = objectMapper.readTree(list.getResponse().getContentAsString())
                .get("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.size()).isGreaterThan(0);
        String eventId = content.get(0).get("id").asText();

        mockMvc.perform(patch("/api/calendar/events/" + eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"title\": \"hack\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void summaryAndToday_respectAuthenticatedUser() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);

        mockMvc.perform(get("/api/calendar/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayCount").isNumber());

        mockMvc.perform(get("/api/calendar/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    private String createCase(String token) throws Exception {
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseNumber = "CAL-INT-" + UUID.randomUUID();
        MvcResult caseRes = mockMvc.perform(post("/api/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "caseNumber": "%s",
                                  "title": "Calendar IT case",
                                  "clientId": "%s",
                                  "assignedLawyerId": "%s"
                                }
                                """.formatted(caseNumber, clientId, lawyerId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(caseRes.getResponse().getContentAsString()).get("id").asText();
    }

    private UUID requirePersonId(String email) {
        return personRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Person not found: " + email))
                .getId();
    }

    private String obtainToken(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText();
    }
}
