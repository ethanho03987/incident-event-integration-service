-- Flyway runs this once on startup and records it in flyway_schema_history.
-- Never edit a migration that has already run; add V2__..., V3__... instead.

CREATE TABLE incident_events (
    id                      BIGINT        NOT NULL AUTO_INCREMENT,
    event_id                VARCHAR(100)  NOT NULL,
    source                  VARCHAR(100)  NOT NULL,
    channel                 VARCHAR(20)   NOT NULL,
    type                    VARCHAR(30)   NOT NULL,
    severity                VARCHAR(20)   NOT NULL,
    latitude                DOUBLE        NULL,
    longitude               DOUBLE        NULL,
    description             VARCHAR(2000) NULL,
    occurred_at             DATETIME(6)   NOT NULL,
    received_at             DATETIME(6)   NOT NULL,
    status                  VARCHAR(20)   NOT NULL,
    assigned_service        VARCHAR(50)   NULL,
    priority_score          INT           NULL,
    response_target_minutes INT           NULL,
    processed_at            DATETIME(6)   NULL,
    failure_reason          VARCHAR(500)  NULL,
    version                 BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    -- The idempotency guarantee: one row per producer event ID.
    CONSTRAINT uk_incident_events_event_id UNIQUE (event_id)
);

-- Supports the republisher query (status = 'RECEIVED' AND received_at < ?) and status filtering in the API.
CREATE INDEX idx_incident_events_status_received ON incident_events (status, received_at);
