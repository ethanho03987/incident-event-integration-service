package com.ethanho.incidents.messaging;

import com.ethanho.incidents.metrics.IncidentMetrics;
import com.ethanho.incidents.processing.IncidentFailureService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * Runs when a message has failed every retry attempt.
 *
 * <ol>
 *   <li>Re-publishes it to the dead-letter exchange, adding headers with the exception message and
 *       stack trace ({@code x-exception-message}, {@code x-exception-stacktrace}) so it can be investigated.</li>
 *   <li>Marks the event FAILED in MySQL with the reason, so the API shows what happened.</li>
 * </ol>
 *
 * Spring Boot plugs any {@code MessageRecoverer} bean into the listener's retry interceptor automatically.
 * The message stays in the DLQ until someone replays it ({@code POST /api/v1/admin/dead-letters/replay}).
 */
@Component
public class FailedIncidentRecoverer implements MessageRecoverer {

    private static final Logger log = LoggerFactory.getLogger(FailedIncidentRecoverer.class);

    private final MessageRecoverer republisher;
    private final IncidentFailureService failureService;
    private final IncidentMetrics metrics;
    private final ObjectMapper objectMapper;

    public FailedIncidentRecoverer(AmqpTemplate amqpTemplate, IncidentFailureService failureService,
                                   IncidentMetrics metrics, ObjectMapper objectMapper) {
        this.republisher = new RepublishMessageRecoverer(amqpTemplate,
                RabbitTopology.DEAD_LETTER_EXCHANGE, RabbitTopology.DEAD_LETTER_ROUTING_KEY);
        this.failureService = failureService;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    @Override
    public void recover(Message message, Throwable cause) {
        republisher.recover(message, cause);
        metrics.deadLettered();

        String reason = rootCauseMessage(cause);
        Optional<String> eventId = readEventId(message);
        log.error("Dead-lettered eventId={} after retries: {}", eventId.orElse("<unreadable>"), reason);
        eventId.ifPresent(id -> failureService.markFailed(id, reason));
    }

    private Optional<String> readEventId(Message message) {
        try {
            IncidentMessage parsed = objectMapper.readValue(message.getBody(), IncidentMessage.class);
            return Optional.ofNullable(parsed.eventId());
        } catch (IOException e) {
            return Optional.empty(); // a "poison" message with a body we can't parse
        }
    }

    static String rootCauseMessage(Throwable cause) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(cause);
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
