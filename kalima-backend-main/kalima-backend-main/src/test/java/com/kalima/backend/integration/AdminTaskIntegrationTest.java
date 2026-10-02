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

@DisplayName("Admin tasks — tenant-wide API")
class AdminTaskIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersonRepository personRepository;

    @Test
    void myOpenCountAndUnassignedFilter() throws Exception {
        String adminToken = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        String lawyerToken = obtainToken(KalimaTestDataSeeder.LAWYER_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);

        String unassignedBody = """
                {
                  "title": "بدون تعيين",
                  "description": null,
                  "priority": "LOW",
                  "status": "TODO",
                  "dueDate": null,
                  "assignedToId": null,
                  "caseId": null,
                  "disputeId": null
                }
                """;
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content(unassignedBody))
                .andExpect(status().isCreated());

        String assignedBody = """
                {
                  "title": "مفتوحة للمحامي",
                  "description": null,
                  "priority": "MEDIUM",
                  "status": "TODO",
                  "dueDate": null,
                  "assignedToId": "%s",
                  "caseId": null,
                  "disputeId": null
                }
                """.formatted(lawyerId);
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content(assignedBody))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/tasks/my-open-count")
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("unassignedOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.title=='بدون تعيين')]").exists());
    }

    @Test
    void completedAtSetWhenMarkedDone() throws Exception {
        String adminToken = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);

        String createBody = """
                {
                  "title": "للإنجاز",
                  "description": null,
                  "priority": "LOW",
                  "status": "TODO",
                  "dueDate": null,
                  "assignedToId": "%s",
                  "caseId": null,
                  "disputeId": null
                }
                """.formatted(lawyerId);

        MvcResult created = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.completedAt").isEmpty())
                .andReturn();
        String taskId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        String updateBody = """
                {
                  "title": "للإنجاز",
                  "description": null,
                  "priority": "LOW",
                  "status": "DONE",
                  "dueDate": null,
                  "assignedToId": "%s",
                  "caseId": null,
                  "disputeId": null
                }
                """.formatted(lawyerId);

        mockMvc.perform(put("/api/tasks/" + taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
    }

    @Test
    void assigneeReceivesNotificationOnCreate() throws Exception {
        String adminToken = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        String lawyerToken = obtainToken(KalimaTestDataSeeder.LAWYER_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);

        String createBody = """
                {
                  "title": "مهمة للمحامي",
                  "description": null,
                  "priority": "MEDIUM",
                  "status": "TODO",
                  "dueDate": null,
                  "assignedToId": "%s",
                  "caseId": null,
                  "disputeId": null
                }
                """.formatted(lawyerId);

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content(createBody))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.type=='TASK_ASSIGNED')]").exists())
                .andExpect(jsonPath("$.content[?(@.title=='مهمة جديدة')]").exists());
    }

    @Test
    void createStandaloneListAndDelete() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);

        String createBody = """
                {
                  "title": "مهمة إدارية",
                  "description": "بدون قضية",
                  "priority": "MEDIUM",
                  "status": "TODO",
                  "dueDate": null,
                  "assignedToId": "%s",
                  "caseId": null,
                  "disputeId": null
                }
                """.formatted(lawyerId);

        MvcResult created = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.caseId").isEmpty())
                .andExpect(jsonPath("$.disputeId").isEmpty())
                .andExpect(jsonPath("$.title").value("مهمة إدارية"))
                .andReturn();
        String taskId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .param("status", "TODO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id=='" + taskId + "')]").exists());

        mockMvc.perform(delete("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void createWithCaseLink() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        String caseId = createBareCaseId(token);

        String createBody = """
                {
                  "title": "مرتبطة بقضية",
                  "description": null,
                  "priority": "LOW",
                  "status": "TODO",
                  "dueDate": null,
                  "assignedToId": null,
                  "caseId": "%s",
                  "disputeId": null
                }
                """.formatted(caseId);

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseId").value(caseId))
                .andExpect(jsonPath("$.caseNumber").isNotEmpty());
    }

    @Test
    void caseScopedDelete() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        Pair pair = createCaseWithTask(token);

        mockMvc.perform(delete("/api/cases/" + pair.caseId() + "/tasks/" + pair.taskId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/cases/" + pair.caseId() + "/tasks/" + pair.taskId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private Pair createCaseWithTask(String token) throws Exception {
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseNumber = "ADM-TASK-" + UUID.randomUUID();
        String caseBody = """
                {
                  "caseNumber": "%s",
                  "title": "Admin task case",
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
        String caseNumber = "ADM-TASK-" + UUID.randomUUID();
        String caseBody = """
                {
                  "caseNumber": "%s",
                  "title": "Case",
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
