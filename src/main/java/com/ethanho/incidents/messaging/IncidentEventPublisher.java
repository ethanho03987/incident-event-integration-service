package com.ethanho.incidents.messaging;

import com.ethanho.incidents.domain.EventStatus;
import com.ethanho.incidents.domain.IncidentEvent;
import com.ethanho.incidents.domain.IncidentEventRepository;
import com.ethanho.incidents.metrics.IncidentMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes saved events to RabbitMQ and waits for the broker to confirm it has them.
 *
 * <p>Why confirms? Without them, {@code convertAndSend} returns as soon as the bytes leave the app,
 * so a broker crash at that moment would lose the message silently. With publisher confirms
 * ({@code spring.rabbitmq.publisher-confirm-type: simple}) we only mark the row QUEUED once
 * RabbitMQ has acknowledged it. If anything fails, the row stays RECEIVED and
 * {@link StuckEventRepublisher} sends it again later. That is how the service avoids losing messages.
 */
@Component
public class IncidentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(IncidentEventPublisher.class);
    private static final long CONFIRM_TIMEOUT_MS = 5_000;

    private final RabbitTemplate rabbitTemplate;
    private final IncidentEventRepository repository;
    private final IncidentMetrics metrics;

    public IncidentEventPublisher(RabbitTemplate rabbitTemplate, IncidentEventRepository repository, IncidentMetrics metrics) {
        this.rabbitTemplate = rabbitTemplate;
        this.repository = repository;
        this.metrics = metrics;
    }

    /**
     * @return true if RabbitMQ confirmed the message, false if it will need re-publishing
     */
    public boolean publish(IncidentEvent event) {
        IncidentMessage message = new IncidentMessage(event.getId(), event.getEventId());
        try {
            // invoke() runs both calls on the same channel, so we wait for the confirm of this exact message.
            rabbitTemplate.invoke(ops -> {
                ops.convertAndSend(RabbitTopology.EXCHANGE, RabbitTopology.ROUTING_KEY, message);
                ops.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
                return null;
            });
        } catch (RuntimeException e) {
            log.warn("Publish not confirmed for eventId={}; leaving as RECEIVED for the republisher: {}",
                    event.getEventId(), e.getMessage());
            metrics.publishFailed();
            return false;
        }
        // Conditional update: only RECEIVED -> QUEUED. If the consumer already marked it PROCESSED, leave it alone.
        repository.transitionStatus(event.getId(), EventStatus.RECEIVED, EventStatus.QUEUED);
        return true;
    }
}
