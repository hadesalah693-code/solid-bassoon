package com.kalima.backend.config;

import com.kalima.backend.auth.jwt.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.List;

/**
 * Stateless API security: {@link JwtAuthenticationFilter} before
 * {@link org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter},
 * no session, CSRF off (token auth). CORS is an explicit origin allowlist from
 * {@link KalimaCorsProperties} (in production, set {@code CORS_ALLOWED_ORIGINS}; an empty
 * list disables browser cross-origin use of credentials).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final HandlerExceptionResolver resolver;
    private final KalimaCorsProperties corsProperties;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter,
                          @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver,
                          KalimaCorsProperties corsProperties) {
        this.jwtFilter = jwtFilter;
        this.resolver = resolver;
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(c -> c.configurationSource(corsSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/info", "/actuator/metrics").authenticated()
                        // STOMP/WebSocket handshake: auth is enforced inside
                        // the WebSocketConfig channel interceptor on CONNECT.
                        .requestMatchers("/ws/**", "/ws").permitAll()
                        // Accounting runs in a single currency with no FX and
                        // the ledger is read-only for senior lawyers. These
                        // request matchers run in the filter chain, i.e. before
                        // argument resolution, so a denied caller gets a 403
                        // instead of a 400 from @Valid on the request body.
                        // The @PreAuthorize annotations on the controllers stay
                        // in place as the method-level contract.
                        .requestMatchers("/api/accounting/periods/**", "/api/accounting/tax-rates/**")
                        .hasAnyRole("ADMIN", "ACCOUNTANT")
                        .requestMatchers(HttpMethod.POST, "/api/accounting/**")
                        .hasAnyRole("ADMIN", "ACCOUNTANT")
                        .requestMatchers(HttpMethod.PUT, "/api/accounting/**")
                        .hasAnyRole("ADMIN", "ACCOUNTANT")
                        .requestMatchers(HttpMethod.PATCH, "/api/accounting/**")
                        .hasAnyRole("ADMIN", "ACCOUNTANT")
                        .requestMatchers(HttpMethod.DELETE, "/api/accounting/**")
                        .hasAnyRole("ADMIN", "ACCOUNTANT")
                        .requestMatchers("/api/accounting/**")
                        .hasAnyRole("ADMIN", "ACCOUNTANT", "SENIOR_LAWYER")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Delegates to the MVC exception resolver chain so that 401 / 403 responses
     * emitted by Spring Security share the same {@code ApiError} shape used
     * everywhere else (see {@code GlobalExceptionHandler}).
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) ->
                resolver.resolveException(request, response, null, authException);
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) ->
                resolver.resolveException(request, response, null, accessDeniedException);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsSource() {
        List<String> origins = corsProperties.allowedOriginList();
        List<String> originPatterns = corsProperties.allowedOriginPatterns();
        CorsConfiguration cfg = new CorsConfiguration();
        if (!origins.isEmpty()) {
            cfg.setAllowedOrigins(origins);
        }
        if (!originPatterns.isEmpty()) {
            cfg.setAllowedOriginPatterns(originPatterns);
        }
        // No allow-list configured: the app is served behind a single-origin
        // reverse proxy (localhost:8081 / LAN IP / Cloudflare tunnel), so any
        // Origin reaching the backend is fine. Wildcard patterns stay valid
        // together with credentials (unlike allowed-origins "*").
        if (origins.isEmpty() && originPatterns.isEmpty()) {
            cfg.setAllowedOriginPatterns(List.of("*"));
        }
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("Set-Cookie"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
