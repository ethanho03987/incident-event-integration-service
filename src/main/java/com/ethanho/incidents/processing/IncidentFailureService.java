package com.ethanho.incidents.processing;

import com.ethanho.incidents.domain.EventStatus;
import com.ethanho.incidents.domain.IncidentEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Updates an event's status when it is dead-lettered or replayed out of the DLQ. */
@Service
public class IncidentFailureService {

    private final IncidentEventRepository repository;

    public IncidentFailureService(IncidentEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void markFailed(String eventId, String reason) {
        repository.findByEventId(eventId)
                .filter(e -> !e.isProcessed()) // never downgrade an event that did get processed
                .ifPresent(e -> e.markFailed(reason));
    }

    /** Moves FAILED back to QUEUED when a dead letter is replayed. Returns true if the row changed. */
    @Transactional
    public boolean markRequeued(String eventId) {
        return repository.findByEventId(eventId)
                .map(e -> repository.transitionStatus(e.getId(), EventStatus.FAILED, EventStatus.QUEUED) == 1)
                .orElse(false);
    }
}
