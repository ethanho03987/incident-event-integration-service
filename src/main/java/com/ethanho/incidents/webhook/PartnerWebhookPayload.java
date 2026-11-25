package com.ethanho.incidents.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * The JSON a partner system (for example a neighbouring control room or an alarm-monitoring company)
 * sends to our webhook. It uses its own field names and a 1-5 priority instead of our severity levels,
 * so {@link PartnerPayloadMapper} translates it into our internal {@code IncidentRequest}.
 *
 * <pre>
 * {
 *   "id": "ptn-88213",
 *   "provider": "north-alarm-monitoring",
 *   "category": "fire",
 *   "priority": 1,
 *   "location": { "lat": 53.4808, "lng": -2.2426 },
 *   "summary": "Smoke alarm triggered, occupants trapped on 2nd floor",
 *   "reported_at": "2026-11-25T14:30:00Z"
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true) // partners add fields over time; don't break when they do
public record PartnerWebhookPayload(
        String id,
        String provider,
        String category,
        Integer priority,
        Location location,
        String summary,
        @JsonProperty("reported_at") Instant reportedAt
) {
    public record Location(Double lat, Double lng) {}
}
