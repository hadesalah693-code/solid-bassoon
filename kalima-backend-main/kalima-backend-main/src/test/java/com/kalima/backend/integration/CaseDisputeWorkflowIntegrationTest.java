package com.kalima.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.disputeevent.repository.DisputeEventRepository;
import com.kalima.backend.disputeevent.service.DisputeWorkflowInitialEventsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Case dispute path — auto workflow seed integration")
class CaseDisputeWorkflowIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private DisputeEventRepository disputeEventRepository;
    @Autowired
    private CaseFileRepository caseFileRepository;
    @Autowired
    private DisputeWorkflowInitialEventsService disputeWorkflowInitialEventsService;

    private UUID requirePersonId(String email) {
        return personRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Missing seeded person: " + email))
                .getId();
    }

    @Test
    void create_withoutDisputePathType_noEventsStored() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL,
                KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseNumber = "WF-NONE-" + UUID.randomUUID();
        String caseBody = """
                {
                  "caseNumber": "%s",
                  "title": "No path preset",
                  "clientId": "%s",
                  "assignedLawyerId": "%s"
                }
                """.formatted(caseNumber, clientId, lawyerId);
        MvcResult caseRes = mockMvc.perform(post("/api/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(caseBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.disputePathType").value(nullValue()))
                .andReturn();
        String caseId = objectMapper.readTree(caseRes.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(get("/api/cases/" + caseId + "/dispute-events")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void create_withCivil_seedsEightEvents_orderedAndTyped() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL,
                KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseNumber = "WF-CIV-" + UUID.randomUUID();
        String caseBody = """
                {
                  "caseNumber": "%s",
                  "title": "Civil preset",
                  "clientId": "%s",
                  "assignedLawyerId": "%s",
                  "disputePathType": "CIVIL"
                }
                """.formatted(caseNumber, clientId, lawyerId);
        MvcResult caseRes = mockMvc.perform(post("/api/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(caseBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.disputePathType").value("CIVIL"))
                .andReturn();
        String caseId = objectMapper.readTree(caseRes.getResponse().getContentAsString())
                .get("id").asText();
        UUID caseUuid = UUID.fromString(caseId);

        mockMvc.perform(get("/api/cases/" + caseId + "/dispute-events")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(8))
                .andExpect(jsonPath("$.content[0].caseId").value(caseId))
                .andExpect(jsonPath("$.content[0].title").value("فتح القضية"))
                .andExpect(jsonPath("$.content[0].type").value("CASE_OPENED"))
                .andExpect(jsonPath("$.content[0].status").value("DONE"))
                .andExpect(jsonPath("$.content[0].sortOrder").value(0))
                .andExpect(jsonPath("$.content[7].title").value("تنفيذ الحكم"))
                .andExpect(jsonPath("$.content[7].sortOrder").value(7));

        CaseFile cf = caseFileRepository.findScopedById(caseUuid).orElseThrow();
        assertThat(cf.getDisputePathType()).isEqualTo(DomainEnums.DisputePathType.CIVIL);
    }

    @Test
    void create_customPath_noPresetEvents() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL,
                KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseNumber = "WF-CUSTOM-" + UUID.randomUUID();
        String caseBody = """
                {
                  "caseNumber": "%s",
                  "title": "Custom path",
                  "clientId": "%s",
                  "assignedLawyerId": "%s",
                  "disputePathType": "CUSTOM"
                }
                """.formatted(caseNumber, clientId, lawyerId);
        MvcResult caseRes = mockMvc.perform(post("/api/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(caseBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.disputePathType").value("CUSTOM"))
                .andReturn();
        String caseId = objectMapper.readTree(caseRes.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(get("/api/cases/" + caseId + "/dispute-events")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void workflowSeed_twice_secondCallNoDuplicates() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL,
                KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseNumber = "WF-IDEM-" + UUID.randomUUID();
        String caseBody = """
                {
                  "caseNumber": "%s",
                  "title": "Idempotent workflow",
                  "clientId": "%s",
                  "assignedLawyerId": "%s",
                  "disputePathType": "COMMERCIAL"
                }
                """.formatted(caseNumber, clientId, lawyerId);
        MvcResult caseRes = mockMvc.perform(post("/api/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(caseBody))
                .andExpect(status().isCreated())
                .andReturn();
        UUID caseUuid = UUID.fromString(
                objectMapper.readTree(caseRes.getResponse().getContentAsString())
                        .get("id").asText());

        long afterCreate = disputeEventRepository.countByCaseFile_Id(caseUuid);
        assertThat(afterCreate).isEqualTo(7);

        CaseFile cf = caseFileRepository.findScopedById(caseUuid).orElseThrow();
        disputeWorkflowInitialEventsService.seedIfApplicable(cf, DomainEnums.DisputePathType.COMMERCIAL);
        disputeWorkflowInitialEventsService.seedIfApplicable(cf, DomainEnums.DisputePathType.COMMERCIAL);

        assertThat(disputeEventRepository.countByCaseFile_Id(caseUuid)).isEqualTo(7);
    }

    @Test
    void disputeEventsCrud_unchangedAfterWorkflowSeed() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL,
                KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseNumber = "WF-CRUD-" + UUID.randomUUID();
        String caseBody = """
                {
                  "caseNumber": "%s",
                  "title": "CRUD after seed",
                  "clientId": "%s",
                  "assignedLawyerId": "%s",
                  "disputePathType": "LABOR"
                }
                """.formatted(caseNumber, clientId, lawyerId);
        MvcResult caseRes = mockMvc.perform(post("/api/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(caseBody))
                .andExpect(status().isCreated())
                .andReturn();
        String caseId = objectMapper.readTree(caseRes.getResponse().getContentAsString())
                .get("id").asText();

        JsonNode page = objectMapper.readTree(mockMvc.perform(
                        get("/api/cases/" + caseId + "/dispute-events")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String eventId = page.get("content").get(3).get("id").asText();

        String patchBody = """
                {
                  "type": "FIRST_HEARING",
                  "status": "CURRENT",
                  "title": "أول جلسة — تم التحرير",
                  "summary": null,
                  "eventDate": null,
                  "sortOrder": 3
                }
                """;
        mockMvc.perform(patch("/api/cases/" + caseId + "/dispute-events/" + eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(patchBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CURRENT"))
                .andExpect(jsonPath("$.title").value("أول جلسة — تم التحرير"));

        mockMvc.perform(delete("/api/cases/" + caseId + "/dispute-events/" + eventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/cases/" + caseId + "/dispute-events")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6));
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
