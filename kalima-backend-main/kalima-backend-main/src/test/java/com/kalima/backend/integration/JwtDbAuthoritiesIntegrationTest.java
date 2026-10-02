package com.kalima.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.PersonRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ensures Spring Security authorities for JWT requests come from the database
 * {@code system_role}, not from a stale {@code role} claim in the token.
 */
@DisplayName("JWT + DB-backed authorities")
class JwtDbAuthoritiesIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersonRepository personRepository;

    @Test
    void demotionInDbRemovesPrivilegesFromSameJwt() throws Exception {
        String token = obtainAccessToken(
                KalimaTestDataSeeder.MUTABLE_ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        Person p = personRepository.findByEmailWithCompany(KalimaTestDataSeeder.MUTABLE_ADMIN_EMAIL)
                .orElseThrow();
        try {
            p.setSystemRole(DomainEnums.SystemRole.LAWYER);
            personRepository.saveAndFlush(p);

            mockMvc.perform(get("/api/audit-logs")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        } finally {
            p.setSystemRole(DomainEnums.SystemRole.ADMIN);
            personRepository.saveAndFlush(p);
        }
    }

    @Test
    void promotionInDbGrantsPrivilegesWithSameJwt() throws Exception {
        String token = obtainAccessToken(
                KalimaTestDataSeeder.MUTABLE_LAWYER_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD);
        Person p = personRepository.findByEmailWithCompany(KalimaTestDataSeeder.MUTABLE_LAWYER_EMAIL)
                .orElseThrow();
        try {
            p.setSystemRole(DomainEnums.SystemRole.ADMIN);
            personRepository.saveAndFlush(p);

            mockMvc.perform(get("/api/audit-logs")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        } finally {
            p.setSystemRole(DomainEnums.SystemRole.LAWYER);
            personRepository.saveAndFlush(p);
        }
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
}
