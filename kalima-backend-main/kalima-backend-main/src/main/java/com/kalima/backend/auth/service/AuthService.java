package com.kalima.backend.auth.service;

import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.dto.LoginRequest;
import com.kalima.backend.auth.dto.LoginResponse;
import com.kalima.backend.auth.jwt.JwtService;
import com.kalima.backend.auth.ratelimit.LoginRateLimiter;
import com.kalima.backend.auth.support.EmailNormalization;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.PersonRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

@Service
public class AuthService {

    private final AuthenticationManager authManager;
    private final JwtService jwtService;
    private final PersonRepository personRepository;
    private final AuditLogService auditLogService;
    private final LoginRateLimiter loginRateLimiter;
    private final RefreshTokenService refreshTokenService;

    public AuthService(AuthenticationManager authManager,
                       JwtService jwtService,
                       PersonRepository personRepository,
                       AuditLogService auditLogService,
                       LoginRateLimiter loginRateLimiter,
                       RefreshTokenService refreshTokenService) {
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.personRepository = personRepository;
        this.auditLogService = auditLogService;
        this.loginRateLimiter = loginRateLimiter;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public ResponseEntity<LoginResponse> login(LoginRequest request,
                                                 HttpServletRequest httpRequest,
                                                 HttpServletResponse httpResponse) {
        String email = EmailNormalization.normalize(request.email());
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
        }
        if (!loginRateLimiter.allowLoginAttempt(httpRequest, email)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many login attempts. Please try again later.");
        }
        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (AuthenticationException ex) {
            Person probed = personRepository.findByEmail(email).orElse(null);
            auditLogService.recordFailure(
                    AuditAction.LOGIN_FAILED,
                    probed,
                    email,
                    "Person",
                    probed != null ? probed.getId() : null,
                    Map.of("reason", classifyReason(probed)),
                    "Invalid email or password");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        Person person = personRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Invalid email or password"));

        String accessToken = jwtService.generateToken(person);
        person.setLastLoginAt(LocalDateTime.now());

        RefreshTokenService.IssuedToken rt =
                refreshTokenService.issue(person.getId(), person.getCompany().getId());
        httpResponse.addHeader(HttpHeaders.SET_COOKIE,
                refreshTokenService.buildCookie(rt.rawToken(), rt.expiresAt()).toString());

        auditLogService.recordSuccess(
                AuditAction.LOGIN_SUCCESS,
                person,
                "Person",
                person.getId(),
                Map.of("role", person.getSystemRole().name()));

        return ResponseEntity.ok(new LoginResponse(
                accessToken,
                "Bearer",
                jwtService.getExpiration(accessToken),
                new LoginResponse.PersonInfo(
                        person.getId(),
                        person.getFullName(),
                        person.getEmail(),
                        person.getSystemRole())));
    }

    public ResponseEntity<LoginResponse> refresh(HttpServletRequest httpRequest,
                                                 HttpServletResponse httpResponse) {
        String rawToken = extractRefreshCookie(httpRequest)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Refresh token cookie missing"));

        RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(rawToken);

        Person person = personRepository
                .findByIdWithCompany(rotation.personId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Person not found"));

        String accessToken = jwtService.generateToken(person);

        httpResponse.addHeader(HttpHeaders.SET_COOKIE,
                refreshTokenService.buildCookie(
                        rotation.issuedToken().rawToken(),
                        rotation.issuedToken().expiresAt()).toString());

        return ResponseEntity.ok(new LoginResponse(
                accessToken,
                "Bearer",
                jwtService.getExpiration(accessToken),
                new LoginResponse.PersonInfo(
                        person.getId(),
                        person.getFullName(),
                        person.getEmail(),
                        person.getSystemRole())));
    }

    @Transactional
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest,
                                       HttpServletResponse httpResponse) {
        extractRefreshCookie(httpRequest)
                .ifPresent(refreshTokenService::revokeByRawToken);

        httpResponse.addHeader(HttpHeaders.SET_COOKIE,
                refreshTokenService.clearCookie().toString());

        return ResponseEntity.noContent().build();
    }

    private Optional<String> extractRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        String name = refreshTokenService.cookieName();
        return Arrays.stream(cookies)
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> !v.isBlank())
                .findFirst();
    }

    private String classifyReason(Person probed) {
        if (probed == null) {
            return "unknown_email";
        }
        if (!probed.isActive()) {
            return "account_disabled";
        }
        return "invalid_password";
    }
}
