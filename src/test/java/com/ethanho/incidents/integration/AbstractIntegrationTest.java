package com.ethanho.incidents.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Base class for integration tests. Starts the whole application against <b>real</b> MySQL and RabbitMQ.
 *
 * <p>By default Testcontainers starts both in Docker (that is what GitHub Actions does). The containers
 * are started once in a static block and shared by every test class (the "singleton container" pattern),
 * which keeps the suite fast.
 *
 * <p>Run with {@code -Dit.external=true} to use MySQL/RabbitMQ already running on localhost instead,
 * e.g. ones started with {@code docker compose up mysql rabbitmq}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AbstractIntegrationTest.TestGatewayConfig.class)
public abstract class AbstractIntegrationTest {

    /** Swaps the simulated dispatch system for one the tests can control. {@code @Primary} wins over the real bean. */
    @TestConfiguration
    static class TestGatewayConfig {
        @Bean
        @Primary
        ControllableDispatchGateway controllableDispatchGateway() {
            return new ControllableDispatchGateway();
        }
    }

    static final boolean EXTERNAL = Boolean.getBoolean("it.external");

    static MySQLContainer<?> mysql;
    static RabbitMQContainer rabbit;

    static {
        if (!EXTERNAL) {
            mysql = new MySQLContainer<>("mysql:8.4").withDatabaseName("incidents");
            rabbit = new RabbitMQContainer("rabbitmq:3.13-management");
            mysql.start();
            rabbit.start();
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        if (EXTERNAL) {
            registry.add("spring.datasource.url",
                    () -> "jdbc:mysql://localhost:3306/incidents_it?createDatabaseIfNotExist=true");
        } else {
            registry.add("spring.datasource.url", mysql::getJdbcUrl);
            registry.add("spring.datasource.username", mysql::getUsername);
            registry.add("spring.datasource.password", mysql::getPassword);
            registry.add("spring.rabbitmq.host", rabbit::getHost);
            registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
            registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
            registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);
        }
        // Make the republisher fast so the "stuck event" test doesn't have to wait 30 seconds
        registry.add("app.republisher.interval", () -> "PT1S");
        registry.add("app.republisher.stuck-after", () -> "PT2S");
        registry.add("app.webhook.secret", () -> "it-secret");
    }
}
