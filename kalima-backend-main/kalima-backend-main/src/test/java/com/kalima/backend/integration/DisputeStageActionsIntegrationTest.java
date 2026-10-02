package com.kalima.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.domain.repository.PersonRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Dispute stage actions API")
class DisputeStageActionsIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersonRepository personRepository;

    @Test
    void general_action_create_list_summary_complete_cancel() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        String disputeId = createDisputeViaMvc(token, "Actions IT " + UUID.randomUUID());

        String pastDue = LocalDateTime.now().minusDays(2).toString();
        String body = """
                {
                  "stageId": null,
                  "actionType": "CALL",
                  "title": "مكالمة متابعة",
                  "description": "تواصل مع الطرف الآخر",
                  "priority": "HIGH",
                  "dueAt": "%s",
                  "assignedTo": null
                }
                """.formatted(pastDue);

        MvcResult created = mockMvc.perform(post("/api/disputes/" + disputeId + "/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.overdue").value(true))
                .andExpect(jsonPath("$.stageId").value(nullValue()))
                .andReturn();

        String actionId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/disputes/" + disputeId + "/actions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(actionId));

        mockMvc.perform(get("/api/disputes/" + disputeId + "/actions/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openCount").value(1))
                .andExpect(jsonPath("$.overdueCount").value(1));

        mockMvc.perform(post("/api/disputes/" + disputeId + "/actions/" + actionId + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"completionNote\":\"تم الاتصال بالخصم\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.completionNote").value("تم الاتصال بالخصم"));

        mockMvc.perform(get("/api/disputes/" + disputeId + "/actions/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doneCount").value(1));

        String body2 = """
                {
                  "stageId": null,
                  "actionType": "DOCUMENT",
                  "title": "مهمة ثانية",
                  "priority": "LOW",
                  "dueAt": null,
                  "assignedTo": null
                }
                """;

        MvcResult created2 = mockMvc.perform(post("/api/disputes/" + disputeId + "/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body2))
                .andExpect(status().isCreated())
                .andReturn();
        String action2 = objectMapper.readTree(created2.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/disputes/" + disputeId + "/actions/" + action2 + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void action_linked_to_stage_and_reject_foreign_stage() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        String disputeId = createDisputeViaMvc(token, "Stage link IT " + UUID.randomUUID());

        String stageBody = """
                { "stageType": "LEGAL_NOTICE", "status": "ACTIVE", "title": "إنذار" }
                """;
        MvcResult stageRes = mockMvc.perform(post("/api/disputes/" + disputeId + "/stages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(stageBody))
                .andExpect(status().isCreated())
                .andReturn();
        String stageId = objectMapper.readTree(stageRes.getResponse().getContentAsString()).get("id").asText();

        String disputeB = createDisputeViaMvc(token, "Other dispute " + UUID.randomUUID());
        String stageBodyB = """
                { "stageType": "OTHER", "status": "ACTIVE", "title": "مرحلة أخرى" }
                """;
        MvcResult stageBRes = mockMvc.perform(post("/api/disputes/" + disputeB + "/stages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(stageBodyB))
                .andExpect(status().isCreated())
                .andReturn();
        String stageIdB = objectMapper.readTree(stageBRes.getResponse().getContentAsString()).get("id").asText();

        String bad = """
                {
                  "stageId": "%s",
                  "actionType": "MEETING",
                  "title": "يجب أن يفشل",
                  "priority": "MEDIUM"
                }
                """.formatted(stageIdB);

        mockMvc.perform(post("/api/disputes/" + disputeId + "/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(bad))
                .andExpect(status().isBadRequest());

        String ok = """
                {
                  "stageId": "%s",
                  "actionType": "MEETING",
                  "title": "اجتماع إنذار",
                  "priority": "URGENT"
                }
                """.formatted(stageId);

        mockMvc.perform(post("/api/disputes/" + disputeId + "/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(ok))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stageId").value(stageId));

        mockMvc.perform(get("/api/disputes/" + disputeId + "/stages/" + stageId + "/actions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("اجتماع إنذار"));
    }

    @Test
    void patch_status_done_sets_completed_at_reopen_clears() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        String disputeId = createDisputeViaMvc(token, "Patch IT " + UUID.randomUUID());
        String body = """
                {
                  "actionType": "FOLLOW_UP",
                  "title": "Patch flow",
                  "priority": "MEDIUM"
                }
                """;
        MvcResult res = mockMvc.perform(post("/api/disputes/" + disputeId + "/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode n = objectMapper.readTree(res.getResponse().getContentAsString());
        String actionId = n.get("id").asText();
        assertThat(n.get("completedAt").isNull()).isTrue();

        mockMvc.perform(patch("/api/disputes/" + disputeId + "/actions/" + actionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"status\":\"DONE\",\"completionNote\":\"اكتمل العمل\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.completionNote").value("اكتمل العمل"));

        mockMvc.perform(patch("/api/disputes/" + disputeId + "/actions/" + actionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.completedAt").value(nullValue()));
    }

    @Test
    void complete_action_without_completion_note_is_bad_request() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        String disputeId = createDisputeViaMvc(token, "Complete note IT " + UUID.randomUUID());
        String body = """
                {
                  "actionType": "CALL",
                  "title": "بدون ملاحظة",
                  "priority": "MEDIUM"
                }
                """;
        MvcResult res = mockMvc.perform(post("/api/disputes/" + disputeId + "/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        String actionId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/disputes/" + disputeId + "/actions/" + actionId + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void closing_stage_requires_note_and_creates_stage_linked_update() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        String disputeId = createDisputeViaMvc(token, "Stage close IT " + UUID.randomUUID());
        String stageBody = """
                { "stageType": "LEGAL_NOTICE", "status": "ACTIVE", "title": "إنذار" }
                """;
        MvcResult stageRes = mockMvc.perform(post("/api/disputes/" + disputeId + "/stages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(stageBody))
                .andExpect(status().isCreated())
                .andReturn();
        String stageId = objectMapper.readTree(stageRes.getResponse().getContentAsString()).get("id").asText();

        String closedAt = LocalDateTime.now().withNano(0).toString();
        mockMvc.perform(patch("/api/disputes/" + disputeId + "/stages/" + stageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"status\":\"CLOSED\",\"closedAt\":\"" + closedAt + "\"}"))
                .andExpect(status().isBadRequest());

        String closingNote = "تم الانتهاء من الإنذار وتوثيق الرد";
        mockMvc.perform(patch("/api/disputes/" + disputeId + "/stages/" + stageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"status\":\"CLOSED\",\"closedAt\":\"" + closedAt + "\",\"closingNote\":\"" + closingNote + "\"}"))
                .andExpect(status().isBadRequest());

        String patchJson = """
                {"status":"CLOSED","closedAt":"%s","closingNote":"%s","outcome":"SUCCESS","outcomeNote":"ملاحظة إضافية"}
                """.formatted(closedAt, closingNote);
        mockMvc.perform(patch("/api/disputes/" + disputeId + "/stages/" + stageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(patchJson))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/disputes/" + disputeId + "/stages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].outcome").value("SUCCESS"))
                .andExpect(jsonPath("$[0].outcomeNote").value("ملاحظة إضافية"));

        mockMvc.perform(get("/api/disputes/" + disputeId + "/updates")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].updateType").value("STAGE_CLOSED"))
                .andExpect(jsonPath("$[0].stageId").value(stageId))
                .andExpect(jsonPath("$[0].description").value(containsString("نجحت")))
                .andExpect(jsonPath("$[0].description").value(containsString(closingNote)))
                .andExpect(jsonPath("$[0].description").value(containsString("ملاحظة إضافية")));
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
        MvcResult res = mockMvc.perform(post("/api/disputes")
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
