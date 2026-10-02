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
import static com.kalima.backend.integration.MessagingSecurityTestSupport.sendMessage;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.tenant1AdminToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Message list pagination")
class MessagePaginationIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersonRepository personRepository;

    @Test
    @DisplayName("Default list returns paginated envelope with items array")
    void list_returnsPaginatedEnvelope() throws Exception {
        String token = tenant1AdminToken(mockMvc, objectMapper);
        UUID clientId = requirePersonId(personRepository, KalimaTestDataSeeder.CLIENT_EMAIL);
        String caseId = createCase(mockMvc, objectMapper, token, clientId, null, "Pagination shape");

        sendMessage(mockMvc, objectMapper, token, caseId, "hello");

        mockMvc.perform(get("/api/cases/" + caseId + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    @DisplayName("limit=1 with two messages sets hasMore and nextCursor")
    void list_smallPage_hasMoreAndCursor() throws Exception {
        String token = tenant1AdminToken(mockMvc, objectMapper);
        UUID clientId = requirePersonId(personRepository, KalimaTestDataSeeder.CLIENT_EMAIL);
        String caseId = createCase(mockMvc, objectMapper, token, clientId, null, "Pagination hasMore");

        sendMessage(mockMvc, objectMapper, token, caseId, "first");
        sendMessage(mockMvc, objectMapper, token, caseId, "second");

        mockMvc.perform(get("/api/cases/" + caseId + "/messages?limit=1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty());
    }

    @Test
    @DisplayName("before cursor returns older page in chronological order")
    void list_beforeCursor_returnsOlderPage() throws Exception {
        String token = tenant1AdminToken(mockMvc, objectMapper);
        UUID clientId = requirePersonId(personRepository, KalimaTestDataSeeder.CLIENT_EMAIL);
        String caseId = createCase(mockMvc, objectMapper, token, clientId, null, "Pagination cursor");

        sendMessage(mockMvc, objectMapper, token, caseId, "older");
        sendMessage(mockMvc, objectMapper, token, caseId, "newer");

        var firstPage = objectMapper.readTree(mockMvc.perform(get("/api/cases/" + caseId + "/messages?limit=1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        String cursor = firstPage.get("nextCursor").asText();
        mockMvc.perform(get("/api/cases/" + caseId + "/messages?limit=1&before=" + cursor)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].content").value("older"))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    @DisplayName("Invalid before cursor → 400")
    void list_invalidCursor_returns400() throws Exception {
        String token = tenant1AdminToken(mockMvc, objectMapper);
        UUID clientId = requirePersonId(personRepository, KalimaTestDataSeeder.CLIENT_EMAIL);
        String caseId = createCase(mockMvc, objectMapper, token, clientId, null, "Bad cursor");

        mockMvc.perform(get("/api/cases/" + caseId + "/messages?before=not-a-valid-cursor")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}
