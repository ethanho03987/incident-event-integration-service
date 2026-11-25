package com.ethanho.incidents.api;

/**
 * Returned when an event is submitted.
 *
 * @param eventId   the producer's event ID
 * @param status    ACCEPTED for a new event, DUPLICATE if we already had it
 * @param duplicate convenience flag for clients
 */
public record IngestionResponse(String eventId, String status, boolean duplicate) {

    public static IngestionResponse accepted(String eventId) {
        return new IngestionResponse(eventId, "ACCEPTED", false);
    }

    public static IngestionResponse duplicateOf(String eventId) {
        return new IngestionResponse(eventId, "DUPLICATE", true);
    }
}
