package com.ethanho.incidents.processing;

import com.ethanho.incidents.domain.IncidentEvent;
import com.ethanho.incidents.domain.IncidentType;
import com.ethanho.incidents.domain.Severity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Business rules for prioritising an incident. Pure logic with no I/O, so it is easy to unit test.
 *
 * <p>Score (0-100) = base score for severity + bonus for life-threatening incident types
 * + bonus if the description mentions a life-risk keyword. Scores of 90+ get the fastest
 * response target regardless of severity.
 */
@Service
public class TriageService {

    static final int MAX_SCORE = 100;
    static final int URGENT_THRESHOLD = 90;
    static final int URGENT_RESPONSE_MINUTES = Severity.CRITICAL.targetResponseMinutes();

    private static final Set<IncidentType> LIFE_THREATENING_TYPES = Set.of(IncidentType.FIRE, IncidentType.MEDICAL, IncidentType.HAZMAT);
    private static final List<String> LIFE_RISK_KEYWORDS = List.of("trapped", "unconscious", "not breathing", "explosion", "weapon", "child");

    public TriageDecision assess(IncidentEvent event) {
        int score = baseScore(event.getSeverity());
        if (LIFE_THREATENING_TYPES.contains(event.getType())) {
            score += 10;
        }
        if (mentionsLifeRisk(event.getDescription())) {
            score += 15;
        }
        score = Math.min(score, MAX_SCORE);

        int target = event.getSeverity().targetResponseMinutes();
        if (score >= URGENT_THRESHOLD) {
            target = Math.min(target, URGENT_RESPONSE_MINUTES);
        }
        return new TriageDecision(event.getType().respondingService(), score, target);
    }

    static int baseScore(Severity severity) {
        // A "switch expression" (Java 14+): returns a value, and the compiler checks every enum case is covered.
        return switch (severity) {
            case CRITICAL -> 80;
            case HIGH -> 60;
            case MEDIUM -> 40;
            case LOW -> 20;
        };
    }

    static boolean mentionsLifeRisk(String description) {
        if (description == null || description.isBlank()) {
            return false;
        }
        String text = description.toLowerCase(Locale.ROOT);
        return LIFE_RISK_KEYWORDS.stream().anyMatch(text::contains);
    }
}
