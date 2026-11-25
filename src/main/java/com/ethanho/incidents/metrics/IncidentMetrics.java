package com.ethanho.incidents.metrics;

import com.ethanho.incidents.domain.IncidentType;
import com.ethanho.incidents.domain.IngestChannel;
import com.ethanho.incidents.domain.Severity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * All custom metrics in one place. Micrometer is the metrics facade (like SLF4J for logging);
 * Actuator exposes everything at {@code /actuator/prometheus}, Prometheus scrapes it, Grafana graphs it.
 *
 * <p>Prometheus names: {@code incidents.received} becomes {@code incidents_received_total}, and the
 * timers become {@code incidents_processing_duration_seconds_*}.
 */
@Component
public class IncidentMetrics {

    private final MeterRegistry registry;
    private final Counter deadLettered;
    private final Counter publishFailures;
    private final Counter republished;
    private final Counter duplicateDeliveries;
    private final Timer processingDuration;
    private final Timer endToEndLatency;

    public IncidentMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.deadLettered = Counter.builder("incidents.dead.lettered")
                .description("Events moved to the dead-letter queue after all retries failed")
                .register(registry);
        this.publishFailures = Counter.builder("incidents.publish.failures")
                .description("Events saved but not confirmed by RabbitMQ (the republisher will retry them)")
                .register(registry);
        this.republished = Counter.builder("incidents.republished")
                .description("Events re-sent to RabbitMQ by the republisher job")
                .register(registry);
        this.duplicateDeliveries = Counter.builder("incidents.duplicate.deliveries")
                .description("Messages delivered for events that were already processed (skipped)")
                .register(registry);
        this.processingDuration = Timer.builder("incidents.processing.duration")
                .description("Time the consumer spends processing one event")
                .publishPercentileHistogram()
                .register(registry);
        this.endToEndLatency = Timer.builder("incidents.end_to_end.latency")
                .description("Time from the event being received by the API to being processed")
                .publishPercentileHistogram()
                .register(registry);
    }

    public void received(IngestChannel channel) {
        registry.counter("incidents.received", "channel", channel.name()).increment();
    }

    public void duplicateRejected(IngestChannel channel) {
        registry.counter("incidents.duplicates", "channel", channel.name()).increment();
    }

    public void processed(IncidentType type, Severity severity, Duration endToEnd) {
        registry.counter("incidents.processed", "type", type.name(), "severity", severity.name()).increment();
        endToEndLatency.record(endToEnd);
    }

    public void duplicateDelivery() {
        duplicateDeliveries.increment();
    }

    public void deadLettered() {
        deadLettered.increment();
    }

    public void publishFailed() {
        publishFailures.increment();
    }

    public void republished(int count) {
        republished.increment(count);
    }

    public Timer processingTimer() {
        return processingDuration;
    }
}
