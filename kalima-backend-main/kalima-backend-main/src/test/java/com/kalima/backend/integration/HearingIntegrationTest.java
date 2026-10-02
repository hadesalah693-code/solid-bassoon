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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Hearings module — API integration")
class HearingIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersonRepository personRepository;

    @Test
    void createListGetUpdateHearing() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseNumber = "HEAR-INT-" + UUID.randomUUID();
        String caseBody = """
                {
                  "caseNumber": "%s",
                  "title": "Hearing IT case",
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

        String hDate = "2026-05-10T10:00:00";
        String createHearing = """
                {
                  "hearingDate": "%s",
                  "courtName": "محكمة الاستئناف",
                  "chamber": "1",
                  "result": null,
                  "notes": "جلسة أولى",
                  "nextHearingDate": "2026-06-01T10:00:00"
                }
                """.formatted(hDate);

        MvcResult created = mockMvc.perform(post("/api/cases/" + caseId + "/hearings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(createHearing))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.caseId").value(caseId))
                .andExpect(jsonPath("$.courtName").value("محكمة الاستئناف"))
                .andExpect(jsonPath("$.createdBy.id").isNotEmpty())
                .andReturn();
        String hearingId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/cases/" + caseId + "/hearings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(hearingId));

        mockMvc.perform(get("/api/cases/" + caseId + "/hearings/" + hearingId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hearingDate").isNotEmpty());

        mockMvc.perform(get("/api/hearings")
                        .param("date", "2026-05-10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].caseNumber").value(caseNumber))
                .andExpect(jsonPath("$.content[0].caseTitle").value("Hearing IT case"))
                .andExpect(jsonPath("$.content[0].client.id").isNotEmpty());

        String updateBody = """
                {
                  "hearingDate": "2026-05-10T10:00:00",
                  "courtName": "محكمة الاستئناف — غرفة محدثة",
                  "chamber": "2",
                  "result": "تأجيل",
                  "notes": "محدث",
                  "nextHearingDate": "2026-07-01T11:00:00"
                }
                """;
        mockMvc.perform(put("/api/cases/" + caseId + "/hearings/" + hearingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chamber").value("2"))
                .andExpect(jsonPath("$.result").value("تأجيل"));
    }

    @Test
    void getHearing_wrongCaseId_returnsNotFound() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UuidAndCase a = createCaseWithHearing(token);
        UuidAndCase b = createCaseWithHearing(token);
        assertThat(b.caseId).isNotEqualTo(a.caseId);
        mockMvc.perform(get("/api/cases/" + b.caseId + "/hearings/" + a.hearingId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void createHearing_archivedCase_returnsConflict() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UuidAndCase u = createBareCase(token);
        mockMvc.perform(delete("/api/cases/" + u.caseId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        String body = """
                {
                  "hearingDate": "2026-08-01T09:00:00",
                  "courtName": "X",
                  "chamber": null,
                  "result": null,
                  "notes": null,
                  "nextHearingDate": null
                }
                """;
        mockMvc.perform(post("/api/cases/" + u.caseId + "/hearings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void createHearing_nextBeforeHearingDate_returnsBadRequest() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UuidAndCase u = createBareCase(token);
        String body = """
                {
                  "hearingDate": "2026-08-10T10:00:00",
                  "courtName": "C",
                  "chamber": null,
                  "result": null,
                  "notes": null,
                  "nextHearingDate": "2026-08-01T10:00:00"
                }
                """;
        mockMvc.perform(post("/api/cases/" + u.caseId + "/hearings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    private UuidAndCase createCaseWithHearing(String token) throws Exception {
        UuidAndCase u = createBareCase(token);
        String createH = """
                {
                  "hearingDate": "2026-04-01T10:00:00",
                  "courtName": "C",
                  "chamber": null,
                  "result": null,
                  "notes": null,
                  "nextHearingDate": null
                }
                """;
        MvcResult r = mockMvc.perform(post("/api/cases/" + u.caseId + "/hearings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(createH))
                .andExpect(status().isCreated())
                .andReturn();
        String hid = objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
        return new UuidAndCase(u.caseId, hid);
    }

    private UuidAndCase createBareCase(String token) throws Exception {
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseNumber = "HEAR-INT-" + UUID.randomUUID();
        String caseBody = """
                {
                  "caseNumber": "%s",
                  "title": "Hearing IT case",
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
        return new UuidAndCase(caseId, null);
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

    private UUID requirePersonId(String email) {
        return personRepository.findByEmail(email)
                .orElseThrow()
                .getId();
    }

    private record UuidAndCase(String caseId, String hearingId) {
    }
}
