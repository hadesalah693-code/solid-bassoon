package com.kalima.backend.integration;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Wires a shared PostgreSQL instance for all test classes. Uses
 * Testcontainers' JDBC URL so one container (with {@code TC_DAEMON=true}) outlives
 * individual test classes; otherwise static {@code @Container} fields are
 * stopped after each class and a later class may still point at a dead port.
 * Requires a working Docker (or compatible) environment for {@code mvn test}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
public abstract class AbstractPostgresSpringBootTest {

    @DynamicPropertySource
    static void registerDatabase(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",
                () -> "jdbc:tc:postgresql:16-alpine:///kalima_test?TC_DAEMON=true");
        r.add("spring.datasource.username", () -> "test");
        r.add("spring.datasource.password", () -> "test");
        r.add("spring.datasource.driver-class-name",
                () -> "org.testcontainers.jdbc.ContainerDatabaseDriver");
    }
}
