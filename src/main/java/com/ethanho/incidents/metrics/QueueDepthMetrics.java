package com.ethanho.incidents.metrics;

import com.ethanho.incidents.messaging.RabbitTopology;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.stereotype.Component;

/**
 * Exposes how many messages are waiting in the processing queue and the DLQ, so Grafana can show
 * a backlog building up or failures piling into the DLQ.
 */
@Component
public class QueueDepthMetrics {

    public QueueDepthMetrics(MeterRegistry registry, AmqpAdmin amqpAdmin) {
        register(registry, amqpAdmin, RabbitTopology.PROCESSING_QUEUE, "processing");
        register(registry, amqpAdmin, RabbitTopology.DEAD_LETTER_QUEUE, "dead_letter");
    }

    private static void register(MeterRegistry registry, AmqpAdmin admin, String queueName, String label) {
        Gauge.builder("incidents.queue.depth", admin, a -> depth(a, queueName))
                .description("Messages currently waiting in the queue")
                .tag("queue", label)
                .register(registry);
    }

    static double depth(AmqpAdmin admin, String queueName) {
        try {
            QueueInformation info = admin.getQueueInfo(queueName);
            return info == null ? Double.NaN : info.getMessageCount();
        } catch (RuntimeException e) {
            return Double.NaN; // broker unreachable: report "no data" rather than break the scrape
        }
    }
}
