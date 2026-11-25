package com.ethanho.incidents.processing;

import com.ethanho.incidents.domain.IncidentType;
import com.ethanho.incidents.domain.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.ethanho.incidents.TestData.event;
import static org.assertj.core.api.Assertions.assertThat;

class TriageServiceTest {

    private final TriageService triage = new TriageService();

    @Test
    void criticalFireWithPeopleTrappedGetsMaximumPriority() {
        TriageDecision d = triage.assess(event("e1", IncidentType.FIRE, Severity.CRITICAL, "Family TRAPPED upstairs"));

        assertThat(d.assignedService()).isEqualTo("Fire and Rescue");
        assertThat(d.priorityScore()).isEqualTo(100);          // 80 + 10 + 15 = 105, capped at 100
        assertThat(d.responseTargetMinutes()).isEqualTo(8);
    }

    @Test
    void lowPriorityPoliceIncidentKeepsItsNormalTarget() {
        TriageDecision d = triage.assess(event("e2", IncidentType.POLICE, Severity.LOW, "Noise complaint"));

        assertThat(d.assignedService()).isEqualTo("Police");
        assertThat(d.priorityScore()).isEqualTo(20);
        assertThat(d.responseTargetMinutes()).isEqualTo(60);
    }

    @Test
    void highScoreUpgradesResponseTargetToUrgent() {
        // HIGH (60) + MEDICAL (10) + keyword (15) = 85: below the urgent threshold, keeps 15 minutes
        TriageDecision high = triage.assess(event("e3", IncidentType.MEDICAL, Severity.HIGH, "Patient unconscious"));
        assertThat(high.priorityScore()).isEqualTo(85);
        assertThat(high.responseTargetMinutes()).isEqualTo(15);

        // CRITICAL (80) + MEDICAL (10) = 90: hits the threshold
        TriageDecision critical = triage.assess(event("e4", IncidentType.MEDICAL, Severity.CRITICAL, null));
        assertThat(critical.priorityScore()).isEqualTo(90);
        assertThat(critical.responseTargetMinutes()).isEqualTo(8);
    }

    @ParameterizedTest
    @CsvSource({"CRITICAL,80", "HIGH,60", "MEDIUM,40", "LOW,20"})
    void baseScoreFollowsSeverity(Severity severity, int expected) {
        assertThat(TriageService.baseScore(severity)).isEqualTo(expected);
    }

    @Test
    void keywordDetectionIsCaseInsensitiveAndNullSafe() {
        assertThat(TriageService.mentionsLifeRisk("Man NOT BREATHING")).isTrue();
        assertThat(TriageService.mentionsLifeRisk("Bin fire")).isFalse();
        assertThat(TriageService.mentionsLifeRisk(null)).isFalse();
        assertThat(TriageService.mentionsLifeRisk("   ")).isFalse();
    }
}
