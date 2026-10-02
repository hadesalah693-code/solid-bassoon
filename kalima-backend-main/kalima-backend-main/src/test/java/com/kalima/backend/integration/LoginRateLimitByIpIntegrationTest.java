package com.kalima.backend.integration;

import com.kalima.backend.auth.ratelimit.LoginRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Login rate limit — by client IP")
@TestPropertySource(
        properties = {
                "kalima.auth.login.max-attempts-per-window-by-ip=2",
                "kalima.auth.login.max-attempts-per-window-by-email=10000"
        }
)
class LoginRateLimitByIpIntegrationTest extends AbstractPostgresSpringBootTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private LoginRateLimiter loginRateLimiter;

    @BeforeEach
    void clearLimiter() {
        loginRateLimiter.resetInMemoryState();
    }

    @Test
    void thirdLoginFromSameIpReturns429() throws Exception {
        String body = badLoginJson(KalimaTestDataSeeder.ADMIN_EMAIL);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void otherEndpointsNotSubjectToLoginLimiter() throws Exception {
        String body = badLoginJson(KalimaTestDataSeeder.ADMIN_EMAIL);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(get("/api/cases"))
                .andExpect(status().isUnauthorized());
    }

    private static String badLoginJson(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"WrongPassword1\"}";
    }
}
