package com.kalima.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.domain.repository.PersonRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.kalima.backend.integration.MessagingSecurityTestSupport.bytes;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.createCase;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.expectMessageSendStatus;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.obtainToken;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.requirePersonId;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.sendMessage;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.storedFilenameFromUrl;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.tenant1AdminToken;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.tenant1LawyerToken;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.tenant2AdminToken;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.unassignedLawyerToken;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.uploadAttachment;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Message attachment security — Week 2")
class MessageAttachmentSecurityIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersonRepository personRepository;

    @Test
    @DisplayName("Valid upload and download by authorized user → 200")
    void uploadAndDownload_happyPath() throws Exception {
        String adminToken = tenant1AdminToken(mockMvc, objectMapper);
        UUID clientId = requirePersonId(personRepository, KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(personRepository, KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseId = createCase(mockMvc, objectMapper, adminToken, clientId, lawyerId, "Attachment ok");

        String url = uploadAttachment(mockMvc, objectMapper, adminToken, caseId,
                "note.txt", "text/plain", bytes("legal note"));
        String filename = storedFilenameFromUrl(url);

        mockMvc.perform(get("/api/messages/attachments/" + filename)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Cross-tenant attachment download → 404")
    void download_crossTenant_returns404() throws Exception {
        String t1Token = tenant1AdminToken(mockMvc, objectMapper);
        UUID clientId = requirePersonId(personRepository, KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(personRepository, KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseId = createCase(mockMvc, objectMapper, t1Token, clientId, lawyerId, "T1 attachment");

        String url = uploadAttachment(mockMvc, objectMapper, t1Token, caseId,
                "secret.txt", "text/plain", bytes("tenant1 secret"));
        String filename = storedFilenameFromUrl(url);

        String t2Token = tenant2AdminToken(mockMvc, objectMapper);
        mockMvc.perform(get("/api/messages/attachments/" + filename)
                        .header("Authorization", "Bearer " + t2Token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Download without case access → 404")
    void download_withoutCaseAccess_returns404() throws Exception {
        String adminToken = tenant1AdminToken(mockMvc, objectMapper);
        UUID clientId = requirePersonId(personRepository, KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(personRepository, KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseId = createCase(mockMvc, objectMapper, adminToken, clientId, lawyerId, "Protected file");

        String url = uploadAttachment(mockMvc, objectMapper, adminToken, caseId,
                "protected.txt", "text/plain", bytes("restricted"));
        String filename = storedFilenameFromUrl(url);

        String witnessToken = unassignedLawyerToken(mockMvc, objectMapper);
        mockMvc.perform(get("/api/messages/attachments/" + filename)
                        .header("Authorization", "Bearer " + witnessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Download without authentication → 401")
    void download_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/messages/attachments/" + UUID.randomUUID() + "_file.txt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Download unknown filename → 404")
    void download_unknownFilename_returns404() throws Exception {
        String adminToken = tenant1AdminToken(mockMvc, objectMapper);
        mockMvc.perform(get("/api/messages/attachments/" + UUID.randomUUID() + "_missing.txt")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Upload with blocked HTML extension → 400")
    void upload_blockedHtmlExtension_returns400() throws Exception {
        String adminToken = tenant1AdminToken(mockMvc, objectMapper);
        UUID clientId = requirePersonId(personRepository, KalimaTestDataSeeder.CLIENT_EMAIL);
        String caseId = createCase(mockMvc, objectMapper, adminToken, clientId, null, "MIME block");

        MockMultipartFile file = new MockMultipartFile(
                "file", "evil.html", "text/html", bytes("<script>alert(1)</script>"));
        mockMvc.perform(multipart("/api/messages/attachments")
                        .file(file)
                        .param("caseId", caseId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Upload with disallowed MIME type → 400")
    void upload_disallowedMime_returns400() throws Exception {
        String adminToken = tenant1AdminToken(mockMvc, objectMapper);
        UUID clientId = requirePersonId(personRepository, KalimaTestDataSeeder.CLIENT_EMAIL);
        String caseId = createCase(mockMvc, objectMapper, adminToken, clientId, null, "MIME reject");

        MockMultipartFile file = new MockMultipartFile(
                "file", "payload.txt", "application/x-msdownload", bytes("MZ"));
        mockMvc.perform(multipart("/api/messages/attachments")
                        .file(file)
                        .param("caseId", caseId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Upload without caseId → 400")
    void upload_missingCaseId_returns400() throws Exception {
        String adminToken = tenant1AdminToken(mockMvc, objectMapper);
        MockMultipartFile file = new MockMultipartFile(
                "file", "note.txt", "text/plain", bytes("hello"));
        mockMvc.perform(multipart("/api/messages/attachments")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Cross-tenant upload to foreign case → 404")
    void upload_crossTenantCase_returns404() throws Exception {
        String t2Token = tenant2AdminToken(mockMvc, objectMapper);
        UUID t2ClientId = requirePersonId(personRepository, KalimaTestDataSeeder.FOREIGN_CLIENT_EMAIL);
        String t2CaseId = createCase(mockMvc, objectMapper, t2Token, t2ClientId, null, "T2 case");

        String t1Token = tenant1AdminToken(mockMvc, objectMapper);
        MockMultipartFile file = new MockMultipartFile(
                "file", "note.txt", "text/plain", bytes("cross tenant"));
        mockMvc.perform(multipart("/api/messages/attachments")
                        .file(file)
                        .param("caseId", t2CaseId)
                        .header("Authorization", "Bearer " + t1Token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Send with external attachmentUrl → 400")
    void sendMessage_externalAttachmentUrl_returns400() throws Exception {
        String adminToken = tenant1AdminToken(mockMvc, objectMapper);
        UUID clientId = requirePersonId(personRepository, KalimaTestDataSeeder.CLIENT_EMAIL);
        String caseId = createCase(mockMvc, objectMapper, adminToken, clientId, null, "URL inject");

        expectMessageSendStatus(mockMvc, adminToken, caseId,
                "{\"content\":\"x\",\"attachmentUrl\":\"https://evil.example.com/file.pdf\"}",
                400);
    }

    @Test
    @DisplayName("Send with attachment uploaded by another user → 400")
    void sendMessage_otherUsersAttachment_returns400() throws Exception {
        String adminToken = tenant1AdminToken(mockMvc, objectMapper);
        UUID clientId = requirePersonId(personRepository, KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(personRepository, KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseId = createCase(mockMvc, objectMapper, adminToken, clientId, lawyerId, "Owner check");

        String url = uploadAttachment(mockMvc, objectMapper, adminToken, caseId,
                "owned.txt", "text/plain", bytes("admin upload"));

        String lawyerToken = tenant1LawyerToken(mockMvc, objectMapper);
        expectMessageSendStatus(mockMvc, lawyerToken, caseId,
                "{\"content\":\"steal\",\"attachmentUrl\":\"" + url + "\"}",
                400);
    }

    @Test
    @DisplayName("Send with attachment from another case → 400")
    void sendMessage_attachmentFromOtherCase_returns400() throws Exception {
        String adminToken = tenant1AdminToken(mockMvc, objectMapper);
        UUID clientId = requirePersonId(personRepository, KalimaTestDataSeeder.CLIENT_EMAIL);
        String caseA = createCase(mockMvc, objectMapper, adminToken, clientId, null, "Case A");
        String caseB = createCase(mockMvc, objectMapper, adminToken, clientId, null, "Case B");

        String url = uploadAttachment(mockMvc, objectMapper, adminToken, caseA,
                "case-a.txt", "text/plain", bytes("case a only"));

        expectMessageSendStatus(mockMvc, adminToken, caseB,
                "{\"content\":\"wrong case\",\"attachmentUrl\":\"" + url + "\"}",
                400);
    }

    @Test
    @DisplayName("Send with valid owned attachment → 201")
    void sendMessage_validAttachment_returns201() throws Exception {
        String adminToken = tenant1AdminToken(mockMvc, objectMapper);
        UUID clientId = requirePersonId(personRepository, KalimaTestDataSeeder.CLIENT_EMAIL);
        String caseId = createCase(mockMvc, objectMapper, adminToken, clientId, null, "Valid attach send");

        String url = uploadAttachment(mockMvc, objectMapper, adminToken, caseId,
                "brief.txt", "text/plain", bytes("attached"));

        mockMvc.perform(post("/api/cases/" + caseId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content("{\"content\":\"see attached\",\"attachmentUrl\":\"" + url + "\"}"))
                .andExpect(status().isCreated());
    }
}
