package com.ethanho.incidents.processing;

import com.ethanho.incidents.domain.IncidentEvent;
import com.ethanho.incidents.domain.IncidentEventRepository;
import com.ethanho.incidents.messaging.IncidentMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Handles one event taken off the queue: load it, skip it if already done, triage it, dispatch it, save.
 *
 * <p><b>Idempotency on the consumer side.</b> RabbitMQ guarantees "at least once" delivery, so the same
 * message can arrive twice (a redelivery after a crash, or a re-publish from the republisher). The
 * {@code isProcessed()} check makes the second delivery a no-op. If two copies are processed at exactly
 * the same time, the {@code @Version} column makes one of the two commits fail with an optimistic-lock
 * error; that copy is retried, sees PROCESSED and is skipped.
 */
@Service
public class IncidentProcessor {

    private static final Logger log = LoggerFactory.getLogger(IncidentProcessor.class);

    private final IncidentEventRepository repository;
    private final TriageService triageService;
    private final DispatchGateway dispatchGateway;
    private final Clock clock;

    public IncidentProcessor(IncidentEventRepository repository, TriageService triageService,
                             DispatchGateway dispatchGateway, Clock clock) {
        this.repository = repository;
        this.triageService = triageService;
        this.dispatchGateway = dispatchGateway;
        this.clock = clock;
    }

    /**
     * Runs in one database transaction. If anything throws, the changes are rolled back and the
     * exception reaches the listener container, which retries and finally dead-letters the message.
     */
    @Transactional
    public ProcessingResult process(IncidentMessage message) {
        IncidentEvent event = repository.findByEventId(message.eventId())
                .orElseThrow(() -> new IllegalStateException("Message refers to unknown eventId=" + message.eventId()));

        if (event.isProcessed()) {
            log.info("Skipping duplicate delivery for eventId={}", event.getEventId());
            return ProcessingResult.alreadyProcessed(event.getType(), event.getSeverity());
        }

        TriageDecision decision = triageService.assess(event);
        dispatchGateway.dispatch(event, decision);

        Instant now = clock.instant();
        event.markProcessed(decision.assignedService(), decision.priorityScore(), decision.responseTargetMinutes(), now);
        // No explicit save() needed: the entity is "managed" inside this transaction, so Hibernate
        // detects the change ("dirty checking") and issues the UPDATE when the transaction commits.

        return new ProcessingResult(ProcessingResult.Outcome.PROCESSED, event.getType(), event.getSeverity(),
                Duration.between(event.getReceivedAt(), now));
    }
}
