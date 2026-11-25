package com.ethanho.incidents.processing;

import org.junit.jupiter.api.Test;

import static com.ethanho.incidents.TestData.event;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulatedDispatchGatewayTest {

    private final TriageDecision decision = new TriageDecision("Fire and Rescue", 70, 15);

    @Test
    void succeedsWhenRandomValueIsAboveFailureRate() {
        SimulatedDispatchGateway gateway = new SimulatedDispatchGateway(0.2, () -> 0.5);
        assertThatCode(() -> gateway.dispatch(event("e1"), decision)).doesNotThrowAnyException();
    }

    @Test
    void failsWhenRandomValueIsBelowFailureRate() {
        SimulatedDispatchGateway gateway = new SimulatedDispatchGateway(0.2, () -> 0.1);
        assertThatThrownBy(() -> gateway.dispatch(event("e1"), decision))
                .isInstanceOf(DispatchUnavailableException.class)
                .hasMessageContaining("e1");
    }

    @Test
    void zeroFailureRateNeverFails() {
        SimulatedDispatchGateway gateway = new SimulatedDispatchGateway(0.0, () -> 0.0);
        assertThatCode(() -> gateway.dispatch(event("e1"), decision)).doesNotThrowAnyException();
    }
}
