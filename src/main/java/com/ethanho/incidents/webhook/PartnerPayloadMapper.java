package com.ethanho.incidents.webhook;

import com.ethanho.incidents.api.IncidentRequest;
import com.ethanho.incidents.domain.IncidentType;
import com.ethanho.incidents.domain.Severity;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Translates the partner's webhook format into our internal model (an "anti-corruption layer"). */
@Component
public class PartnerPayloadMapper {

    static final String DEFAULT_SOURCE = "partner-webhook";

    public IncidentRequest toRequest(PartnerWebhookPayload p) {
        if (p.id() == null || p.id().isBlank()) {
            throw new InvalidWebhookPayloadException("'id' is required");
        }
        if (p.reportedAt() == null) {
            throw new InvalidWebhookPayloadException("'reported_at' is required");
        }
        Double lat = p.location() == null ? null : p.location().lat();
        Double lng = p.location() == null ? null : p.location().lng();
        String source = (p.provider() == null || p.provider().isBlank()) ? DEFAULT_SOURCE : p.provider();

        return new IncidentRequest(
                p.id(),
                source,
                mapCategory(p.category()),
                mapPriority(p.priority()),
                lat,
                lng,
                p.summary(),
                p.reportedAt());
    }

    static IncidentType mapCategory(String category) {
        if (category == null) {
            return IncidentType.OTHER;
        }
        return switch (category.trim().toLowerCase(Locale.ROOT)) {
            case "fire", "smoke" -> IncidentType.FIRE;
            case "medical", "ambulance" -> IncidentType.MEDICAL;
            case "crime", "police", "intruder" -> IncidentType.POLICE;
            case "rtc", "traffic", "collision" -> IncidentType.TRAFFIC_COLLISION;
            case "chemical", "hazmat", "gas" -> IncidentType.HAZMAT;
            case "flood", "water" -> IncidentType.FLOOD;
            default -> IncidentType.OTHER;
        };
    }

    /** Partner priority: 1 = most urgent ... 5 = least. Missing or out-of-range values are treated as LOW. */
    static Severity mapPriority(Integer priority) {
        if (priority == null) {
            return Severity.LOW;
        }
        return switch (priority) {
            case 1 -> Severity.CRITICAL;
            case 2 -> Severity.HIGH;
            case 3 -> Severity.MEDIUM;
            default -> Severity.LOW;
        };
    }
}
