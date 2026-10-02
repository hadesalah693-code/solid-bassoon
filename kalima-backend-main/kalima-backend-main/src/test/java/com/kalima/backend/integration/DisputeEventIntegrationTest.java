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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Dispute events module — API integration")
class DisputeEventIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersonRepository personRepository;

    private UUID requirePersonId(String email) {
        return personRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Missing seeded person: " + email))
                .getId();
    }

    @Test
    void createListPatchDelete_flow() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL,
                KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseNumber = "DE-INT-" + UUID.randomUUID();
        String caseBody = """
                {
                  "caseNumber": "%s",
                  "title": "Dispute event IT case",
                  "clientId": "%s",
                  "assignedLawyerId": "%s"
                }
                """.formatted(caseNumber, clientId, lawyerId);
        MvcResult caseRes = mockMvc.perform(post("/api/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(caseBody))
                .andExpect(status().isCreated())
                .andReturn();
        String caseId = objectMapper.readTree(caseRes.getResponse().getContentAsString()).get("id").asText();

        String createBody = """
                {
                  "type": "FIRST_HEARING",
                  "status": "UPCOMING",
                  "title": "الجلسة الأولى",
                  "summary": "مسار تجريبي",
                  "eventDate": "2026-06-01T10:00:00",
                  "sortOrder": 10
                }
                """;
        MvcResult created = mockMvc.perform(post("/api/cases/" + caseId + "/dispute-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.caseId").value(caseId))
                .andExpect(jsonPath("$.title").value("الجلسة الأولى"))
                .andExpect(jsonPath("$.sortOrder").value(10))
                .andReturn();
        String eventId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/cases/" + caseId + "/dispute-events")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(eventId));

        String patchBody = """
                {
                  "type": "FIRST_HEARING",
                  "status": "CURRENT",
                  "title": "الجلسة الأولى — محدث",
                  "summary": null,
                  "eventDate": "2026-06-01T11:00:00",
                  "sortOrder": 5
                }
                """;
        mockMvc.perform(patch("/api/cases/" + caseId + "/dispute-events/" + eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(patchBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CURRENT"))
                .andExpect(jsonPath("$.sortOrder").value(5));

        mockMvc.perform(delete("/api/cases/" + caseId + "/dispute-events/" + eventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/cases/" + caseId + "/dispute-events")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void patch_wrongCaseId_returnsNotFound() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL,
                KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);

        String caseA = createCase(token, clientId, lawyerId);
        String caseB = createCase(token, clientId, lawyerId);

        String createBody = """
                {
                  "type": "OTHER",
                  "status": "DONE",
                  "title": "حدث تجريبي"
                }
                """;
        MvcResult created = mockMvc.perform(post("/api/cases/" + caseA + "/dispute-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        String eventId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(patch("/api/cases/" + caseB + "/dispute-events/" + eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "type": "OTHER",
                                  "status": "DONE",
                                  "title": "x",
                                  "sortOrder": 0
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    private String createCase(String token, UUID clientId, UUID lawyerId) throws Exception {
        String caseNumber = "DE-WR-" + UUID.randomUUID();
        String caseBody = """
                {
                  "caseNumber": "%s",
                  "title": "Case",
                  "clientId": "%s",
                  "assignedLawyerId": "%s"
                }
                """.formatted(caseNumber, clientId, lawyerId);
        MvcResult caseRes = mockMvc.perform(post("/api/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(caseBody))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(caseRes.getResponse().getContentAsString()).get("id").asText();
    }

    /** Smoke: ensure seeded client exists. */
    @Test
    void seedClientResolvable() {
        assertThat(requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL)).isNotNull();
    }

    private String obtainToken(String email, String password) throws Exception {
        String r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode n = objectMapper.readTree(r);
        return n.get("accessToken").asText();
    }
}
