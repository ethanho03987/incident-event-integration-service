package com.ethanho.incidents.processing;

import com.ethanho.incidents.domain.IncidentType;
import com.ethanho.incidents.domain.Severity;

import java.time.Duration;

/** What happened when the consumer handled one message. */
public record ProcessingResult(Outcome outcome, IncidentType type, Severity severity, Duration endToEnd) {

    public enum Outcome {
        PROCESSED,
        /** The event was already processed, so this delivery was a duplicate and was skipped. */
        ALREADY_PROCESSED
    }

    public static ProcessingResult alreadyProcessed(IncidentType type, Severity severity) {
        return new ProcessingResult(Outcome.ALREADY_PROCESSED, type, severity, Duration.ZERO);
    }
}
