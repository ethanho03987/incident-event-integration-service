package com.ethanho.incidents.processing;

import com.ethanho.incidents.config.AppProperties;
import com.ethanho.incidents.domain.IncidentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * Stand-in for a real dispatch system. Set {@code app.simulation.dispatch-failure-rate} (0.0 - 1.0)
 * to make a share of calls fail, which is how you demo retries and the dead-letter queue.
 */
@Component
public class SimulatedDispatchGateway implements DispatchGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedDispatchGateway.class);

    private final double failureRate;
    private final DoubleSupplier random;

    @Autowired // tells Spring which constructor to use when there is more than one
    public SimulatedDispatchGateway(AppProperties properties) {
        this(properties.simulation().dispatchFailureRate(), () -> ThreadLocalRandom.current().nextDouble());
    }

    /** Lets tests control the "random" number. */
    SimulatedDispatchGateway(double failureRate, DoubleSupplier random) {
        this.failureRate = failureRate;
        this.random = random;
    }

    @Override
    public void dispatch(IncidentEvent event, TriageDecision decision) {
        if (random.getAsDouble() < failureRate) {
            throw new DispatchUnavailableException("Simulated dispatch outage for eventId=" + event.getEventId());
        }
        log.debug("Dispatched {} to eventId={} (priority {})",
                decision.assignedService(), event.getEventId(), decision.priorityScore());
    }
}
