package com.ethanho.incidents.messaging;

import com.ethanho.incidents.metrics.QueueDepthMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueueDepthMetricsTest {

    @Test
    void reportsQueueDepthAndNaNWhenBrokerIsUnreachable() {
        AmqpAdmin admin = mock(AmqpAdmin.class);
        when(admin.getQueueInfo(RabbitTopology.PROCESSING_QUEUE)).thenReturn(new QueueInformation(RabbitTopology.PROCESSING_QUEUE, 12, 4));
        when(admin.getQueueInfo(RabbitTopology.DEAD_LETTER_QUEUE)).thenThrow(new AmqpConnectException(new ConnectException()));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new QueueDepthMetrics(registry, admin);

        assertThat(registry.get("incidents.queue.depth").tag("queue", "processing").gauge().value()).isEqualTo(12.0);
        assertThat(registry.get("incidents.queue.depth").tag("queue", "dead_letter").gauge().value()).isNaN();
    }
}
