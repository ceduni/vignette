package org.titiplex;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Throwaway PostgreSQL for integration tests (needs Docker).
 * <p>
 * The database starts empty, so each test context runs the real Flyway migrations and
 * then Hibernate's {@code ddl-auto=validate}: a migration that does not match the
 * entities fails the tests instead of failing in production.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestContainerConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:17-alpine");
    }
}
