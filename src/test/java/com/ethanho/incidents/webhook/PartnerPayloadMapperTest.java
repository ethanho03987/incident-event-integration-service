package com.ethanho.incidents.webhook;

import com.ethanho.incidents.api.IncidentRequest;
import com.ethanho.incidents.domain.IncidentType;
import com.ethanho.incidents.domain.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartnerPayloadMapperTest {

    private final PartnerPayloadMapper mapper = new PartnerPayloadMapper();
    private static final Instant REPORTED = Instant.parse("2026-11-25T14:30:00Z");

    @Test
    void mapsFullPayload() {
        PartnerWebhookPayload payload = new PartnerWebhookPayload("ptn-1", "north-alarms", "fire", 1,
                new PartnerWebhookPayload.Location(53.48, -2.24), "Smoke alarm", REPORTED);

        IncidentRequest r = mapper.toRequest(payload);

        assertThat(r.eventId()).isEqualTo("ptn-1");
        assertThat(r.source()).isEqualTo("north-alarms");
        assertThat(r.type()).isEqualTo(IncidentType.FIRE);
        assertThat(r.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(r.latitude()).isEqualTo(53.48);
        assertThat(r.longitude()).isEqualTo(-2.24);
        assertThat(r.description()).isEqualTo("Smoke alarm");
        assertThat(r.occurredAt()).isEqualTo(REPORTED);
    }

    @Test
    void fillsDefaultsForOptionalFields() {
        IncidentRequest r = mapper.toRequest(new PartnerWebhookPayload("ptn-2", null, null, null, null, null, REPORTED));

        assertThat(r.source()).isEqualTo(PartnerPayloadMapper.DEFAULT_SOURCE);
        assertThat(r.type()).isEqualTo(IncidentType.OTHER);
        assertThat(r.severity()).isEqualTo(Severity.LOW);
        assertThat(r.latitude()).isNull();
    }

    @Test
    void requiresIdAndTimestamp() {
        assertThatThrownBy(() -> mapper.toRequest(new PartnerWebhookPayload(" ", null, "fire", 1, null, null, REPORTED)))
                .isInstanceOf(InvalidWebhookPayloadException.class).hasMessageContaining("id");
        assertThatThrownBy(() -> mapper.toRequest(new PartnerWebhookPayload("ptn-3", null, "fire", 1, null, null, null)))
                .isInstanceOf(InvalidWebhookPayloadException.class).hasMessageContaining("reported_at");
    }

    @ParameterizedTest
    @CsvSource({"fire,FIRE", "SMOKE,FIRE", "ambulance,MEDICAL", "intruder,POLICE", "rtc,TRAFFIC_COLLISION",
            "gas,HAZMAT", " flood ,FLOOD", "alien landing,OTHER"})
    void mapsCategories(String category, IncidentType expected) {
        assertThat(PartnerPayloadMapper.mapCategory(category)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"1,CRITICAL", "2,HIGH", "3,MEDIUM", "4,LOW", "5,LOW", "99,LOW"})
    void mapsPriorities(int priority, Severity expected) {
        assertThat(PartnerPayloadMapper.mapPriority(priority)).isEqualTo(expected);
    }
}
