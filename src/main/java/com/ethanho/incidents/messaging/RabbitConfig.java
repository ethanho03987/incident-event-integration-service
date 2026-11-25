package com.ethanho.incidents.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.ethanho.incidents.messaging.RabbitTopology.*;

/**
 * Declares the RabbitMQ topology. Spring's RabbitAdmin creates these on the broker at startup
 * if they don't exist yet.
 *
 * <pre>
 *  API ──publish──▶ [incident.events] ──incident.received──▶ (incident.processing) ──▶ consumer
 *                                                                  │
 *                       retries exhausted / rejected               ▼
 *                    [incident.events.dlx] ──incident.dead──▶ (incident.processing.dlq)
 * </pre>
 *
 * Retry settings (3 attempts, exponential back-off) are in application.yml under
 * {@code spring.rabbitmq.listener.simple.retry}. When they run out, {@link FailedIncidentRecoverer}
 * moves the message to the DLQ.
 */
@Configuration
public class RabbitConfig {

    @Bean
    DirectExchange incidentExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    Queue processingQueue() {
        return QueueBuilder.durable(PROCESSING_QUEUE)            // durable: survives a broker restart
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)       // anything rejected goes to the DLX
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    Binding processingBinding(Queue processingQueue, DirectExchange incidentExchange) {
        return BindingBuilder.bind(processingQueue).to(incidentExchange).with(ROUTING_KEY);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DEAD_LETTER_ROUTING_KEY);
    }

    /** Send and receive messages as JSON instead of Java serialisation. Spring Boot wires this in automatically. */
    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
