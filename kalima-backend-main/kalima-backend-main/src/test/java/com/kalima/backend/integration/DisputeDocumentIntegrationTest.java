package com.kalima.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.domain.repository.PersonRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Dispute documents API")
class DisputeDocumentIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersonRepository personRepository;

    @Test
    void upload_list_download_roundtrip() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        String disputeId = createDisputeViaMvc(token, "Docs IT " + UUID.randomUUID());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notice.pdf",
                "application/pdf",
                "%PDF-1.4 minimal".getBytes());

        MvcResult uploadRes = mockMvc.perform(multipart("/api/disputes/" + disputeId + "/documents")
                        .file(file)
                        .param("category", "EVIDENCE")
                        .param("visibility", "STAFF_ONLY")
                        .param("description", "مرفق نزاع")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.disputeId").value(disputeId))
                .andExpect(jsonPath("$.originalFilename").value("notice.pdf"))
                .andExpect(jsonPath("$.stageId").value(nullValue()))
                .andReturn();

        String documentId = objectMapper.readTree(uploadRes.getResponse().getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(get("/api/disputes/" + disputeId + "/documents")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(documentId));

        mockMvc.perform(get("/api/disputes/" + disputeId + "/documents/" + documentId + "/download")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(r -> assertThat(r.getResponse().getContentAsByteArray().length).isPositive());
    }

    @Test
    void upload_with_stage_and_filter_list() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        String disputeId = createDisputeViaMvc(token, "Stage doc " + UUID.randomUUID());

        MvcResult stageRes = mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/disputes/" + disputeId + "/stages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"stageType\":\"LEGAL_NOTICE\",\"status\":\"ACTIVE\",\"title\":\"إنذار\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String stageId = objectMapper.readTree(stageRes.getResponse().getContentAsString()).get("id").asText();

        MockMultipartFile file = new MockMultipartFile(
                "file", "a.txt", "text/plain", "x".getBytes());

        mockMvc.perform(multipart("/api/disputes/" + disputeId + "/documents")
                        .file(file)
                        .param("stageId", stageId)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stageId").value(stageId));

        mockMvc.perform(get("/api/disputes/" + disputeId + "/documents")
                        .param("stageId", stageId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    private String createDisputeViaMvc(String token, String title) throws Exception {
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String reqBody = """
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
        MvcResult res = mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/disputes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(reqBody))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private UUID requirePersonId(String email) {
        return personRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("missing person " + email))
                .getId();
    }

    private String obtainToken(String email, String password) throws Exception {
        String r = mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(r).get("accessToken").asText();
    }
}
