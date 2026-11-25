package com.ethanho.incidents.integration;

import com.ethanho.incidents.domain.IncidentEvent;
import com.ethanho.incidents.processing.DispatchGateway;
import com.ethanho.incidents.processing.DispatchUnavailableException;
import com.ethanho.incidents.processing.TriageDecision;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test double for the dispatch system. Tests can make it fail for chosen event IDs and count calls.
 * Thread-safe, because RabbitMQ consumers call it from several threads at once.
 */
public class ControllableDispatchGateway implements DispatchGateway {

    private volatile String failPrefix = null;
    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();

    @Override
    public void dispatch(IncidentEvent event, TriageDecision decision) {
        calls.computeIfAbsent(event.getEventId(), k -> new AtomicInteger()).incrementAndGet();
        String prefix = failPrefix;
        if (prefix != null && event.getEventId().startsWith(prefix)) {
            throw new DispatchUnavailableException("CAD system unavailable (test)");
        }
    }

    public void failEventsStartingWith(String prefix) {
        this.failPrefix = prefix;
    }

    public void recover() {
        this.failPrefix = null;
    }

    public int callsFor(String eventId) {
        AtomicInteger n = calls.get(eventId);
        return n == null ? 0 : n.get();
    }
}
