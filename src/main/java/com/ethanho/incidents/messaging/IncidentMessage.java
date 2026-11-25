package com.ethanho.incidents.messaging;

/**
 * The message placed on RabbitMQ. It only carries IDs; the consumer loads the full event from MySQL.
 * Keeping messages small and making MySQL the source of truth means a message can be re-sent
 * any number of times without the data drifting.
 */
public record IncidentMessage(Long incidentId, String eventId) {
}
