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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Dispute official number — allocation and search")
class DisputeOfficialNumberIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersonRepository personRepository;

    @Test
    void create_returns_disputeNumber_matching_pattern() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        String body = buildCreateBody(
                requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL),
                requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL),
                "Official number pattern IT");
        MvcResult res = mockMvc.perform(post("/api/disputes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.disputeNumber").exists())
                .andReturn();
        assertThat(readDisputeNumber(res)).matches("DIS-\\d{4}-\\d{4}");
    }

    @Test
    void same_company_same_year_numbers_increment_sequentially() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String b1 = buildCreateBody(clientId, lawyerId, "Seq A " + UUID.randomUUID());
        String b2 = buildCreateBody(clientId, lawyerId, "Seq B " + UUID.randomUUID());
        MvcResult r1 = mockMvc.perform(post("/api/disputes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(b1))
                .andExpect(status().isCreated())
                .andReturn();
        MvcResult r2 = mockMvc.perform(post("/api/disputes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(b2))
                .andExpect(status().isCreated())
                .andReturn();
        String n1 = readDisputeNumber(r1);
        String n2 = readDisputeNumber(r2);
        assertThat(calendarPrefix(n1)).isEqualTo(calendarPrefix(n2));
        assertThat(trailingSeq(n2)).isEqualTo(trailingSeq(n1) + 1);
    }

    @Test
    void second_tenant_has_independent_counter_sequence() throws Exception {
        UUID foreignClientId = personRepository.findByEmail(KalimaTestDataSeeder.FOREIGN_CLIENT_EMAIL)
                .orElseThrow(() -> new IllegalStateException("Missing seeded foreign client"))
                .getId();
        String tokenSecond = obtainToken(KalimaTestDataSeeder.SECOND_ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        String body1 = buildCreateBodyNoLawyer(foreignClientId, "Second T 1 " + UUID.randomUUID());
        String body2 = buildCreateBodyNoLawyer(foreignClientId, "Second T 2 " + UUID.randomUUID());
        MvcResult r1 = mockMvc.perform(post("/api/disputes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tokenSecond)
                        .content(body1))
                .andExpect(status().isCreated())
                .andReturn();
        MvcResult r2 = mockMvc.perform(post("/api/disputes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tokenSecond)
                        .content(body2))
                .andExpect(status().isCreated())
                .andReturn();
        String n1 = readDisputeNumber(r1);
        String n2 = readDisputeNumber(r2);
        assertThat(n1).matches("DIS-\\d{4}-\\d{4}");
        assertThat(n2).matches("DIS-\\d{4}-\\d{4}");
        assertThat(calendarPrefix(n1)).isEqualTo(calendarPrefix(n2));
        assertThat(trailingSeq(n2)).isEqualTo(trailingSeq(n1) + 1);
    }

    @Test
    void search_finds_dispute_by_disputeNumber() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        String title = "Search by DN " + UUID.randomUUID();
        String body = buildCreateBody(
                requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL),
                requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL),
                title);
        MvcResult res = mockMvc.perform(post("/api/disputes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        String dn = readDisputeNumber(res);
        mockMvc.perform(get("/api/disputes")
                        .param("search", dn)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].disputeNumber").value(dn))
                .andExpect(jsonPath("$.content[0].title").value(title));
    }

    private static String buildCreateBody(UUID clientId, UUID lawyerId, String title) {
        return """
                {
                  "title": "%s",
                  "opponentName": "خصم IT",
                  "description": null,
                  "disputeType": "CIVIL",
                  "riskLevel": "MEDIUM",
                  "legalPath": null,
                  "claimAmount": null,
                  "successProbability": null,
                  "clientId": "%s",
                  "assignedLawyerId": "%s"
                }
                """.formatted(title, clientId, lawyerId);
    }

    private static String buildCreateBodyNoLawyer(UUID clientId, String title) {
        return """
                {
                  "title": "%s",
                  "opponentName": "خصم IT",
                  "description": null,
                  "disputeType": "CIVIL",
                  "riskLevel": "MEDIUM",
                  "legalPath": null,
                  "claimAmount": null,
                  "successProbability": null,
                  "clientId": "%s",
                  "assignedLawyerId": null
                }
                """.formatted(title, clientId);
    }

    private String readDisputeNumber(MvcResult res) throws Exception {
        JsonNode n = objectMapper.readTree(res.getResponse().getContentAsString());
        return n.get("disputeNumber").asText();
    }

    private static String calendarPrefix(String disputeNumber) {
        String[] p = disputeNumber.split("-", 3);
        return p[0] + "-" + p[1];
    }

    private static int trailingSeq(String disputeNumber) {
        int last = disputeNumber.lastIndexOf('-');
        return Integer.parseInt(disputeNumber.substring(last + 1));
    }

    private UUID requirePersonId(String email) {
        return personRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Missing seeded person: " + email))
                .getId();
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
