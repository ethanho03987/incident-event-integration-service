package com.ethanho.incidents.processing;

import com.ethanho.incidents.domain.IncidentEvent;

/**
 * The downstream system that actually sends a unit out. In a real control room this would be an HTTP
 * or messaging call to a computer-aided dispatch (CAD) system. It is an interface so tests can mock it
 * and so the real client could be swapped in without touching the processor.
 *
 * <p>Implementations should treat {@code event.getEventId()} as an idempotency key, because an
 * event can be dispatched more than once if a retry happens after a partial failure.
 */
public interface DispatchGateway {

    /**
     * @throws DispatchUnavailableException if the downstream system can't take the request right now
     */
    void dispatch(IncidentEvent event, TriageDecision decision);
}
