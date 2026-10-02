package com.kalima.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.auth.entity.RefreshToken;
import com.kalima.backend.auth.repository.RefreshTokenRepository;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.PersonRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the Refresh Token rotation system.
 *
 * <p>Covers three scenarios:
 * <ol>
 *   <li><b>Happy path</b> — login issues a cookie; rotation returns a new access
 *       token and a new cookie.</li>
 *   <li><b>Sequential reuse</b> — presenting an already-used token triggers reuse
 *       detection: the whole family is revoked and the successor token is also
 *       immediately invalidated.</li>
 *   <li><b>Concurrent reuse (TOCTOU proof)</b> — two threads fire the same raw
 *       token at {@code POST /api/auth/refresh} simultaneously.  The
 *       {@code SELECT … FOR UPDATE} lock ensures at most one rotation succeeds;
 *       the second thread reads the committed {@code usedAt} value, detects
 *       reuse, and returns 401.  Zero active tokens remain in the family after
 *       both requests settle.</li>
 * </ol>
 *
 * <p>The concurrent test uses {@link TestRestTemplate} (real HTTP over a random
 * port) so both threads run on separate Tomcat worker threads with independent
 * JDBC connections — a realistic simulation of two overlapping client retries.
 */
@DisplayName("Refresh Token Rotation — rotation, reuse detection & SELECT FOR UPDATE")
class RefreshTokenRotationTest extends AbstractPostgresSpringBootTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired PersonRepository personRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired TestRestTemplate restTemplate;
    @LocalServerPort int port;

    // ─── 1. Login issues HttpOnly cookie ─────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/login sets HttpOnly refresh_token cookie with correct attributes")
    void loginSetsRefreshCookie() throws Exception {
        MvcResult r = doLoginMvc(KalimaTestDataSeeder.ADMIN_EMAIL);

        String setCookie = setCookieHeader(r);
        assertThat(setCookie).contains("refresh_token=");
        assertThat(setCookie).containsIgnoringCase("HttpOnly");
        assertThat(setCookie).containsIgnoringCase("SameSite=Strict");
        assertThat(setCookie).containsIgnoringCase("Path=/api/auth");
    }

    // ─── 2. Rotation happy path ───────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/refresh rotates token: new access token + new cookie; old token rejected")
    void rotationHappyPath() throws Exception {
        MvcResult loginResult = doLoginMvc(KalimaTestDataSeeder.ADMIN_EMAIL);
        String rawOld = extractRefreshToken(loginResult);

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", rawOld)))
                .andExpect(status().isOk())
                .andReturn();

        // New access token is present
        JsonNode body = mapper.readTree(refreshResult.getResponse().getContentAsString());
        assertThat(body.get("accessToken").asText()).isNotBlank();

        // New refresh cookie must differ from the original
        String rawNew = extractRefreshToken(refreshResult);
        assertThat(rawNew).isNotEqualTo(rawOld);

        // Old token is now invalid (consumed)
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", rawOld)))
                .andExpect(status().isUnauthorized());
    }

    // ─── 3. Sequential reuse → family revocation ─────────────────────────────

    @Test
    @DisplayName("Presenting a used token revokes the whole family — successor token is also invalidated")
    void sequentialReuse_revokesEntireFamily() throws Exception {
        Instant testStart = Instant.now();
        MvcResult loginResult = doLoginMvc(KalimaTestDataSeeder.ADMIN_EMAIL);
        String rawOld = extractRefreshToken(loginResult);
        UUID personId = personId(KalimaTestDataSeeder.ADMIN_EMAIL);

        // First rotation: succeeds, issues successor
        MvcResult firstRefresh = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", rawOld)))
                .andExpect(status().isOk())
                .andReturn();
        String rawNew = extractRefreshToken(firstRefresh);

        // Reuse: present the OLD (already-used) token → reuse detected, family revoked, 401
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", rawOld)))
                .andExpect(status().isUnauthorized());

        // The SUCCESSOR token must now also be revoked (family-wide revocation)
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", rawNew)))
                .andExpect(status().isUnauthorized());

        // DB: no active token for this person issued during this test
        assertThat(activeFamilyTokenCount(personId, testStart))
                .as("Entire family must be revoked after reuse detection")
                .isZero();
    }

    // ─── 4. Concurrent rotation — TOCTOU window proof ────────────────────────

    /**
     * Two threads send {@code POST /api/auth/refresh} with the SAME raw token
     * at the same instant (CountDownLatch start-gun).
     *
     * <p><b>Without</b> the fix (plain {@code SELECT}):
     * Both threads could read {@code usedAt = null}, both rotate, and both
     * receive 200 — meaning two valid successor tokens exist simultaneously.
     *
     * <p><b>With</b> {@code SELECT … FOR UPDATE}:
     * <ul>
     *   <li>T1 acquires the row lock, sees {@code usedAt = null}, rotates,
     *       marks the slot used, issues T1_new, commits → 200.</li>
     *   <li>T2 blocks until T1 commits; it then reads the committed row
     *       ({@code usedAt} is now set), detects reuse, calls
     *       {@code revokeFamily} (which revokes T1_new), and throws 401.
     *       Thanks to {@code noRollbackFor = ResponseStatusException.class},
     *       the revocation is committed even though a 401 is returned.</li>
     * </ul>
     *
     * <p>Post-condition: zero active tokens remain in the family.
     */
    @Test
    @DisplayName("Concurrent rotation with the same token: SELECT FOR UPDATE allows at most one successor — TOCTOU closed")
    void concurrentRotation_selectForUpdatePreventsRace() throws InterruptedException {
        Instant testStart = Instant.now();

        // Login via real HTTP so we get a proper Set-Cookie header
        ResponseEntity<JsonNode> loginResp = restPost(
                "/api/auth/login",
                loginJson(KalimaTestDataSeeder.ADMIN_EMAIL, KalimaTestDataSeeder.PLAIN_PASSWORD),
                null);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        String rawToken = extractTokenFromSetCookieHeader(loginResp.getHeaders(), "refresh_token");
        UUID personId   = personId(KalimaTestDataSeeder.ADMIN_EMAIL);

        // ── fire two concurrent /refresh requests with the SAME token ──────
        int N = 2;
        CountDownLatch allReady = new CountDownLatch(N);
        CountDownLatch startGun = new CountDownLatch(1);
        List<Integer>  statuses = Collections.synchronizedList(new ArrayList<>());
        final String   token    = rawToken; // effectively-final for lambda

        Runnable task = () -> {
            allReady.countDown();
            try { startGun.await(); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            int status = restPost("/api/auth/refresh", null, token)
                    .getStatusCode()
                    .value();
            statuses.add(status);
        };

        ExecutorService pool = Executors.newFixedThreadPool(N);
        for (int i = 0; i < N; i++) pool.submit(task);
        allReady.await();     // wait until both threads are ready
        startGun.countDown(); // fire!
        pool.shutdown();
        assertThat(pool.awaitTermination(15, TimeUnit.SECONDS))
                .as("Concurrent rotation tasks must complete within 15 s")
                .isTrue();

        // ── assertions ────────────────────────────────────────────────────
        assertThat(statuses).hasSize(N);

        long successes = statuses.stream().filter(s -> s == 200).count();
        assertThat(successes)
                .as("SELECT FOR UPDATE must prevent two concurrent rotations: " +
                    "two 200s means the TOCTOU window is still open. Statuses: " + statuses)
                .isLessThanOrEqualTo(1);

        // The second request detected reuse and revoked the family.
        // Zero active tokens must remain (T1_new was revoked by T2's revokeFamily).
        assertThat(activeFamilyTokenCount(personId, testStart))
                .as("After concurrent reuse detection, entire family is revoked — " +
                    "zero active tokens must remain. Statuses: " + statuses)
                .isZero();
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private MvcResult doLoginMvc(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, KalimaTestDataSeeder.PLAIN_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String loginJson(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    /** Extracts the raw token value from the {@code Set-Cookie: refresh_token=...} header. */
    private String extractRefreshToken(MvcResult result) {
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith("refresh_token="))
                .map(h -> h.split(";")[0].substring("refresh_token=".length()))
                .filter(v -> !v.isBlank())
                .findFirst()
                .orElseThrow(() -> new AssertionError("No refresh_token cookie in response"));
    }

    private String setCookieHeader(MvcResult result) {
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.contains("refresh_token="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Set-Cookie header found"));
    }

    /** Extracts a named cookie value from the response {@code Set-Cookie} headers. */
    private String extractTokenFromSetCookieHeader(HttpHeaders headers, String cookieName) {
        return Optional.ofNullable(headers.get("Set-Cookie"))
                .orElseThrow(() -> new AssertionError("No Set-Cookie header"))
                .stream()
                .filter(h -> h.startsWith(cookieName + "="))
                .map(h -> h.split(";")[0].substring(cookieName.length() + 1))
                .filter(v -> !v.isBlank())
                .findFirst()
                .orElseThrow(() -> new AssertionError("Cookie '" + cookieName + "' not found"));
    }

    /**
     * Makes a real HTTP POST via {@link TestRestTemplate}.
     * When {@code refreshCookie} is non-null it is sent as the Cookie header
     * so the server's {@code extractRefreshCookie} picks it up.
     */
    private ResponseEntity<JsonNode> restPost(String path, String body, String refreshCookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (refreshCookie != null) {
            headers.set(HttpHeaders.COOKIE, "refresh_token=" + refreshCookie);
        }
        return restTemplate.exchange(
                "http://localhost:" + port + path,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                JsonNode.class);
    }

    private UUID personId(String email) {
        return personRepository.findByEmail(email)
                .map(Person::getId)
                .orElseThrow(() -> new AssertionError("Person not found: " + email));
    }

    /**
     * Counts active refresh tokens for the given person that were created at or
     * after {@code since}.  The {@code since} bound scopes the count to the
     * current test run, preventing interference from tokens issued by other tests.
     */
    private long activeFamilyTokenCount(UUID personId, Instant since) {
        Instant now = Instant.now();
        return refreshTokenRepository.findAll().stream()
                .filter(t -> t.getPersonId().equals(personId))
                .filter(t -> !t.getCreatedAt().isBefore(since))
                .filter(t -> t.isActive(now))
                .count();
    }
}
