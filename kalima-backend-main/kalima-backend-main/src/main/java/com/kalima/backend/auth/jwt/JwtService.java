package com.kalima.backend.auth.jwt;

import com.kalima.backend.auth.config.JwtProperties;
import com.kalima.backend.domain.entity.Person;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final JwtProperties props;
    private final SecretKey key;

    public JwtService(JwtProperties props) {
        this.props = props;
        if (props.getSecret() == null || props.getSecret().isBlank()
                || props.getSecret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "kalima.jwt.secret must be set to at least 32 bytes/characters (use env JWT_SECRET).");
        }
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Person person) {
        if (person.getCompany() == null || person.getCompany().getId() == null) {
            throw new IllegalStateException(
                    "Cannot issue token for person without company: " + person.getId());
        }
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.getExpirationMinutes() * 60);
        return Jwts.builder()
                .subject(person.getEmail())
                .claim("personId", person.getId().toString())
                .claim("companyId", person.getCompany().getId().toString())
                .claim("role", person.getSystemRole().name())
                .claim("fullName", person.getFullName())
                .issuer(props.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Instant getExpiration(String token) {
        return parse(token).getExpiration().toInstant();
    }
}
