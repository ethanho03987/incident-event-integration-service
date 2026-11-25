package com.ethanho.incidents.messaging;

import com.ethanho.incidents.TestData;
import com.ethanho.incidents.domain.EventStatus;
import com.ethanho.incidents.domain.IncidentEventRepository;
import com.ethanho.incidents.metrics.IncidentMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentEventPublisherTest {

    @Mock RabbitTemplate rabbitTemplate;
    @Mock IncidentEventRepository repository;
    @Mock IncidentMetrics metrics;
    @InjectMocks IncidentEventPublisher publisher;

    @Test
    void sendsMessageWaitsForConfirmThenMarksQueued() {
        RabbitOperations ops = mock(RabbitOperations.class);
        // Run the lambda passed to invoke() against our mock channel operations
        when(rabbitTemplate.invoke(any())).thenAnswer(inv -> {
            RabbitOperations.OperationsCallback<?> callback = inv.getArgument(0);
            return callback.doInRabbit(ops);
        });

        boolean confirmed = publisher.publish(TestData.event("e1"));

        assertThat(confirmed).isTrue();
        verify(ops).convertAndSend(RabbitTopology.EXCHANGE, RabbitTopology.ROUTING_KEY, new IncidentMessage(42L, "e1"));
        verify(ops).waitForConfirmsOrDie(anyLong());
        verify(repository).transitionStatus(42L, EventStatus.RECEIVED, EventStatus.QUEUED);
    }

    @Test
    void leavesEventReceivedWhenBrokerIsDown() {
        when(rabbitTemplate.invoke(any())).thenThrow(new AmqpConnectException(new ConnectException("refused")));

        boolean confirmed = publisher.publish(TestData.event("e1"));

        assertThat(confirmed).isFalse();
        verify(repository, never()).transitionStatus(any(), any(), any());
        verify(metrics).publishFailed();
    }
}
