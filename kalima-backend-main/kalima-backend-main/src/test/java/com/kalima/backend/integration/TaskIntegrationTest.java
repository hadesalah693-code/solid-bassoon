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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Tasks module — API integration")
class TaskIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersonRepository personRepository;

    @Test
    void createListGetUpdateTask() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseNumber = "TASK-INT-" + UUID.randomUUID();
        String caseBody = """
                {
                  "caseNumber": "%s",
                  "title": "Task IT case",
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

        String createTask = """
                {
                  "title": "مراجعة مذكرة",
                  "description": "قبل الجلسة",
                  "priority": "HIGH",
                  "status": "TODO",
                  "dueDate": "2026-09-01T12:00:00",
                  "assignedToId": "%s"
                }
                """.formatted(lawyerId);

        MvcResult created = mockMvc.perform(post("/api/cases/" + caseId + "/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(createTask))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.caseId").value(caseId))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.assignedTo.id").value(lawyerId.toString()))
                .andReturn();
        String taskId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/cases/" + caseId + "/tasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(taskId));

        mockMvc.perform(get("/api/cases/" + caseId + "/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("مراجعة مذكرة"));

        String updateBody = """
                {
                  "title": "مراجعة مذكرة — محدث",
                  "description": "تم",
                  "priority": "MEDIUM",
                  "status": "IN_PROGRESS",
                  "dueDate": "2026-09-02T12:00:00",
                  "assignedToId": "%s"
                }
                """.formatted(lawyerId);
        mockMvc.perform(put("/api/cases/" + caseId + "/tasks/" + taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void getTask_wrongCaseId_returnsNotFound() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        Pair a = createCaseWithTask(token);
        Pair b = createCaseWithTask(token);
        assertThat(b.caseId).isNotEqualTo(a.caseId);
        mockMvc.perform(get("/api/cases/" + b.caseId + "/tasks/" + a.taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void createTask_archivedCase_returnsConflict() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        String caseId = createBareCaseId(token);
        mockMvc.perform(delete("/api/cases/" + caseId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        String body = """
                {
                  "title": "T",
                  "description": null,
                  "priority": "LOW",
                  "status": "TODO",
                  "dueDate": null,
                  "assignedToId": null
                }
                """;
        mockMvc.perform(post("/api/cases/" + caseId + "/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void createTask_unknownAssignee_returnsBadRequest() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        String caseId = createBareCaseId(token);
        String body = """
                {
                  "title": "T",
                  "description": null,
                  "priority": "LOW",
                  "status": "TODO",
                  "dueDate": null,
                  "assignedToId": "00000000-0000-0000-0000-00000000dead"
                }
                """;
        mockMvc.perform(post("/api/cases/" + caseId + "/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    private Pair createCaseWithTask(String token) throws Exception {
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseNumber = "TASK-INT-" + UUID.randomUUID();
        String caseBody = """
                {
                  "caseNumber": "%s",
                  "title": "Task case",
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
        String createTask = """
                {
                  "title": "T",
                  "description": null,
                  "priority": "LOW",
                  "status": "TODO",
                  "dueDate": null,
                  "assignedToId": null
                }
                """;
        MvcResult t = mockMvc.perform(post("/api/cases/" + caseId + "/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(createTask))
                .andExpect(status().isCreated())
                .andReturn();
        String taskId = objectMapper.readTree(t.getResponse().getContentAsString()).get("id").asText();
        return new Pair(caseId, taskId);
    }

    private String createBareCaseId(String token) throws Exception {
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseNumber = "TASK-INT-" + UUID.randomUUID();
        String caseBody = """
                {
                  "caseNumber": "%s",
                  "title": "Task case",
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
        return objectMapper.readTree(caseRes.getResponse().getContentAsString()).get("id").asText();
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
                .orElseThrow()
                .getId();
    }

    private record Pair(String caseId, String taskId) {
    }
}
