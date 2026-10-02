package com.kalima.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * The explicit {@code order} on {@link EnableTransactionManagement} is what
 * makes {@code TenantFilterAspect} actually work: it forces Spring's
 * transaction advice to have higher precedence (outer) than our aspect
 * (which keeps the default {@link Ordered#LOWEST_PRECEDENCE}), so the filter
 * is enabled <em>inside</em> an open transaction on the right Hibernate Session.
 */
@SpringBootApplication
@ConfigurationPropertiesScan({
        "com.kalima.backend.auth.config",
        "com.kalima.backend.config",
        "com.kalima.backend.agent.config"
})
@EnableTransactionManagement(order = Ordered.HIGHEST_PRECEDENCE + 100)
@EnableScheduling
public class KalimaBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(KalimaBackendApplication.class, args);
	}

}
