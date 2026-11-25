package com.ethanho.incidents.messaging;

/** Names of the RabbitMQ exchanges, queues and routing keys, so they are defined once. */
public final class RabbitTopology {

    public static final String EXCHANGE = "incident.events";
    public static final String ROUTING_KEY = "incident.received";
    public static final String PROCESSING_QUEUE = "incident.processing";

    public static final String DEAD_LETTER_EXCHANGE = "incident.events.dlx";
    public static final String DEAD_LETTER_ROUTING_KEY = "incident.dead";
    public static final String DEAD_LETTER_QUEUE = "incident.processing.dlq";

    private RabbitTopology() {
        // constants only
    }
}
