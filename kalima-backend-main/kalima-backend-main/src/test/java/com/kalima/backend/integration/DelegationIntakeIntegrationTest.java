package com.kalima.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.ocr.entity.OcrRecord;
import com.kalima.backend.ocr.repository.OcrRecordRepository;
import com.kalima.backend.ocr.service.OcrStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Delegation OCR intake — Phase 3")
class DelegationIntakeIntegrationTest extends AbstractPostgresSpringBootTest {

    private static final String SAMPLE_POA_TEXT = """
            توكيل قضائي
            رقم التوثيق: 2026/123456
            الموكل: %s
            الوكيل: Attorney Candidate One
            تاريخ الإصدار: 01/01/2026
            تاريخ انتهاء: 31/12/2027
            """;

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private OcrRecordRepository ocrRecordRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private OcrStorageService ocrStorageService;

    @Test
    void parse_create_link_roundtrip() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        String clientName = personName(KalimaTestDataSeeder.CLIENT_EMAIL);

        UUID ocrId = seedCompletedOcrRecord(SAMPLE_POA_TEXT.formatted(clientName), token);

        mockMvc.perform(get("/api/delegations/intake/from-ocr/" + ocrId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notaryNumber").value("2026/123456"))
                .andExpect(jsonPath("$.delegationType").value("JUDICIAL"))
                .andExpect(jsonPath("$.suggestedClient.id").value(clientId.toString()));

        String createBody = """
                {
                  "clientId": "%s",
                  "delegationType": "JUDICIAL",
                  "issuingCountry": "QA",
                  "issuedAt": "%s",
                  "notaryNumber": "2026/123456"
                }
                """.formatted(clientId, LocalDate.of(2026, 1, 1));

        MvcResult createRes = mockMvc.perform(post("/api/delegations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notaryNumber").value("2026/123456"))
                .andExpect(jsonPath("$.title", containsString("2026/123456")))
                .andReturn();

        String delegationId = objectMapper.readTree(createRes.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(post("/api/delegations/intake/" + delegationId + "/link-ocr/" + ocrId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void attachFromOcr_copiesSourceFile() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID ocrId = seedCompletedOcrRecord(SAMPLE_POA_TEXT.formatted(
                personName(KalimaTestDataSeeder.CLIENT_EMAIL)), token);

        TenantContext.set(Company.DEFAULT_ID);
        try {
            OcrRecord record = ocrRecordRepository.findScopedById(ocrId).orElseThrow();
            assertThat(Files.isRegularFile(ocrStorageService.filePath(record))).isTrue();
        } finally {
            TenantContext.clear();
        }

        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        String createBody = """
                {
                  "clientId": "%s",
                  "delegationType": "GENERAL",
                  "issuingCountry": "QA",
                  "issuedAt": "%s",
                  "notaryNumber": "2026/999999"
                }
                """.formatted(clientId, LocalDate.of(2026, 1, 1));
        MvcResult createRes = mockMvc.perform(post("/api/delegations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        String delegationId = objectMapper.readTree(createRes.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(post("/api/delegations/" + delegationId + "/documents/from-ocr/" + ocrId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentType").value("ORIGINAL"));
    }

    @Test
    void parseFromOcr_beforeCompleted_returns409() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID ocrId = seedPendingOcrRecord(token);

        mockMvc.perform(get("/api/delegations/intake/from-ocr/" + ocrId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    private UUID seedCompletedOcrRecord(String text, String token) throws Exception {
        return seedOcrRecord(text, DomainEnums.OcrStatus.COMPLETED, token);
    }

    private UUID seedPendingOcrRecord(String token) throws Exception {
        return seedOcrRecord(null, DomainEnums.OcrStatus.PENDING, token);
    }

    private UUID seedOcrRecord(String text, DomainEnums.OcrStatus status, String token) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "poa.pdf",
                "application/pdf",
                "%PDF-1.4 test".getBytes());

        MvcResult uploadRes = mockMvc.perform(multipart("/api/ocr/upload")
                        .file(file)
                        .param("title", "POA test")
                        .param("documentType", "SCAN")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        UUID ocrId = UUID.fromString(objectMapper.readTree(uploadRes.getResponse().getContentAsString())
                .get("id")
                .asText());

        if (text != null || status != DomainEnums.OcrStatus.PENDING) {
            TenantContext.set(Company.DEFAULT_ID);
            try {
                OcrRecord record = ocrRecordRepository.findScopedById(ocrId).orElseThrow();
                if (text != null) {
                    record.setExtractedText(text);
                }
                record.setStatus(status);
                ocrRecordRepository.saveAndFlush(record);
            } finally {
                TenantContext.clear();
            }
        }
        return ocrId;
    }

    private UUID requirePersonId(String email) {
        return personRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Missing person: " + email))
                .getId();
    }

    private String personName(String email) {
        var p = personRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Missing person: " + email));
        return p.getFullName() != null ? p.getFullName() : email;
    }

    private String obtainToken(String email, String password) throws Exception {
        String r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(r).get("accessToken").asText();
    }
}
