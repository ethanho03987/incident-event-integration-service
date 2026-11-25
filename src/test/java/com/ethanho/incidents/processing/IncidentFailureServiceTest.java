package com.ethanho.incidents.processing;

import com.ethanho.incidents.TestData;
import com.ethanho.incidents.domain.EventStatus;
import com.ethanho.incidents.domain.IncidentEvent;
import com.ethanho.incidents.domain.IncidentEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentFailureServiceTest {

    @Mock IncidentEventRepository repository;
    @InjectMocks IncidentFailureService service;

    @Test
    void marksUnprocessedEventAsFailed() {
        IncidentEvent event = TestData.event("e1");
        when(repository.findByEventId("e1")).thenReturn(Optional.of(event));

        service.markFailed("e1", "DispatchUnavailableException: down");

        assertThat(event.getStatus()).isEqualTo(EventStatus.FAILED);
        assertThat(event.getFailureReason()).contains("down");
    }

    @Test
    void neverDowngradesProcessedEvent() {
        IncidentEvent event = TestData.event("e1");
        event.markProcessed("Police", 50, 30, TestData.NOW);
        when(repository.findByEventId("e1")).thenReturn(Optional.of(event));

        service.markFailed("e1", "late failure");

        assertThat(event.getStatus()).isEqualTo(EventStatus.PROCESSED);
    }

    @Test
    void truncatesVeryLongFailureReasons() {
        IncidentEvent event = TestData.event("e1");
        when(repository.findByEventId("e1")).thenReturn(Optional.of(event));

        service.markFailed("e1", "x".repeat(2_000));

        assertThat(event.getFailureReason()).hasSize(500);
    }

    @Test
    void requeueMovesFailedBackToQueued() {
        IncidentEvent event = TestData.event("e1");
        when(repository.findByEventId("e1")).thenReturn(Optional.of(event));
        when(repository.transitionStatus(42L, EventStatus.FAILED, EventStatus.QUEUED)).thenReturn(1);

        assertThat(service.markRequeued("e1")).isTrue();
    }

    @Test
    void requeueOfUnknownEventReturnsFalse() {
        when(repository.findByEventId("nope")).thenReturn(Optional.empty());
        assertThat(service.markRequeued("nope")).isFalse();
    }
}
