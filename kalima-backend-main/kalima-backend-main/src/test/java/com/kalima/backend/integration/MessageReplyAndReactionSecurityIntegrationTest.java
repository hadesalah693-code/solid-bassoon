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
import static com.kalima.backend.integration.MessagingSecurityTestSupport.expectMessageSendStatus;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.requirePersonId;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.sendMessage;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.tenant1AdminToken;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.tenant2AdminToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Message reply & reaction security — Week 1")
class MessageReplyAndReactionSecurityIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersonRepository personRepository;

    @Test
    @DisplayName("Reply to message in another case (same tenant) → 404")
    void reply_wrongCase_returns404() throws Exception {
        String adminToken = tenant1AdminToken(mockMvc, objectMapper);
        UUID clientId = requirePersonId(personRepository, KalimaTestDataSeeder.CLIENT_EMAIL);
        String caseA = createCase(mockMvc, objectMapper, adminToken, clientId, null, "Reply case A");
        String caseB = createCase(mockMvc, objectMapper, adminToken, clientId, null, "Reply case B");

        String messageInA = sendMessage(mockMvc, objectMapper, adminToken, caseA, "parent in A");

        expectMessageSendStatus(mockMvc, adminToken, caseB,
                "{\"content\":\"wrong reply\",\"replyToMessageId\":\"" + messageInA + "\"}",
                404);
    }

    @Test
    @DisplayName("Reply to message in another tenant → 404")
    void reply_crossTenant_returns404() throws Exception {
        String t2Token = tenant2AdminToken(mockMvc, objectMapper);
        UUID t2ClientId = requirePersonId(personRepository, KalimaTestDataSeeder.FOREIGN_CLIENT_EMAIL);
        String t2CaseId = createCase(mockMvc, objectMapper, t2Token, t2ClientId, null, "T2 reply target");
        String t2MessageId = sendMessage(mockMvc, objectMapper, t2Token, t2CaseId, "foreign parent");

        String t1Token = tenant1AdminToken(mockMvc, objectMapper);
        UUID t1ClientId = requirePersonId(personRepository, KalimaTestDataSeeder.CLIENT_EMAIL);
        String t1CaseId = createCase(mockMvc, objectMapper, t1Token, t1ClientId, null, "T1 reply sender");

        expectMessageSendStatus(mockMvc, t1Token, t1CaseId,
                "{\"content\":\"cross tenant reply\",\"replyToMessageId\":\"" + t2MessageId + "\"}",
                404);
    }

    @Test
    @DisplayName("Valid reply in same case → 201")
    void reply_sameCase_returns201() throws Exception {
        String adminToken = tenant1AdminToken(mockMvc, objectMapper);
        UUID clientId = requirePersonId(personRepository, KalimaTestDataSeeder.CLIENT_EMAIL);
        String caseId = createCase(mockMvc, objectMapper, adminToken, clientId, null, "Valid reply");

        String parentId = sendMessage(mockMvc, objectMapper, adminToken, caseId, "parent");

        mockMvc.perform(post("/api/cases/" + caseId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"content\":\"child\",\"replyToMessageId\":\"" + parentId + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Cross-tenant reaction → 404")
    void reaction_crossTenant_returns404() throws Exception {
        String t2Token = tenant2AdminToken(mockMvc, objectMapper);
        UUID t2ClientId = requirePersonId(personRepository, KalimaTestDataSeeder.FOREIGN_CLIENT_EMAIL);
        String t2CaseId = createCase(mockMvc, objectMapper, t2Token, t2ClientId, null, "T2 reaction");
        String t2MessageId = sendMessage(mockMvc, objectMapper, t2Token, t2CaseId, "react me");

        String t1Token = tenant1AdminToken(mockMvc, objectMapper);
        mockMvc.perform(post("/api/cases/" + t2CaseId + "/messages/" + t2MessageId + "/reactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + t1Token)
                        .content("{\"emoji\":\"👍\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Reaction with wrong caseId → 404")
    void reaction_wrongCaseId_returns404() throws Exception {
        String adminToken = tenant1AdminToken(mockMvc, objectMapper);
        UUID clientId = requirePersonId(personRepository, KalimaTestDataSeeder.CLIENT_EMAIL);
        String caseA = createCase(mockMvc, objectMapper, adminToken, clientId, null, "React A");
        String caseB = createCase(mockMvc, objectMapper, adminToken, clientId, null, "React B");
        String messageInA = sendMessage(mockMvc, objectMapper, adminToken, caseA, "in A");

        mockMvc.perform(post("/api/cases/" + caseB + "/messages/" + messageInA + "/reactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"emoji\":\"👍\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Valid reaction → 200 with reaction map")
    void reaction_happyPath_returns200() throws Exception {
        String adminToken = tenant1AdminToken(mockMvc, objectMapper);
        UUID clientId = requirePersonId(personRepository, KalimaTestDataSeeder.CLIENT_EMAIL);
        String caseId = createCase(mockMvc, objectMapper, adminToken, clientId, null, "React ok");
        String messageId = sendMessage(mockMvc, objectMapper, adminToken, caseId, "like this");

        mockMvc.perform(post("/api/cases/" + caseId + "/messages/" + messageId + "/reactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"emoji\":\"👍\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reactions['👍']").value(1))
                .andExpect(jsonPath("$.myReaction").value("👍"));
    }
}
