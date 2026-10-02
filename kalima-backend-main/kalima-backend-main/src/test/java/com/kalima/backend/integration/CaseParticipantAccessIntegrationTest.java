package com.kalima.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.domain.repository.PersonRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.StringMessageConverter;
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

import static com.kalima.backend.integration.MessagingSecurityTestSupport.addParticipant;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.bytes;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.createCase;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.listMessagesExpectStatus;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.mutableLawyerToken;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.requirePersonId;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.sendMessage;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.storedFilenameFromUrl;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.tenant1AdminToken;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.tenant2AdminToken;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.unassignedLawyerToken;
import static com.kalima.backend.integration.MessagingSecurityTestSupport.uploadAttachment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Case participant ACL — inbox visibility vs messaging access")
class CaseParticipantAccessIntegrationTest extends AbstractPostgresSpringBootTest {

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
    @DisplayName("Secondary-lawyer participant can list messages → 200")
    void participant_canListMessages() throws Exception {
        ParticipantCase ctx = seedParticipantCase();
        listMessagesExpectStatus(mockMvc, ctx.participantToken(), ctx.caseId(), 200);
    }

    @Test
    @DisplayName("Secondary-lawyer participant can upload and download attachments → 200")
    void participant_canAccessAttachments() throws Exception {
        ParticipantCase ctx = seedParticipantCase();
        String url = uploadAttachment(mockMvc, objectMapper, ctx.participantToken(), ctx.caseId(),
                "participant.txt", "text/plain", bytes("participant file"));
        String filename = storedFilenameFromUrl(url);

        mockMvc.perform(get("/api/messages/attachments/" + filename)
                        .header("Authorization", "Bearer " + ctx.participantToken()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Secondary-lawyer participant can ask chatbot → 200")
    void participant_canAskChatbot() throws Exception {
        ParticipantCase ctx = seedParticipantCase();
        mockMvc.perform(post("/api/cases/" + ctx.caseId() + "/chatbot/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + ctx.participantToken())
                        .content("{\"question\":\"حالة القضية\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Secondary-lawyer participant can SUBSCRIBE to case WebSocket topic")
    void participant_canSubscribeWebSocket() throws Exception {
        ParticipantCase ctx = seedParticipantCase();
        AtomicReference<Throwable> sessionError = new AtomicReference<>();
        StompSession session = connect(ctx.participantToken(), sessionError);
        try {
            session.subscribe(
                    CASE_TOPIC_PREFIX + ctx.caseId() + "/messages",
                    noopHandler());
            Thread.sleep(300);
            assertThat(sessionError.get()).isNull();
        } finally {
            safeDisconnect(session);
        }
    }

    @Test
    @DisplayName("Non-participant lawyer still denied messages → 404")
    void nonParticipant_stillDenied() throws Exception {
        ParticipantCase ctx = seedParticipantCase();
        String otherLawyerToken = mutableLawyerToken(mockMvc, objectMapper);
        listMessagesExpectStatus(mockMvc, otherLawyerToken, ctx.caseId(), 404);
    }

    @Test
    @DisplayName("Cross-tenant participant access still denied → 404")
    void crossTenantParticipant_stillDenied() throws Exception {
        ParticipantCase ctx = seedParticipantCase();
        String t2Token = tenant2AdminToken(mockMvc, objectMapper);
        listMessagesExpectStatus(mockMvc, t2Token, ctx.caseId(), 404);
    }

    private ParticipantCase seedParticipantCase() throws Exception {
        String adminToken = tenant1AdminToken(mockMvc, objectMapper);
        UUID clientId = requirePersonId(personRepository, KalimaTestDataSeeder.CLIENT_EMAIL);
        UUID assignedLawyerId = requirePersonId(personRepository, KalimaTestDataSeeder.LAWYER_EMAIL);
        UUID participantId = requirePersonId(personRepository, KalimaTestDataSeeder.WITNESS_EMAIL);
        String caseId = createCase(mockMvc, objectMapper, adminToken, clientId, assignedLawyerId,
                "Participant ACL");

        addParticipant(mockMvc, adminToken, UUID.fromString(caseId), participantId, "SECONDARY_LAWYER");
        sendMessage(mockMvc, objectMapper, adminToken, caseId, "seed message");

        String participantToken = unassignedLawyerToken(mockMvc, objectMapper);
        return new ParticipantCase(caseId, participantToken);
    }

    private StompSession connect(String bearerToken, AtomicReference<Throwable> sessionError)
            throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + bearerToken);

        CompletableFuture<StompSession> future = new CompletableFuture<>();
        stompClient.connectAsync(
                "ws://localhost:" + port + "/ws",
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
                    public void handleException(StompSession session,
                                                org.springframework.messaging.simp.stomp.StompCommand command,
                                                StompHeaders headers, byte[] payload,
                                                Throwable exception) {
                        sessionError.set(exception);
                    }
                });
        return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
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

    private static void safeDisconnect(StompSession session) {
        try {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        } catch (Exception ignored) {
        }
    }

    private record ParticipantCase(String caseId, String participantToken) {
    }
}
