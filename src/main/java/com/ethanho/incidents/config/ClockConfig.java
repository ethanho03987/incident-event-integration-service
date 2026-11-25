package com.ethanho.incidents.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Code asks for the current time through an injected {@link Clock} instead of calling {@code Instant.now()}.
 * Tests can then pass a fixed clock and get repeatable results.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
