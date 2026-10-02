package com.kalima.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.domain.repository.PersonRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.kalima.backend.integration.MessagingSecurityTestSupport.createCase;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.requirePersonId;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.tenant1AdminToken;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.tenant2AdminToken;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.unassignedLawyerToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("WebSocket STOMP authorization — Week 1 + Week 2 CORS")
class WebSocketSecurityIntegrationTest extends AbstractPostgresSpringBootTest {

    private static final String CASE_TOPIC_PREFIX = "/topic/cases/";
    private static final long TIMEOUT_SECONDS = 10;

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PersonRepository personRepository;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUpClient() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new StringMessageConverter());
    }

    @AfterEach
    void tearDownClient() {
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    @Test
    @DisplayName("Authorized user can SUBSCRIBE to own-tenant case topic")
    void subscribe_authorizedCase_succeeds() throws Exception {
        String adminToken = tenant1AdminToken(mockMvc, objectMapper);
        UUID clientId = requirePersonId(personRepository, KalimaTestDataSeeder.CLIENT_EMAIL);
        String caseId = createCase(mockMvc, objectMapper, adminToken, clientId, null, "WS ok");

        AtomicReference<Throwable> sessionError = new AtomicReference<>();
        StompSession session = connect(adminToken, sessionError);
        try {
            session.subscribe(
                    CASE_TOPIC_PREFIX + caseId + "/messages",
                    noopHandler());
            Thread.sleep(300);
            assertThat(sessionError.get()).isNull();
        } finally {
            safeDisconnect(session);
        }
    }

    @Test
    @DisplayName("Cross-tenant SUBSCRIBE is rejected")
    void subscribe_crossTenantCase_rejected() throws Exception {
        String t2Token = tenant2AdminToken(mockMvc, objectMapper);
        UUID t2ClientId = requirePersonId(personRepository, KalimaTestDataSeeder.FOREIGN_CLIENT_EMAIL);
        String t2CaseId = createCase(mockMvc, objectMapper, t2Token, t2ClientId, null, "WS T2");

        String t1Token = tenant1AdminToken(mockMvc, objectMapper);
        AtomicReference<Throwable> sessionError = new AtomicReference<>();
        StompSession session = connect(t1Token, sessionError);
        try {
            session.subscribe(
                    CASE_TOPIC_PREFIX + t2CaseId + "/messages",
                    noopHandler());
            assertSubscribeRejected(session, sessionError);
        } finally {
            safeDisconnect(session);
        }
    }

    @Test
    @DisplayName("SUBSCRIBE without case access is rejected")
    void subscribe_unassignedLawyer_rejected() throws Exception {
        String adminToken = tenant1AdminToken(mockMvc, objectMapper);
        UUID clientId = requirePersonId(personRepository, KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID lawyerId = requirePersonId(personRepository, KalimaTestDataSeeder.LAWYER_EMAIL);
        String caseId = createCase(mockMvc, objectMapper, adminToken, clientId, lawyerId, "WS lawyer block");

        String witnessToken = unassignedLawyerToken(mockMvc, objectMapper);
        AtomicReference<Throwable> sessionError = new AtomicReference<>();
        StompSession session = connect(witnessToken, sessionError);
        try {
            session.subscribe(
                    CASE_TOPIC_PREFIX + caseId + "/messages",
                    noopHandler());
            assertSubscribeRejected(session, sessionError);
        } finally {
            safeDisconnect(session);
        }
    }

    @Test
    @DisplayName("SUBSCRIBE to invalid case topic format is rejected")
    void subscribe_invalidTopic_rejected() throws Exception {
        String adminToken = tenant1AdminToken(mockMvc, objectMapper);
        AtomicReference<Throwable> sessionError = new AtomicReference<>();
        StompSession session = connect(adminToken, sessionError);
        try {
            session.subscribe(
                    CASE_TOPIC_PREFIX + "not-a-uuid/messages",
                    noopHandler());
            assertSubscribeRejected(session, sessionError);
        } finally {
            safeDisconnect(session);
        }
    }

    @Test
    @DisplayName("CONNECT without Bearer token is rejected")
    void connect_withoutToken_rejected() {
        CompletableFuture<StompSession> future = new CompletableFuture<>();
        stompClient.connectAsync(
                wsUrl(),
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                        future.complete(session);
                    }

                    @Override
                    public void handleTransportError(StompSession session, Throwable exception) {
                        future.completeExceptionally(exception);
                    }
                });

        assertThrows(Exception.class, () -> future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    private StompSession connect(String bearerToken, AtomicReference<Throwable> sessionError)
            throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + bearerToken);

        CompletableFuture<StompSession> future = new CompletableFuture<>();
        stompClient.connectAsync(
                wsUrl(),
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                        future.complete(session);
                    }

                    @Override
                    public void handleTransportError(StompSession session, Throwable exception) {
                        future.completeExceptionally(exception);
                    }

                    @Override
                    public void handleException(StompSession session, StompCommand command,
                                                StompHeaders headers, byte[] payload,
                                                Throwable exception) {
                        sessionError.set(exception);
                    }
                });
        return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private String wsUrl() {
        return "ws://localhost:" + port + "/ws";
    }

    private static StompFrameHandler noopHandler() {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
            }
        };
    }

    private static void assertSubscribeRejected(StompSession session,
                                                AtomicReference<Throwable> error)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3_000;
        while (error.get() == null && session.isConnected() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertThat(error.get() != null || !session.isConnected()).isTrue();
    }

    private static void safeDisconnect(StompSession session) {
        try {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        } catch (Exception ignored) {
            // Server may already have closed the connection after a rejected SUBSCRIBE.
        }
    }
}
