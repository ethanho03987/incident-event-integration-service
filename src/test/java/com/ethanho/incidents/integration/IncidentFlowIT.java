package com.ethanho.incidents.integration;

import com.ethanho.incidents.api.IncidentResponse;
import com.ethanho.incidents.api.IngestionResponse;
import com.ethanho.incidents.domain.EventStatus;
import com.ethanho.incidents.domain.IncidentEvent;
import com.ethanho.incidents.domain.IncidentEventRepository;
import com.ethanho.incidents.domain.IncidentType;
import com.ethanho.incidents.domain.IngestChannel;
import com.ethanho.incidents.domain.Severity;
import com.ethanho.incidents.messaging.IncidentMessage;
import com.ethanho.incidents.messaging.RabbitTopology;
import com.ethanho.incidents.webhook.WebhookSignatureVerifier;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end tests through the real HTTP layer, MySQL and RabbitMQ.
 * Processing is asynchronous, so assertions about the outcome use Awaitility to poll until it happens.
 */
class IncidentFlowIT extends AbstractIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired TestRestTemplate http;
    @Autowired IncidentEventRepository repository;
    @Autowired ControllableDispatchGateway dispatchGateway;
    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired AmqpAdmin amqpAdmin;
    @Autowired WebhookSignatureVerifier signatureVerifier;
    @Autowired MeterRegistry meterRegistry;

    @AfterEach
    void resetGateway() {
        dispatchGateway.recover();
    }

    // ---------- happy path ----------

    @Test
    void restEventIsStoredQueuedAndProcessed() {
        String eventId = newId("rest");

        ResponseEntity<IngestionResponse> response = http.postForEntity("/api/v1/incidents", incident(eventId), IngestionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().status()).isEqualTo("ACCEPTED");

        IncidentEvent processed = awaitStatus(eventId, EventStatus.PROCESSED);
        assertThat(processed.getChannel()).isEqualTo(IngestChannel.REST);
        assertThat(processed.getAssignedService()).isEqualTo("Fire and Rescue");
        assertThat(processed.getPriorityScore()).isEqualTo(70);
        assertThat(processed.getProcessedAt()).isNotNull();

        IncidentResponse viaApi = http.getForObject("/api/v1/incidents/" + eventId, IncidentResponse.class);
        assertThat(viaApi.status()).isEqualTo(EventStatus.PROCESSED);
    }

    // ---------- duplicate handling ----------

    @Test
    void sameEventSentTwiceIsStoredAndProcessedOnce() {
        String eventId = newId("dup");

        http.postForEntity("/api/v1/incidents", incident(eventId), IngestionResponse.class);
        ResponseEntity<IngestionResponse> second = http.postForEntity("/api/v1/incidents", incident(eventId), IngestionResponse.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().duplicate()).isTrue();
        awaitStatus(eventId, EventStatus.PROCESSED);
        assertThat(dispatchGateway.callsFor(eventId)).isEqualTo(1);
    }

    @Test
    void concurrentDuplicatesResultInExactlyOneAcceptedEvent() throws Exception {
        String eventId = newId("race");
        Map<String, Object> body = incident(eventId);

        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Callable<HttpStatus>> calls = IntStream.range(0, 10)
                    .<Callable<HttpStatus>>mapToObj(i -> () -> (HttpStatus) http
                            .postForEntity("/api/v1/incidents", body, IngestionResponse.class).getStatusCode())
                    .toList();
            List<HttpStatus> statuses = pool.invokeAll(calls).stream().map(IncidentFlowIT::get).toList();

            assertThat(statuses).filteredOn(s -> s == HttpStatus.ACCEPTED).hasSize(1);
            assertThat(statuses).filteredOn(s -> s == HttpStatus.OK).hasSize(9);
        } finally {
            pool.shutdown();
        }
        awaitStatus(eventId, EventStatus.PROCESSED);
    }

    @Test
    void redeliveredMessageForProcessedEventIsSkipped() {
        String eventId = newId("redeliver");
        http.postForEntity("/api/v1/incidents", incident(eventId), IngestionResponse.class);
        IncidentEvent processed = awaitStatus(eventId, EventStatus.PROCESSED);
        double before = meterRegistry.counter("incidents.duplicate.deliveries").count();

        // Simulate RabbitMQ delivering the same message again
        rabbitTemplate.convertAndSend(RabbitTopology.EXCHANGE, RabbitTopology.ROUTING_KEY,
                new IncidentMessage(processed.getId(), eventId));

        await().atMost(TIMEOUT).until(() -> meterRegistry.counter("incidents.duplicate.deliveries").count() > before);
        assertThat(dispatchGateway.callsFor(eventId)).isEqualTo(1);
    }

    // ---------- webhook ----------

    @Test
    void signedWebhookIsAcceptedAndProcessed() {
        String eventId = newId("ptn");
        String body = """
                {"id":"%s","provider":"north-alarms","category":"medical","priority":1,
                 "location":{"lat":53.48,"lng":-2.24},"summary":"Patient not breathing","reported_at":"%s"}
                """.formatted(eventId, Instant.now());
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(WebhookSignatureVerifier.TIMESTAMP_HEADER, timestamp);
        headers.set(WebhookSignatureVerifier.SIGNATURE_HEADER, "sha256=" + signatureVerifier.sign(timestamp + "." + body));

        ResponseEntity<IngestionResponse> response = http.postForEntity("/api/v1/webhooks/partner",
                new HttpEntity<>(body, headers), IngestionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        IncidentEvent processed = awaitStatus(eventId, EventStatus.PROCESSED);
        assertThat(processed.getChannel()).isEqualTo(IngestChannel.WEBHOOK);
        assertThat(processed.getType()).isEqualTo(IncidentType.MEDICAL);
        assertThat(processed.getSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(processed.getPriorityScore()).isEqualTo(100);
    }

    @Test
    void unsignedWebhookIsRejected() {
        String body = "{\"id\":\"%s\",\"category\":\"fire\",\"reported_at\":\"2026-11-25T14:30:00Z\"}".formatted(newId("bad"));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = http.postForEntity("/api/v1/webhooks/partner", new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---------- retries, DLQ and replay ----------

    @Test
    void failingEventIsRetriedThenDeadLetteredThenReplayed() {
        amqpAdmin.purgeQueue(RabbitTopology.DEAD_LETTER_QUEUE, false);
        String eventId = newId("fail");
        dispatchGateway.failEventsStartingWith(eventId);

        http.postForEntity("/api/v1/incidents", incident(eventId), IngestionResponse.class);

        IncidentEvent failed = awaitStatus(eventId, EventStatus.FAILED);
        assertThat(failed.getFailureReason()).contains("CAD system unavailable");
        assertThat(dispatchGateway.callsFor(eventId)).isEqualTo(3); // max-attempts: 3
        await().atMost(TIMEOUT).until(() -> amqpAdmin.getQueueInfo(RabbitTopology.DEAD_LETTER_QUEUE).getMessageCount() == 1);

        // The downstream system comes back; an operator replays the DLQ
        dispatchGateway.recover();
        ResponseEntity<Map> replay = http.postForEntity("/api/v1/admin/dead-letters/replay", null, Map.class);

        assertThat(replay.getBody()).containsEntry("replayed", 1);
        awaitStatus(eventId, EventStatus.PROCESSED);
        assertThat(amqpAdmin.getQueueInfo(RabbitTopology.DEAD_LETTER_QUEUE).getMessageCount()).isZero();
    }

    // ---------- no lost messages ----------

    @Test
    void eventThatNeverReachedRabbitIsRepublished() {
        // Simulates "saved to MySQL, then the app crashed before publishing": a RECEIVED row with no message.
        String eventId = newId("stuck");
        repository.save(new IncidentEvent(eventId, "test", IngestChannel.REST, IncidentType.FLOOD, Severity.MEDIUM,
                53.4, -2.2, "Flooded road", Instant.now().minusSeconds(120), Instant.now().minusSeconds(60)));

        awaitStatus(eventId, EventStatus.PROCESSED);
        assertThat(meterRegistry.counter("incidents.republished").count()).isGreaterThanOrEqualTo(1.0);
    }

    // ---------- validation, stats and monitoring ----------

    @Test
    void invalidRequestIsRejectedWith400() {
        ResponseEntity<String> response = http.postForEntity("/api/v1/incidents", Map.of("source", "x"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("eventId");
    }

    @Test
    void statsAndPrometheusEndpointsExposeCounts() {
        String eventId = newId("metrics");
        http.postForEntity("/api/v1/incidents", incident(eventId), IngestionResponse.class);
        awaitStatus(eventId, EventStatus.PROCESSED);

        Map<?, ?> stats = http.getForObject("/api/v1/incidents/stats", Map.class);
        assertThat(((Number) stats.get("PROCESSED")).longValue()).isPositive();

        String prometheus = http.getForObject("/actuator/prometheus", String.class);
        assertThat(prometheus)
                .contains("incidents_received_total")
                .contains("incidents_processed_total")
                .contains("incidents_processing_duration_seconds")
                .contains("incidents_queue_depth");

        String health = http.getForObject("/actuator/health", String.class);
        assertThat(health).contains("\"status\":\"UP\"").contains("rabbit").contains("db");
    }

    @Test
    void listEndpointFiltersByStatus() {
        String eventId = newId("list");
        http.postForEntity("/api/v1/incidents", incident(eventId), IngestionResponse.class);
        awaitStatus(eventId, EventStatus.PROCESSED);

        Map<?, ?> page = http.getForObject("/api/v1/incidents?status=PROCESSED&size=5", Map.class);
        assertThat((List<?>) page.get("content")).isNotEmpty().hasSizeLessThanOrEqualTo(5);
    }

    // ---------- helpers ----------

    private IncidentEvent awaitStatus(String eventId, EventStatus expected) {
        await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(200)).until(() ->
                repository.findByEventId(eventId).map(e -> e.getStatus() == expected).orElse(false));
        return repository.findByEventId(eventId).orElseThrow();
    }

    private static String newId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static Map<String, Object> incident(String eventId) {
        return Map.of(
                "eventId", eventId,
                "source", "integration-test",
                "type", "FIRE",
                "severity", "HIGH",
                "latitude", 53.4808,
                "longitude", -2.2426,
                "description", "Kitchen fire",
                "occurredAt", Instant.now().minusSeconds(30).toString());
    }

    private static <T> T get(Future<T> f) {
        try {
            return f.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
