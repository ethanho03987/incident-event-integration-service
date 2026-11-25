package com.ethanho.incidents.messaging;

import com.ethanho.incidents.processing.IncidentFailureService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.GetResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Moves messages from the DLQ back onto the main exchange once the underlying problem is fixed
 * (for example the dispatch system is back up).
 *
 * <p>Each message is only acknowledged (removed from the DLQ) after RabbitMQ confirms the re-published
 * copy, so a crash part-way through can duplicate a message but never lose one. Duplicates are fine
 * because the consumer is idempotent.
 */
@Service
public class DeadLetterReplayService {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterReplayService.class);
    private static final long CONFIRM_TIMEOUT_MS = 5_000;

    private final RabbitTemplate rabbitTemplate;
    private final IncidentFailureService failureService;
    private final ObjectMapper objectMapper;

    public DeadLetterReplayService(RabbitTemplate rabbitTemplate, IncidentFailureService failureService, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.failureService = failureService;
        this.objectMapper = objectMapper;
    }

    /** @return the event IDs that were replayed (unreadable messages are replayed but not listed) */
    public List<String> replay(int maxMessages) {
        List<byte[]> replayedBodies = rabbitTemplate.execute(channel -> {
            List<byte[]> bodies = new ArrayList<>();
            channel.confirmSelect();
            while (bodies.size() < maxMessages) {
                GetResponse response = channel.basicGet(RabbitTopology.DEAD_LETTER_QUEUE, false); // false = manual ack
                if (response == null) {
                    break; // DLQ is empty
                }
                channel.basicPublish(RabbitTopology.EXCHANGE, RabbitTopology.ROUTING_KEY,
                        response.getProps(), response.getBody());
                channel.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
                channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
                bodies.add(response.getBody());
            }
            return bodies;
        });

        List<String> eventIds = new ArrayList<>();
        for (byte[] body : replayedBodies) {
            try {
                String eventId = objectMapper.readValue(body, IncidentMessage.class).eventId();
                failureService.markRequeued(eventId);
                eventIds.add(eventId);
            } catch (IOException e) {
                log.warn("Replayed a dead letter whose body could not be parsed");
            }
        }
        log.info("Replayed {} dead-lettered messages", replayedBodies.size());
        return eventIds;
    }
}
