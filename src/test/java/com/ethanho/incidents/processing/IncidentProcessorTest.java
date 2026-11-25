package com.ethanho.incidents.processing;

import com.ethanho.incidents.TestData;
import com.ethanho.incidents.domain.EventStatus;
import com.ethanho.incidents.domain.IncidentEvent;
import com.ethanho.incidents.domain.IncidentEventRepository;
import com.ethanho.incidents.messaging.IncidentMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit test: the repository and dispatch gateway are Mockito mocks, so no database or broker is needed. */
@ExtendWith(MockitoExtension.class)
class IncidentProcessorTest {

    @Mock IncidentEventRepository repository;
    @Mock DispatchGateway dispatchGateway;

    private IncidentProcessor processor;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(TestData.NOW, ZoneOffset.UTC);
        processor = new IncidentProcessor(repository, new TriageService(), dispatchGateway, fixed);
    }

    @Test
    void processesNewEventAndRecordsTriageResult() {
        IncidentEvent event = TestData.event("e1");
        when(repository.findByEventId("e1")).thenReturn(Optional.of(event));

        ProcessingResult result = processor.process(new IncidentMessage(42L, "e1"));

        assertThat(result.outcome()).isEqualTo(ProcessingResult.Outcome.PROCESSED);
        assertThat(result.endToEnd()).isEqualTo(Duration.ofSeconds(5)); // received 5s before the fixed clock
        assertThat(event.getStatus()).isEqualTo(EventStatus.PROCESSED);
        assertThat(event.getAssignedService()).isEqualTo("Fire and Rescue");
        assertThat(event.getPriorityScore()).isEqualTo(70);
        assertThat(event.getProcessedAt()).isEqualTo(TestData.NOW);
        verify(dispatchGateway).dispatch(any(), any());
    }

    @Test
    void skipsEventThatWasAlreadyProcessed() {
        IncidentEvent event = TestData.event("e1");
        event.markProcessed("Fire and Rescue", 70, 15, TestData.NOW);
        when(repository.findByEventId("e1")).thenReturn(Optional.of(event));

        ProcessingResult result = processor.process(new IncidentMessage(42L, "e1"));

        assertThat(result.outcome()).isEqualTo(ProcessingResult.Outcome.ALREADY_PROCESSED);
        verify(dispatchGateway, never()).dispatch(any(), any());
    }

    @Test
    void propagatesDispatchFailureSoTheMessageIsRetried() {
        IncidentEvent event = TestData.event("e1");
        when(repository.findByEventId("e1")).thenReturn(Optional.of(event));
        doThrow(new DispatchUnavailableException("down")).when(dispatchGateway).dispatch(any(), any());

        assertThatThrownBy(() -> processor.process(new IncidentMessage(42L, "e1")))
                .isInstanceOf(DispatchUnavailableException.class);
        assertThat(event.getStatus()).isNotEqualTo(EventStatus.PROCESSED);
    }

    @Test
    void failsForUnknownEvent() {
        when(repository.findByEventId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> processor.process(new IncidentMessage(1L, "missing")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing");
    }
}
