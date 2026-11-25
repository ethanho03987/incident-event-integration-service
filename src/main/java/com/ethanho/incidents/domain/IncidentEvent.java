package com.ethanho.incidents.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * JPA entity mapped to the {@code incident_events} table (created by Flyway, see V1__create_incident_events.sql).
 *
 * <p>{@code eventId} is the producer's own ID for the event. It has a UNIQUE constraint in MySQL,
 * which is what makes ingestion idempotent: the same event sent twice is stored once.
 */
@Entity
@Table(name = "incident_events")
public class IncidentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // MySQL AUTO_INCREMENT
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 100)
    private String eventId;

    @Column(nullable = false, length = 100)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IngestChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IncidentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity;

    private Double latitude;
    private Double longitude;

    @Column(length = 2000)
    private String description;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status;

    @Column(name = "assigned_service", length = 50)
    private String assignedService;

    @Column(name = "priority_score")
    private Integer priorityScore;

    @Column(name = "response_target_minutes")
    private Integer responseTargetMinutes;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    /** Optimistic locking: Hibernate bumps this on every update and rejects stale writes. */
    @Version
    private Long version;

    /** JPA needs a no-args constructor. {@code protected} stops other code using it by accident. */
    protected IncidentEvent() {
    }

    public IncidentEvent(String eventId, String source, IngestChannel channel, IncidentType type,
                         Severity severity, Double latitude, Double longitude, String description,
                         Instant occurredAt, Instant receivedAt) {
        this.eventId = eventId;
        this.source = source;
        this.channel = channel;
        this.type = type;
        this.severity = severity;
        this.latitude = latitude;
        this.longitude = longitude;
        this.description = description;
        this.occurredAt = occurredAt;
        this.receivedAt = receivedAt;
        this.status = EventStatus.RECEIVED;
    }

    /** Called by the consumer once triage and dispatch succeed. */
    public void markProcessed(String assignedService, int priorityScore, int responseTargetMinutes, Instant processedAt) {
        this.assignedService = assignedService;
        this.priorityScore = priorityScore;
        this.responseTargetMinutes = responseTargetMinutes;
        this.processedAt = processedAt;
        this.status = EventStatus.PROCESSED;
        this.failureReason = null;
    }

    /** Called by the dead-letter listener once all retries are used up. */
    public void markFailed(String reason) {
        this.status = EventStatus.FAILED;
        this.failureReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 500));
    }

    public boolean isProcessed() {
        return status == EventStatus.PROCESSED;
    }

    // Getters. (In C# these would be properties; Java uses getX() methods by convention.)
    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getSource() { return source; }
    public IngestChannel getChannel() { return channel; }
    public IncidentType getType() { return type; }
    public Severity getSeverity() { return severity; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getDescription() { return description; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public EventStatus getStatus() { return status; }
    public String getAssignedService() { return assignedService; }
    public Integer getPriorityScore() { return priorityScore; }
    public Integer getResponseTargetMinutes() { return responseTargetMinutes; }
    public Instant getProcessedAt() { return processedAt; }
    public String getFailureReason() { return failureReason; }
    public Long getVersion() { return version; }
}
