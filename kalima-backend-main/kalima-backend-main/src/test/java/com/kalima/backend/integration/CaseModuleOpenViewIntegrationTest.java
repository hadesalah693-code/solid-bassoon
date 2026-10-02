package com.kalima.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.domain.repository.PersonRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises case read paths with {@code spring.jpa.open-in-view=false} (set in
 * application.properties). Mapping runs inside {@code CaseService} transactions;
 * these calls must return 200 with nested party summaries, not 500 from
 * {@link org.hibernate.LazyInitializationException}.
 */
@DisplayName("Cases module — read paths (OSIV off)")
class CaseModuleOpenViewIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersonRepository personRepository;

    @Test
    void listSearchDetailParticipantsHearingsReturnOkWithPartyData() throws Exception {
        String token = obtainAccessToken(
                KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        UUID witnessId = requirePersonId(KalimaTestDataSeeder.WITNESS_EMAIL);

        String caseNumber = "OSIV-CASE-" + UUID.randomUUID();
        String createCase = """
                {
                  "caseNumber": "%s",
                  "title": "OSIV list/detail test",
                  "clientId": "%s",
                  "assignedLawyerId": "%s"
                }
                """.formatted(caseNumber, clientId, lawyerId);
        ResultActions create = mockMvc.perform(post("/api/cases")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .content(createCase));
        create.andExpect(status().isCreated());
        String caseId = objectMapper.readTree(
                        create.andReturn().getResponse().getContentAsString())
                .get("id")
                .asText();

        // List + text search: exercises searchWithText + @EntityGraph (party columns per row)
        mockMvc.perform(get("/api/cases")
                        .param("search", caseNumber)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(caseId))
                .andExpect(jsonPath("$.content[0].client.id").value(clientId.toString()))
                .andExpect(jsonPath("$.content[0].assignedLawyer.id").value(lawyerId.toString()))
                .andExpect(jsonPath("$.content[0].createdBy.id").isNotEmpty());

        // Unfiltered list (no search text): exercises searchWithoutText + @EntityGraph
        mockMvc.perform(get("/api/cases")
                        .param("sort", "createdAt,desc")
                        .param("size", "5")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(caseId))
                .andExpect(jsonPath("$.content[0].client.id").value(clientId.toString()))
                .andExpect(jsonPath("$.content[0].createdBy.id").isNotEmpty());

        // Single case: findScopedById + graph
        mockMvc.perform(get("/api/cases/" + caseId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client.id").value(clientId.toString()))
                .andExpect(jsonPath("$.assignedLawyer.id").value(lawyerId.toString()))
                .andExpect(jsonPath("$.createdBy.id").isNotEmpty());

        String addParticipant = """
                { "personId": "%s", "role": "WITNESS" }
                """.formatted(witnessId);
        mockMvc.perform(post("/api/cases/" + caseId + "/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(addParticipant))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/cases/" + caseId + "/participants")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].person.id", hasItem(witnessId.toString())));

        mockMvc.perform(get("/api/cases/" + caseId + "/hearings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    private String obtainAccessToken(String email, String password) throws Exception {
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
}
