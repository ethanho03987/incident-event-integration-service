package com.ethanho.incidents.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Type-safe view of the {@code app.*} section of application.yml.
 * A Java record is an immutable data class (like a C# record): fields, constructor,
 * getters, equals/hashCode and toString are generated for you.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Webhook webhook, Republisher republisher, Simulation simulation) {

    /** Shared secret used to verify HMAC-SHA256 webhook signatures. */
    public record Webhook(String secret) {}

    /** Settings for the job that re-publishes events that never reached RabbitMQ. */
    public record Republisher(Duration stuckAfter, int batchSize) {}

    /** Lets you make the simulated downstream dispatch system fail, to demo retries and the DLQ. */
    public record Simulation(double dispatchFailureRate) {}
}
