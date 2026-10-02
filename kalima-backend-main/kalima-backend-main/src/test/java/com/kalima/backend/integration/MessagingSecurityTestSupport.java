package com.kalima.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.domain.repository.PersonRepository;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared helpers for messaging security integration tests (Week 1 + Week 2).
 */
final class MessagingSecurityTestSupport {

    private MessagingSecurityTestSupport() {
    }

    static String obtainToken(MockMvc mockMvc, ObjectMapper objectMapper,
                              String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken")
                .asText();
    }

    static UUID requirePersonId(PersonRepository personRepository, String email) {
        return personRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Missing seeded person: " + email))
                .getId();
    }

    static String createCase(MockMvc mockMvc, ObjectMapper objectMapper, String token,
                             UUID clientId, UUID assignedLawyerId, String titlePrefix)
            throws Exception {
        String lawyerJson = assignedLawyerId == null ? "null" : "\"" + assignedLawyerId + "\"";
        String body = """
                {
                  "caseNumber": "%s",
                  "title": "%s",
                  "clientId": "%s",
                  "assignedLawyerId": %s
                }
                """.formatted(
                "MSG-SEC-" + UUID.randomUUID(),
                titlePrefix + " " + UUID.randomUUID(),
                clientId,
                lawyerJson);
        MvcResult result = mockMvc.perform(post("/api/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    static String sendMessage(MockMvc mockMvc, ObjectMapper objectMapper, String token,
                              String caseId, String content) throws Exception {
        return sendMessage(mockMvc, objectMapper, token, caseId, content, null, null);
    }

    static String sendMessage(MockMvc mockMvc, ObjectMapper objectMapper, String token,
                              String caseId, String content, String attachmentUrl,
                              UUID replyToMessageId) throws Exception {
        StringBuilder body = new StringBuilder("{");
        if (content != null) {
            body.append("\"content\":\"").append(content).append("\"");
        }
        if (attachmentUrl != null) {
            if (body.length() > 1) {
                body.append(',');
            }
            body.append("\"attachmentUrl\":\"").append(attachmentUrl).append("\"");
        }
        if (replyToMessageId != null) {
            if (body.length() > 1) {
                body.append(',');
            }
            body.append("\"replyToMessageId\":\"").append(replyToMessageId).append("\"");
        }
        body.append('}');

        MvcResult result = mockMvc.perform(post("/api/cases/" + caseId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body.toString()))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    static String uploadAttachment(MockMvc mockMvc, ObjectMapper objectMapper, String token,
                                   String caseId, String filename, String contentType,
                                   byte[] bytes) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", filename, contentType, bytes);
        MvcResult result = mockMvc.perform(multipart("/api/messages/attachments")
                        .file(file)
                        .param("caseId", caseId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("url").asText();
    }

    static void expectMessageSendStatus(MockMvc mockMvc, String token, String caseId,
                                        String jsonBody, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/cases/" + caseId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(jsonBody))
                .andExpect(status().is(expectedStatus));
    }

    static String storedFilenameFromUrl(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    static String tenant1AdminToken(MockMvc mockMvc, ObjectMapper objectMapper) throws Exception {
        return obtainToken(mockMvc, objectMapper,
                KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
    }

    static String tenant2AdminToken(MockMvc mockMvc, ObjectMapper objectMapper) throws Exception {
        return obtainToken(mockMvc, objectMapper,
                KalimaTestDataSeeder.SECOND_ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
    }

    static String tenant1LawyerToken(MockMvc mockMvc, ObjectMapper objectMapper) throws Exception {
        return obtainToken(mockMvc, objectMapper,
                KalimaTestDataSeeder.LAWYER_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
    }

    static String unassignedLawyerToken(MockMvc mockMvc, ObjectMapper objectMapper) throws Exception {
        return obtainToken(mockMvc, objectMapper,
                KalimaTestDataSeeder.WITNESS_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
    }

    static String mutableLawyerToken(MockMvc mockMvc, ObjectMapper objectMapper) throws Exception {
        return obtainToken(mockMvc, objectMapper,
                KalimaTestDataSeeder.MUTABLE_LAWYER_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
    }

    static void addParticipant(MockMvc mockMvc, String adminToken, UUID caseId,
                               UUID personId, String role) throws Exception {
        mockMvc.perform(post("/api/cases/" + caseId + "/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("""
                                {"personId":"%s","role":"%s"}
                                """.formatted(personId, role)))
                .andExpect(status().isCreated());
    }

    static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    static void listMessagesExpectStatus(MockMvc mockMvc, String token, String caseId,
                                         int expectedStatus) throws Exception {
        mockMvc.perform(get("/api/cases/" + caseId + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is(expectedStatus));
    }

    static JsonNode parse(MockMvc mockMvc, ObjectMapper objectMapper, MvcResult result)
            throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
