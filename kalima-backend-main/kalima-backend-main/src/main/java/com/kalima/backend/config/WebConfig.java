package com.kalima.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

/**
 * Web MVC configuration shared across all controllers.
 */
@Configuration
public class WebConfig {

    /**
     * Caps the maximum page size accepted on any paginated endpoint to 200 rows.
     * Callers requesting {@code ?size=N} where N > 200 will receive at most 200
     * results. The default page size set by individual controllers via
     * {@code @PageableDefault(size = X)} is unaffected.
     */
    @Bean
    PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer() {
        return resolver -> resolver.setMaxPageSize(200);
    }
}
