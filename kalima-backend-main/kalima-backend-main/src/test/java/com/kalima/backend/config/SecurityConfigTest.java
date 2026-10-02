package com.kalima.backend.config;

import com.kalima.backend.auth.jwt.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {

    @Test
    void allowsVercelOriginPatternsAndCredentials() {
        KalimaCorsProperties props = new KalimaCorsProperties();
        props.setAllowedOrigins("https://example.com,https://*.vercel.app");

        SecurityConfig config = new SecurityConfig(
                mock(JwtAuthenticationFilter.class),
                mock(HandlerExceptionResolver.class),
                props
        );

        CorsConfigurationSource source = config.corsSource();
        CorsConfiguration cors = source.getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/auth/login"));

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOriginPatterns()).contains("https://*.vercel.app");
        assertThat(cors.getAllowCredentials()).isTrue();
    }
}
