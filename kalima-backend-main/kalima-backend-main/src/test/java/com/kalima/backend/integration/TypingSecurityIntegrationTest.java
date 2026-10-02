package com.kalima.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.domain.repository.PersonRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.kalima.backend.integration.MessagingSecurityTestSupport.createCase;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.requirePersonId;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.tenant1AdminToken;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.tenant2AdminToken;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.unassignedLawyerToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Typing notification security — Week 1")
class TypingSecurityIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersonRepository personRepository;

    @Test
    @DisplayName("Cross-tenant typing → 404")
    void typing_crossTenant_returns404() throws Exception {
        String t2Token = tenant2AdminToken(mockMvc, objectMapper);
        UUID t2ClientId = requirePersonId(personRepository, KalimaTestDataSeeder.FOREIGN_CLIENT_EMAIL);
        String t2CaseId = createCase(mockMvc, objectMapper, t2Token, t2ClientId, null, "T2 typing");

        String t1Token = tenant1AdminToken(mockMvc, objectMapper);
        mockMvc.perform(post("/api/cases/" + t2CaseId + "/typing")
                        .header("Authorization", "Bearer " + t1Token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Typing without case access → 404")
    void typing_unassignedLawyer_returns404() throws Exception {
        String adminToken = tenant1AdminToken(mockMvc, objectMapper);
        UUID clientId = requirePersonId(personRepository, KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(personRepository, KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseId = createCase(mockMvc, objectMapper, adminToken, clientId, lawyerId, "Typing block");

        String witnessToken = unassignedLawyerToken(mockMvc, objectMapper);
        mockMvc.perform(post("/api/cases/" + caseId + "/typing")
                        .header("Authorization", "Bearer " + witnessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Authorized typing → 204")
    void typing_authorized_returns204() throws Exception {
        String adminToken = tenant1AdminToken(mockMvc, objectMapper);
        UUID clientId = requirePersonId(personRepository, KalimaTestDataSeeder.CLIENT_EMAIL);
        String caseId = createCase(mockMvc, objectMapper, adminToken, clientId, null, "Typing ok");

        mockMvc.perform(post("/api/cases/" + caseId + "/typing")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Unauthenticated typing → 401")
    void typing_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/cases/" + UUID.randomUUID() + "/typing"))
                .andExpect(status().isUnauthorized());
    }
}
