package com.ethanho.incidents.ingestion;

import com.ethanho.incidents.api.IncidentRequest;
import com.ethanho.incidents.domain.IncidentEvent;
import com.ethanho.incidents.domain.IncidentEventRepository;
import com.ethanho.incidents.domain.IngestChannel;
import com.ethanho.incidents.messaging.IncidentEventPublisher;
import com.ethanho.incidents.metrics.IncidentMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;

/**
 * Shared ingestion path for both REST and webhook events.
 *
 * <ol>
 *   <li><b>Duplicate check</b> – if we already have this eventId, return the existing one.</li>
 *   <li><b>Persist</b> – save to MySQL first, so the event is safe even if RabbitMQ is down.</li>
 *   <li><b>Publish</b> – hand the event to RabbitMQ. The HTTP caller gets a reply straight away
 *       and does not wait for processing. This is what "decoupling ingestion from processing" means.</li>
 * </ol>
 *
 * Deliberately <i>not</i> {@code @Transactional}: the insert must be committed before we publish,
 * otherwise the consumer could receive the message before the row is visible in the database.
 */
@Service
public class IncidentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(IncidentIngestionService.class);

    private final IncidentEventRepository repository;
    private final IncidentEventPublisher publisher;
    private final IncidentMetrics metrics;
    private final Clock clock;

    public IncidentIngestionService(IncidentEventRepository repository, IncidentEventPublisher publisher,
                                    IncidentMetrics metrics, Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.metrics = metrics;
        this.clock = clock;
    }

    public IngestionResult ingest(IncidentRequest request, IngestChannel channel) {
        Optional<IncidentEvent> existing = repository.findByEventId(request.eventId());
        if (existing.isPresent()) {
            return duplicate(existing.get(), channel);
        }

        IncidentEvent saved;
        try {
            saved = repository.saveAndFlush(toEntity(request, channel));
        } catch (DataIntegrityViolationException e) {
            // Two identical requests arrived at the same moment: both passed the check above, but the
            // UNIQUE constraint on event_id let only one INSERT through. Treat the loser as a duplicate.
            IncidentEvent winner = repository.findByEventId(request.eventId()).orElseThrow(() -> e);
            return duplicate(winner, channel);
        }

        metrics.received(channel);
        publisher.publish(saved); // if this isn't confirmed, the republisher retries it later
        log.debug("Accepted eventId={} via {}", saved.getEventId(), channel);
        return new IngestionResult(saved, false);
    }

    private IngestionResult duplicate(IncidentEvent event, IngestChannel channel) {
        log.info("Duplicate eventId={} received via {}; ignoring", event.getEventId(), channel);
        metrics.duplicateRejected(channel);
        return new IngestionResult(event, true);
    }

    private IncidentEvent toEntity(IncidentRequest r, IngestChannel channel) {
        return new IncidentEvent(r.eventId(), r.source(), channel, r.type(), r.severity(),
                r.latitude(), r.longitude(), r.description(), r.occurredAt(), clock.instant());
    }
}
