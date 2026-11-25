package com.ethanho.incidents.messaging;

import com.ethanho.incidents.metrics.IncidentMetrics;
import com.ethanho.incidents.processing.DispatchUnavailableException;
import com.ethanho.incidents.processing.IncidentFailureService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FailedIncidentRecovererTest {

    @Mock AmqpTemplate amqpTemplate;
    @Mock IncidentFailureService failureService;
    @Mock IncidentMetrics metrics;

    private FailedIncidentRecoverer recoverer;

    @BeforeEach
    void setUp() {
        recoverer = new FailedIncidentRecoverer(amqpTemplate, failureService, metrics, new ObjectMapper());
    }

    @Test
    void sendsToDeadLetterExchangeAndMarksEventFailed() {
        Message message = message("{\"incidentId\":42,\"eventId\":\"e1\"}");
        Throwable cause = new ListenerExecutionFailedException("Listener threw", new DispatchUnavailableException("CAD down"));

        recoverer.recover(message, cause);

        verify(amqpTemplate).send(eq(RabbitTopology.DEAD_LETTER_EXCHANGE), eq(RabbitTopology.DEAD_LETTER_ROUTING_KEY), any(Message.class));
        verify(failureService).markFailed("e1", "DispatchUnavailableException: CAD down");
        verify(metrics).deadLettered();
    }

    @Test
    void stillDeadLettersMessageWithUnreadableBody() {
        recoverer.recover(message("not json"), new RuntimeException("boom"));

        verify(amqpTemplate).send(eq(RabbitTopology.DEAD_LETTER_EXCHANGE), eq(RabbitTopology.DEAD_LETTER_ROUTING_KEY), any(Message.class));
        verify(failureService, never()).markFailed(anyString(), anyString());
    }

    @Test
    void rootCauseMessageUnwrapsNestedExceptions() {
        Throwable nested = new RuntimeException("outer", new IllegalStateException("inner"));
        assertThat(FailedIncidentRecoverer.rootCauseMessage(nested)).isEqualTo("IllegalStateException: inner");
    }

    private static Message message(String body) {
        return new Message(body.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }
}
