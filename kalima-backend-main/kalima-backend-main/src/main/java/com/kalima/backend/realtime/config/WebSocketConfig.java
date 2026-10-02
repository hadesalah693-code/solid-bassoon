package com.kalima.backend.realtime.config;

import com.kalima.backend.auth.jwt.JwtService;
import com.kalima.backend.auth.support.EmailNormalization;
import com.kalima.backend.auth.support.PersonAuthSupport;
import com.kalima.backend.casefile.service.CaseAccessService;
import com.kalima.backend.config.KalimaCorsProperties;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STOMP-over-WebSocket setup for real-time case chat.
 *
 * <ul>
 *   <li>Endpoint: {@code /ws} (raw WebSocket; the frontend uses
 *       {@code @stomp/stompjs} with native transport).</li>
 *   <li>Broker prefix: {@code /topic} (in-memory simple broker).</li>
 *   <li>App prefix: {@code /app} (reserved; we don't currently route
 *       client→server STOMP messages — sends still go through REST).</li>
 *   <li>Auth: JWT in the {@code Authorization: Bearer …} STOMP header on the
 *       initial CONNECT frame. Identical validation rules to
 *       {@link com.kalima.backend.auth.jwt.JwtAuthenticationFilter}.</li>
 *   <li>Authorization: SUBSCRIBE to {@code /topic/cases/{caseId}/messages}
 *       only succeeds if the connected user can read that case (mirrors
 *       {@code MessageService.ensureCanRead}). Otherwise the broker drops
 *       the frame.</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    static final String CASE_TOPIC_PREFIX = "/topic/cases/";
    private static final Pattern CASE_TOPIC_PATTERN =
            Pattern.compile("^/topic/cases/([0-9a-fA-F-]{36})/messages$");

    private final JwtService jwtService;
    private final PersonRepository personRepository;
    private final CaseAccessService caseAccessService;
    private final KalimaCorsProperties corsProperties;

    public WebSocketConfig(JwtService jwtService,
                           PersonRepository personRepository,
                           CaseAccessService caseAccessService,
                           KalimaCorsProperties corsProperties) {
        this.jwtService = jwtService;
        this.personRepository = personRepository;
        this.caseAccessService = caseAccessService;
        this.corsProperties = corsProperties;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(corsProperties.allowedOriginList()
                        .toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor());
    }

    private ChannelInterceptor authChannelInterceptor() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                        message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    authenticateConnect(accessor);
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    authorizeSubscribe(accessor);
                }
                return message;
            }
        };
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        String authHeader = firstNativeHeader(accessor, "Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw rejected("WebSocket CONNECT requires Bearer token");
        }
        String token = authHeader.substring("Bearer ".length()).trim();
        Claims claims;
        try {
            claims = jwtService.parse(token);
        } catch (JwtException | IllegalArgumentException ex) {
            throw rejected("Invalid token");
        }

        String email = EmailNormalization.normalize(claims.getSubject());
        String companyIdFromJwt = claims.get("companyId", String.class);
        if (email == null || companyIdFromJwt == null) {
            throw rejected("Invalid token claims");
        }

        Optional<Person> personOpt = personRepository.findByEmailWithCompany(email);
        if (personOpt.isEmpty()
                || !PersonAuthSupport.isAllowedAuthenticatedSession(personOpt.get())) {
            throw rejected("User not allowed");
        }
        Person person = personOpt.get();
        UUID companyDb = person.getCompany().getId();
        if (!companyDb.equals(UUID.fromString(companyIdFromJwt))) {
            throw rejected("Tenant mismatch");
        }

        Authentication auth = new UsernamePasswordAuthenticationToken(
                email, null, PersonAuthSupport.roleAuthorities(person));
        accessor.setUser(auth);

        // Stash for SUBSCRIBE access checks without a second DB hit.
        accessor.getSessionAttributes().put("personId", person.getId());
        accessor.getSessionAttributes().put("personRole", person.getSystemRole());
        accessor.getSessionAttributes().put("companyId", companyDb);
    }

    @Transactional(readOnly = true)
    void authorizeSubscribe(StompHeaderAccessor accessor) {
        String dest = accessor.getDestination();
        if (dest == null || !dest.startsWith(CASE_TOPIC_PREFIX)) {
            return; // Only case topics are gated; let other (future) topics through.
        }
        Matcher m = CASE_TOPIC_PATTERN.matcher(dest);
        if (!m.matches()) {
            throw rejected("Invalid case topic: " + dest);
        }
        UUID caseId = UUID.fromString(m.group(1));

        var attrs = accessor.getSessionAttributes();
        if (attrs == null || attrs.get("personId") == null) {
            throw rejected("Not authenticated");
        }
        UUID personId = (UUID) attrs.get("personId");
        DomainEnums.SystemRole role =
                (DomainEnums.SystemRole) attrs.get("personRole");
        UUID companyId = (UUID) attrs.get("companyId");

        TenantContext.set(companyId);
        try {
            caseAccessService.requireReadableCase(caseId, personId, role);
        } catch (ResponseStatusException ex) {
            throw rejected(ex.getReason() != null ? ex.getReason() : "Forbidden");
        } finally {
            TenantContext.clear();
        }
    }

    private static String firstNativeHeader(StompHeaderAccessor accessor, String name) {
        var values = accessor.getNativeHeader(name);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }

    private static MessagingException rejected(String reason) {
        return new MessagingException(reason);
    }
}
