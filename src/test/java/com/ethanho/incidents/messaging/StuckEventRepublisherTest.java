package com.ethanho.incidents.messaging;

import com.ethanho.incidents.TestData;
import com.ethanho.incidents.config.AppProperties;
import com.ethanho.incidents.domain.EventStatus;
import com.ethanho.incidents.domain.IncidentEvent;
import com.ethanho.incidents.domain.IncidentEventRepository;
import com.ethanho.incidents.metrics.IncidentMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StuckEventRepublisherTest {

    @Mock IncidentEventRepository repository;
    @Mock IncidentEventPublisher publisher;
    @Mock IncidentMetrics metrics;

    private StuckEventRepublisher republisher;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                new AppProperties.Webhook("s"),
                new AppProperties.Republisher(Duration.ofSeconds(30), 50),
                new AppProperties.Simulation(0.0));
        republisher = new StuckEventRepublisher(repository, publisher, metrics, props, Clock.fixed(TestData.NOW, ZoneOffset.UTC));
    }

    @Test
    void republishesEventsStuckInReceived() {
        IncidentEvent a = TestData.event("a");
        IncidentEvent b = TestData.event("b");
        when(repository.findByStatusAndReceivedAtBeforeOrderByReceivedAtAsc(
                eq(EventStatus.RECEIVED), eq(TestData.NOW.minusSeconds(30)), any(Pageable.class)))
                .thenReturn(List.of(a, b));
        when(publisher.publish(a)).thenReturn(true);
        when(publisher.publish(b)).thenReturn(false); // broker still down for this one

        int sent = republisher.republishStuckEvents();

        assertThat(sent).isEqualTo(1);
        verify(metrics).republished(1);
    }

    @Test
    void doesNothingWhenNothingIsStuck() {
        when(repository.findByStatusAndReceivedAtBeforeOrderByReceivedAtAsc(any(), any(), any())).thenReturn(List.of());

        assertThat(republisher.republishStuckEvents()).isZero();
        verifyNoInteractions(publisher);
        verify(metrics, never()).republished(org.mockito.ArgumentMatchers.anyInt());
    }
}
