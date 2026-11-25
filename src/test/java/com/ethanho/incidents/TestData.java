package com.ethanho.incidents;

import com.ethanho.incidents.api.IncidentRequest;
import com.ethanho.incidents.domain.IncidentEvent;
import com.ethanho.incidents.domain.IncidentType;
import com.ethanho.incidents.domain.IngestChannel;
import com.ethanho.incidents.domain.Severity;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

/** Small factory for test objects, so each test only spells out the fields it cares about. */
public final class TestData {

    public static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");

    private TestData() {
    }

    public static IncidentRequest request(String eventId) {
        return new IncidentRequest(eventId, "999-call-centre", IncidentType.FIRE, Severity.HIGH,
                53.4808, -2.2426, "Kitchen fire in flat", NOW.minusSeconds(60));
    }

    public static IncidentEvent event(String eventId, IncidentType type, Severity severity, String description) {
        IncidentEvent e = new IncidentEvent(eventId, "test", IngestChannel.REST, type, severity,
                53.48, -2.24, description, NOW.minusSeconds(60), NOW.minusSeconds(5));
        ReflectionTestUtils.setField(e, "id", 42L); // normally set by MySQL AUTO_INCREMENT
        return e;
    }

    public static IncidentEvent event(String eventId) {
        return event(eventId, IncidentType.FIRE, Severity.HIGH, "Kitchen fire");
    }
}
