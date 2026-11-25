package com.ethanho.incidents.api;

import com.ethanho.incidents.domain.EventStatus;
import com.ethanho.incidents.domain.IncidentEvent;
import com.ethanho.incidents.domain.IncidentType;
import com.ethanho.incidents.domain.IngestChannel;
import com.ethanho.incidents.domain.Severity;

import java.time.Instant;

/** What the API returns for an incident. Kept separate from the entity so the DB shape can change freely. */
public record IncidentResponse(
        String eventId,
        String source,
        IngestChannel channel,
        IncidentType type,
        Severity severity,
        Double latitude,
        Double longitude,
        String description,
        Instant occurredAt,
        Instant receivedAt,
        EventStatus status,
        String assignedService,
        Integer priorityScore,
        Integer responseTargetMinutes,
        Instant processedAt,
        String failureReason
) {
    public static IncidentResponse from(IncidentEvent e) {
        return new IncidentResponse(
                e.getEventId(), e.getSource(), e.getChannel(), e.getType(), e.getSeverity(),
                e.getLatitude(), e.getLongitude(), e.getDescription(), e.getOccurredAt(), e.getReceivedAt(),
                e.getStatus(), e.getAssignedService(), e.getPriorityScore(), e.getResponseTargetMinutes(),
                e.getProcessedAt(), e.getFailureReason());
    }
}
