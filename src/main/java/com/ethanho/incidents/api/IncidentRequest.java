package com.ethanho.incidents.api;

import com.ethanho.incidents.domain.IncidentType;
import com.ethanho.incidents.domain.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Body of {@code POST /api/v1/incidents}. Bean Validation annotations are checked automatically
 * because the controller parameter is marked {@code @Valid}; failures become a 400 response.
 */
public record IncidentRequest(
        @Schema(description = "Producer's unique ID for this event. Re-sending the same ID is safe (idempotent).",
                example = "evt-2026-000123")
        @NotBlank @Size(max = 100) String eventId,

        @Schema(example = "999-call-centre")
        @NotBlank @Size(max = 100) String source,

        @NotNull IncidentType type,

        @NotNull Severity severity,

        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,

        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,

        @Size(max = 2000) String description,

        @Schema(example = "2026-11-25T14:30:00Z")
        @NotNull Instant occurredAt
) {
}
