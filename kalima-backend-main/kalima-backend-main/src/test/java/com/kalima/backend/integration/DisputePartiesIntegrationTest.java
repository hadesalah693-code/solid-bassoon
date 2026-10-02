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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Dispute parties API")
class DisputePartiesIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersonRepository personRepository;

    @Test
    void parties_addFreeText_list_patch_blockedAfterConversion() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        String disputeId = createDispute(token, "Parties IT dispute");

        String addFree = """
                {
                  "role": "OTHER",
                  "displayName": "وسيط طرف ثالث",
                  "phone": "01001234567",
                  "sortOrder": 10
                }
                """;
        MvcResult freeRes = mockMvc.perform(post("/api/disputes/" + disputeId + "/parties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(addFree))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("OTHER"))
                .andExpect(jsonPath("$.displayName").value("وسيط طرف ثالث"))
                .andReturn();
        String freePartyId = objectMapper.readTree(freeRes.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/disputes/" + disputeId + "/parties")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String addLawyerWitness = """
                {
                  "role": "WITNESS",
                  "personId": "%s",
                  "notes": "شاهد",
                  "sortOrder": 0
                }
                """.formatted(lawyerId);
        mockMvc.perform(post("/api/disputes/" + disputeId + "/parties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(addLawyerWitness))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.person.id").value(lawyerId.toString()));

        mockMvc.perform(get("/api/disputes/" + disputeId + "/parties")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        String patchNotes = "{\"notes\":\"تحديث\"}";
        JsonNode lawyerParty = loadPartyByPersonId(token, disputeId, lawyerId);
        mockMvc.perform(patch("/api/disputes/" + disputeId + "/parties/" + lawyerParty.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(patchNotes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("تحديث"));

        mockMvc.perform(delete("/api/disputes/" + disputeId + "/parties/" + freePartyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/disputes/" + disputeId + "/parties")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(post("/api/disputes/" + disputeId + "/convert-to-case")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/disputes/" + disputeId + "/parties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(addFree))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/disputes/" + disputeId + "/parties/" + lawyerParty.get("id").asText())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void coClient_rejects_primaryClient_and_nonClientPerson() throws Exception {
        String token = obtainToken(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        UUID clientId = requirePersonId(KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(KalimaTestDataSeeder.LAWYER_EMAIL);
        String disputeId = createDispute(token, "Co-client constraints");

        String dupPrimary = """
                {
                  "role": "CO_CLIENT",
                  "personId": "%s"
                }
                """.formatted(clientId);
        mockMvc.perform(post("/api/disputes/" + disputeId + "/parties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(dupPrimary))
                .andExpect(status().isBadRequest());

        String lawyerAsCoClient = """
                {
                  "role": "CO_CLIENT",
                  "personId": "%s"
                }
                """.formatted(lawyerId);
        mockMvc.perform(post("/api/disputes/" + disputeId + "/parties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(lawyerAsCoClient))
                .andExpect(status().isBadRequest());
    }

    private JsonNode loadPartyByPersonId(String token, String disputeId, UUID personId) throws Exception {
        JsonNode arr = loadParties(token, disputeId);
        for (JsonNode row : arr) {
            JsonNode person = row.get("person");
            if (person != null && personId.toString().equals(person.get("id").asText())) {
                return row;
            }
        }
        throw new IllegalStateException("Party not found for person " + personId);
    }

    private JsonNode loadParties(String token, String disputeId) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/disputes/" + disputeId + "/parties")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString());
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
