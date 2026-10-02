package com.kalima.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.domain.repository.PersonRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Production hardening — API integration")
class HardeningIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersonRepository personRepository;

    @Test
    void loginSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + KalimaTestDataSeeder.ADMIN_EMAIL
                                + "\",\"password\":\"" + KalimaTestDataSeeder.PLAIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void loginFailure() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + KalimaTestDataSeeder.ADMIN_EMAIL
                                + "\",\"password\":\"WrongPassword1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthorizedWhenNoToken() throws Exception {
        mockMvc.perform(get("/api/cases"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forbiddenForLawyerOnAuditLogs() throws Exception {
        String token = obtainAccessToken(
                KalimaTestDataSeeder.LAWYER_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminListAuditLogsReturnsPageWithoutLazyError() throws Exception {
        String token = obtainAccessToken(
                KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void crossTenantPersonReturnsNotFound() throws Exception {
        String token = obtainAccessToken(
                KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID foreignId = personRepository.findByEmail(KalimaTestDataSeeder.FOREIGN_CLIENT_EMAIL)
                .orElseThrow()
                .getId();
        mockMvc.perform(get("/api/persons/" + foreignId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void createCase() throws Exception {
        String token = obtainAccessToken(
                KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseNumber = "HARD-CREATE-" + UUID.randomUUID();
        String body = """
                {
                  "caseNumber": "%s",
                  "title": "Hardening case",
                  "clientId": "%s",
                  "assignedLawyerId": "%s"
                }
                """.formatted(caseNumber, clientId, lawyerId);
        mockMvc.perform(post("/api/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void duplicateCaseNumberConflict() throws Exception {
        String token = obtainAccessToken(
                KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseNumber = "HARD-DUP-" + UUID.randomUUID();
        String body = """
                {
                  "caseNumber": "%s",
                  "title": "First",
                  "clientId": "%s",
                  "assignedLawyerId": "%s"
                }
                """.formatted(caseNumber, clientId, lawyerId);
        mockMvc.perform(post("/api/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void addParticipantAndDuplicateIsConflict() throws Exception {
        String token = obtainAccessToken(
                KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        UUID witnessId = requirePersonId(KalimaTestDataSeeder.WITNESS_EMAIL);
        String caseNumber = "HARD-PART-" + UUID.randomUUID();
        String createCase = """
                {
                  "caseNumber": "%s",
                  "title": "Participant test",
                  "clientId": "%s",
                  "assignedLawyerId": "%s"
                }
                """.formatted(caseNumber, clientId, lawyerId);
        ResultActions create = mockMvc.perform(post("/api/cases")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .content(createCase));
        create.andExpect(status().isCreated());
        String resp = create.andReturn().getResponse().getContentAsString();
        String caseId = objectMapper.readTree(resp).get("id").asText();

        String addParticipant = """
                { "personId": "%s", "role": "WITNESS" }
                """.formatted(witnessId);
        mockMvc.perform(post("/api/cases/" + caseId + "/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(addParticipant))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/cases/" + caseId + "/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(addParticipant))
                .andExpect(status().isConflict());
    }

    @Test
    void archiveClientLinkedToCaseReturnsConflict() throws Exception {
        String token = obtainAccessToken(
                KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseNumber = "HARD-ARCH-" + UUID.randomUUID();
        String createCase = """
                {
                  "caseNumber": "%s",
                  "title": "Archive ref test",
                  "clientId": "%s",
                  "assignedLawyerId": "%s"
                }
                """.formatted(caseNumber, clientId, lawyerId);
        mockMvc.perform(post("/api/cases")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .content(createCase))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/persons/" + clientId + "/archive")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    private String obtainAccessToken(String email, String password) throws Exception {
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
}
