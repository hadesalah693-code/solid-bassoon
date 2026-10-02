package com.kalima.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.domain.repository.PersonRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.kalima.backend.integration.MessagingSecurityTestSupport.createCase;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.listMessagesExpectStatus;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.obtainToken;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.requirePersonId;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.sendMessage;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.tenant1AdminToken;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.tenant1LawyerToken;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.tenant2AdminToken;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.unassignedLawyerToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Message access security — Week 1 tenant isolation")
class MessageAccessSecurityIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersonRepository personRepository;

    @Test
    @DisplayName("Cross-tenant message list → 404")
    void listMessages_crossTenant_returns404() throws Exception {
        String t2Token = tenant2AdminToken(mockMvc, objectMapper);
        UUID t2ClientId = requirePersonId(personRepository, KalimaTestDataSeeder.FOREIGN_CLIENT_EMAIL);
        String t2CaseId = createCase(mockMvc, objectMapper, t2Token, t2ClientId, null, "T2 messages");

        String t1Token = tenant1AdminToken(mockMvc, objectMapper);
        listMessagesExpectStatus(mockMvc, t1Token, t2CaseId, 404);
    }

    @Test
    @DisplayName("Cross-tenant message send → 404")
    void sendMessage_crossTenant_returns404() throws Exception {
        String t2Token = tenant2AdminToken(mockMvc, objectMapper);
        UUID t2ClientId = requirePersonId(personRepository, KalimaTestDataSeeder.FOREIGN_CLIENT_EMAIL);
        String t2CaseId = createCase(mockMvc, objectMapper, t2Token, t2ClientId, null, "T2 send block");

        String t1Token = tenant1AdminToken(mockMvc, objectMapper);
        mockMvc.perform(post("/api/cases/" + t2CaseId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + t1Token)
                        .content("{\"content\":\"cross-tenant attempt\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Lawyer without case assignment cannot list messages → 404")
    void listMessages_unassignedLawyer_returns404() throws Exception {
        String adminToken = tenant1AdminToken(mockMvc, objectMapper);
        UUID clientId = requirePersonId(personRepository, KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(personRepository, KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseId = createCase(mockMvc, objectMapper, adminToken, clientId, lawyerId, "Assigned case");

        sendMessage(mockMvc, objectMapper, adminToken, caseId, "hello");

        String witnessToken = unassignedLawyerToken(mockMvc, objectMapper);
        listMessagesExpectStatus(mockMvc, witnessToken, caseId, 404);
    }

    @Test
    @DisplayName("Assigned lawyer can list messages → 200")
    void listMessages_assignedLawyer_returns200() throws Exception {
        String adminToken = tenant1AdminToken(mockMvc, objectMapper);
        UUID clientId = requirePersonId(personRepository, KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(personRepository, KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseId = createCase(mockMvc, objectMapper, adminToken, clientId, lawyerId, "Lawyer access");

        sendMessage(mockMvc, objectMapper, adminToken, caseId, "visible to lawyer");

        String lawyerToken = tenant1LawyerToken(mockMvc, objectMapper);
        listMessagesExpectStatus(mockMvc, lawyerToken, caseId, 200);
    }

    @Test
    @DisplayName("Unauthenticated message list → 401")
    void listMessages_withoutToken_returns401() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/cases/" + UUID.randomUUID() + "/messages"))
                .andExpect(status().isUnauthorized());
    }
}
