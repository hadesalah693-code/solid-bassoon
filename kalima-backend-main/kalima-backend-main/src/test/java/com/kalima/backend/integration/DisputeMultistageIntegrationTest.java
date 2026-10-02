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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Dispute multi-stage — stages, updates, case links API")
class DisputeMultistageIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersonRepository personRepository;

    @Test
    void stages_crud_listAfterAdd() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        String disputeId = createDispute(token, "Stage list case");

        String addStage = """
                {
                  "stageType": "NEGOTIATION",
                  "status": "ACTIVE",
                  "title": "تفاوض",
                  "startedAt": "2026-05-17T10:00:00",
                  "closedAt": null
                }
                """;

        mockMvc.perform(post("/api/disputes/" + disputeId + "/stages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(addStage))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.stageType").value("NEGOTIATION"))
                .andExpect(jsonPath("$.title").value("تفاوض"));

        mockMvc.perform(get("/api/disputes/" + disputeId + "/stages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].disputeId").value(disputeId))
                .andExpect(jsonPath("$[0].stageType").value("NEGOTIATION"));
    }

    @Test
    void administrativeUpdates_addAndList_orderedDesc() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        String disputeId = createDispute(token, "Admin updates case");

        String body = """
                {
                  "updateType": "INTERNAL_NOTE",
                  "title": "اجتماع متابعة",
                  "description": "تم الاتفاق على موعد لاحق",
                  "occurredAt": "2026-05-17T14:00:00",
                  "stageId": null
                }
                """;

        mockMvc.perform(post("/api/disputes/" + disputeId + "/updates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("اجتماع متابعة"))
                .andExpect(jsonPath("$.updateType").value("INTERNAL_NOTE"));

        mockMvc.perform(get("/api/disputes/" + disputeId + "/updates")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("اجتماع متابعة"))
                .andExpect(jsonPath("$[0].createdBy.id").isNotEmpty());
    }

    @Test
    void convertToCase_createsConvertedLink_stageAndAdmin_secondCallIdempotent() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        String disputeId = createDispute(token, "Convert multistage IT");

        MvcResult conv1 = mockMvc.perform(post("/api/disputes/" + disputeId + "/convert-to-case")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONVERTED_TO_CASE"))
                .andExpect(jsonPath("$.convertedCaseId").isNotEmpty())
                .andReturn();
        String caseId = objectMapper.readTree(conv1.getResponse().getContentAsString())
                .get("convertedCaseId").asText();

        mockMvc.perform(get("/api/disputes/" + disputeId + "/cases")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].caseId").value(caseId))
                .andExpect(jsonPath("$[0].linkRole").value("CONVERTED"));

        mockMvc.perform(get("/api/disputes/" + disputeId + "/stages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].stageType").value("COURT_CASE"))
                .andExpect(jsonPath("$[0].title").value("تحويل إلى قضية"));

        mockMvc.perform(get("/api/disputes/" + disputeId + "/updates")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].updateType").value("CONVERTED_TO_CASE"))
                .andExpect(jsonPath("$[0].title").value("تم تحويل النزاع إلى قضية"));

        mockMvc.perform(post("/api/disputes/" + disputeId + "/convert-to-case")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convertedCaseId").value(caseId));

        mockMvc.perform(get("/api/disputes/" + disputeId + "/cases")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].caseId").value(caseId));

        mockMvc.perform(get("/api/disputes/" + disputeId + "/stages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/disputes/" + disputeId + "/updates")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void caseLinks_link_duplicateConflict_unlink_listEmpty() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String disputeId = createDispute(token, "Link case dispute");

        String caseNumber = "DISP-LINK-" + UUID.randomUUID();
        String caseBody = """
                {
                  "caseNumber": "%s",
                  "title": "Linked from dispute IT",
                  "clientId": "%s",
                  "assignedLawyerId": "%s"
                }
                """.formatted(caseNumber, clientId, lawyerId);
        MvcResult caseRes = mockMvc.perform(post("/api/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(caseBody))
                .andExpect(status().isCreated())
                .andReturn();
        String caseId = objectMapper.readTree(caseRes.getResponse().getContentAsString()).get("id").asText();

        String linkPayload = """
                { "caseId": "%s", "linkRole": "PRIMARY" }
                """.formatted(caseId);

        mockMvc.perform(post("/api/disputes/" + disputeId + "/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(linkPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseId").value(caseId));

        mockMvc.perform(post("/api/disputes/" + disputeId + "/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(linkPayload))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/disputes/" + disputeId + "/cases")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].caseId").value(caseId));

        mockMvc.perform(delete("/api/disputes/" + disputeId + "/cases/" + caseId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/disputes/" + disputeId + "/cases")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    private String createDispute(String token, String title) throws Exception {
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String body = """
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
        MvcResult res = mockMvc.perform(post("/api/disputes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
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

    private UUID requirePersonId(String email) {
        return personRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Missing seeded person: " + email))
                .getId();
    }
}
