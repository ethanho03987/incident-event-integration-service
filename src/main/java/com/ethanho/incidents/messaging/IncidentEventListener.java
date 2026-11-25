package com.ethanho.incidents.messaging;

import com.ethanho.incidents.metrics.IncidentMetrics;
import com.ethanho.incidents.processing.IncidentProcessor;
import com.ethanho.incidents.processing.ProcessingResult;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * RabbitMQ consumer. Spring calls {@link #onMessage} for every message on the processing queue,
 * on several threads at once (see {@code spring.rabbitmq.listener.simple.concurrency}).
 *
 * <p>If this method returns normally, the message is acknowledged and removed from the queue.
 * If it throws, the retry interceptor tries again with back-off; after the last attempt
 * {@link FailedIncidentRecoverer} sends it to the dead-letter queue.
 */
@Component
public class IncidentEventListener {

    private final IncidentProcessor processor;
    private final IncidentMetrics metrics;

    public IncidentEventListener(IncidentProcessor processor, IncidentMetrics metrics) {
        this.processor = processor;
        this.metrics = metrics;
    }

    @RabbitListener(queues = RabbitTopology.PROCESSING_QUEUE)
    public void onMessage(IncidentMessage message) {
        long start = System.nanoTime();
        ProcessingResult result = processor.process(message);
        metrics.processingTimer().record(Duration.ofNanos(System.nanoTime() - start));
        // Metrics are recorded here, after the transaction has committed, so a rolled-back attempt isn't counted.
        if (result.outcome() == ProcessingResult.Outcome.ALREADY_PROCESSED) {
            metrics.duplicateDelivery();
        } else {
            metrics.processed(result.type(), result.severity(), result.endToEnd());
        }
    }
}
