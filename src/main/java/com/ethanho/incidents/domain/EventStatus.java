package com.ethanho.incidents.domain;

/**
 * Lifecycle of an event:
 * <pre>
 *   RECEIVED  -> saved in MySQL, not yet confirmed on RabbitMQ
 *   QUEUED    -> RabbitMQ confirmed it has the message
 *   PROCESSED -> consumer triaged and dispatched it
 *   FAILED    -> all retries used up; message is in the dead-letter queue
 * </pre>
 */
public enum EventStatus {
    RECEIVED,
    QUEUED,
    PROCESSED,
    FAILED
}
