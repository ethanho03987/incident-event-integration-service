package com.ethanho.incidents.ingestion;

import com.ethanho.incidents.TestData;
import com.ethanho.incidents.domain.IncidentEvent;
import com.ethanho.incidents.domain.IncidentEventRepository;
import com.ethanho.incidents.domain.IngestChannel;
import com.ethanho.incidents.messaging.IncidentEventPublisher;
import com.ethanho.incidents.metrics.IncidentMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentIngestionServiceTest {

    @Mock IncidentEventRepository repository;
    @Mock IncidentEventPublisher publisher;
    @Mock IncidentMetrics metrics;

    private IncidentIngestionService service;

    @BeforeEach
    void setUp() {
        service = new IncidentIngestionService(repository, publisher, metrics, Clock.fixed(TestData.NOW, ZoneOffset.UTC));
    }

    @Test
    void savesAndPublishesNewEvent() {
        when(repository.findByEventId("e1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(IncidentEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        IngestionResult result = service.ingest(TestData.request("e1"), IngestChannel.REST);

        assertThat(result.duplicate()).isFalse();
        ArgumentCaptor<IncidentEvent> saved = ArgumentCaptor.forClass(IncidentEvent.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEventId()).isEqualTo("e1");
        assertThat(saved.getValue().getChannel()).isEqualTo(IngestChannel.REST);
        assertThat(saved.getValue().getReceivedAt()).isEqualTo(TestData.NOW);
        verify(publisher).publish(saved.getValue());
        verify(metrics).received(IngestChannel.REST);
    }

    @Test
    void returnsExistingEventForDuplicateWithoutPublishing() {
        IncidentEvent existing = TestData.event("e1");
        when(repository.findByEventId("e1")).thenReturn(Optional.of(existing));

        IngestionResult result = service.ingest(TestData.request("e1"), IngestChannel.WEBHOOK);

        assertThat(result.duplicate()).isTrue();
        assertThat(result.event()).isSameAs(existing);
        verify(repository, never()).saveAndFlush(any());
        verify(publisher, never()).publish(any());
        verify(metrics).duplicateRejected(IngestChannel.WEBHOOK);
    }

    @Test
    void treatsUniqueConstraintRaceAsDuplicate() {
        IncidentEvent winner = TestData.event("e1");
        when(repository.findByEventId("e1"))
                .thenReturn(Optional.empty())       // first check: not there yet
                .thenReturn(Optional.of(winner));   // after the failed insert: the other request won
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        IngestionResult result = service.ingest(TestData.request("e1"), IngestChannel.REST);

        assertThat(result.duplicate()).isTrue();
        assertThat(result.event()).isSameAs(winner);
        verify(publisher, never()).publish(any());
    }

    @Test
    void rethrowsConstraintErrorThatIsNotADuplicate() {
        when(repository.findByEventId("e1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("Column too long"));

        assertThatThrownBy(() -> service.ingest(TestData.request("e1"), IngestChannel.REST))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
