package com.ethanho.incidents.ingestion;

import com.ethanho.incidents.domain.IncidentEvent;

/** Result of ingesting an event: the stored event, and whether it was a duplicate of one we already had. */
public record IngestionResult(IncidentEvent event, boolean duplicate) {
}
