package com.ethanho.incidents.api;

/** Thrown when an incident with the requested event ID does not exist. Mapped to HTTP 404. */
public class IncidentNotFoundException extends RuntimeException {

    public IncidentNotFoundException(String eventId) {
        super("No incident with eventId '" + eventId + "'");
    }
}
