package com.ethanho.incidents.messaging;

import com.ethanho.incidents.config.AppProperties;
import com.ethanho.incidents.domain.EventStatus;
import com.ethanho.incidents.domain.IncidentEvent;
import com.ethanho.incidents.domain.IncidentEventRepository;
import com.ethanho.incidents.metrics.IncidentMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Safety net for the gap between "saved in MySQL" and "confirmed by RabbitMQ".
 *
 * <p>If the broker was down, or the app crashed straight after saving, the row is stuck in RECEIVED.
 * This job finds those rows and publishes them again. Re-sending is safe because the consumer is
 * idempotent: it skips events that are already PROCESSED. This is a lightweight version of the
 * "transactional outbox" pattern.
 */
@Component
public class StuckEventRepublisher {

    private static final Logger log = LoggerFactory.getLogger(StuckEventRepublisher.class);

    private final IncidentEventRepository repository;
    private final IncidentEventPublisher publisher;
    private final IncidentMetrics metrics;
    private final AppProperties.Republisher settings;
    private final Clock clock;

    public StuckEventRepublisher(IncidentEventRepository repository, IncidentEventPublisher publisher,
                                 IncidentMetrics metrics, AppProperties properties, Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.metrics = metrics;
        this.settings = properties.republisher();
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.republisher.interval:PT15S}", initialDelayString = "${app.republisher.interval:PT15S}")
    public int republishStuckEvents() {
        Instant cutoff = clock.instant().minus(settings.stuckAfter());
        List<IncidentEvent> stuck = repository.findByStatusAndReceivedAtBeforeOrderByReceivedAtAsc(
                EventStatus.RECEIVED, cutoff, PageRequest.of(0, settings.batchSize()));
        if (stuck.isEmpty()) {
            return 0;
        }
        int sent = 0;
        for (IncidentEvent event : stuck) {
            if (publisher.publish(event)) {
                sent++;
            }
        }
        log.info("Republisher found {} stuck events, re-published {}", stuck.size(), sent);
        metrics.republished(sent);
        return sent;
    }
}
