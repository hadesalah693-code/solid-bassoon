package com.kalima.backend.auth.jwt;

import com.kalima.backend.auth.support.EmailNormalization;
import com.kalima.backend.auth.support.PersonAuthSupport;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Extracts the JWT from the {@code Authorization: Bearer ...} header, validates
 * it cryptographically, then:
 * <ol>
 *   <li>Resolves the subject email to a {@link Person} and current tenant.</li>
 *   <li>Builds {@code ROLE_*} from the <strong>database</strong>, not the JWT
 *   {@code role} claim, so de-elevation in DB is enforced for every request.
 *   </li>
 *   <li>Requires {@code companyId} in the token to match the person&apos;s
 *   current company (moved users must obtain a new token).</li>
 *   <li>Publishes the tenant id into {@link TenantContext} (from DB, after checks).</li>
 * </ol>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final PersonRepository personRepository;

    public JwtAuthenticationFilter(JwtService jwtService, PersonRepository personRepository) {
        this.jwtService = jwtService;
        this.personRepository = personRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        boolean tenantSet = false;

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String token = header.substring(BEARER_PREFIX.length()).trim();
            try {
                Claims claims = jwtService.parse(token);
                String email = EmailNormalization.normalize(claims.getSubject());
                String companyIdFromJwt = claims.get("companyId", String.class);

                if (email == null) {
                    SecurityContextHolder.clearContext();
                } else {
                    Optional<Person> personOpt = personRepository.findByEmailWithCompany(email);
                    if (personOpt.isEmpty() || !PersonAuthSupport.isAllowedAuthenticatedSession(
                            personOpt.get())) {
                        SecurityContextHolder.clearContext();
                    } else {
                        Person person = personOpt.get();
                        UUID companyIdDb = person.getCompany().getId();
                        if (companyIdFromJwt == null) {
                            SecurityContextHolder.clearContext();
                        } else {
                            UUID companyFromToken = UUID.fromString(companyIdFromJwt);
                            if (!companyFromToken.equals(companyIdDb)) {
                                SecurityContextHolder.clearContext();
                            } else {
                                TenantContext.set(companyIdDb);
                                tenantSet = true;
                                var auth = new UsernamePasswordAuthenticationToken(
                                        email,
                                        null,
                                        PersonAuthSupport.roleAuthorities(person));
                                auth.setDetails(
                                        new WebAuthenticationDetailsSource().buildDetails(request));
                                SecurityContextHolder.getContext().setAuthentication(auth);
                            }
                        }
                    }
                }
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            if (tenantSet) {
                TenantContext.clear();
            }
        }
    }
}
