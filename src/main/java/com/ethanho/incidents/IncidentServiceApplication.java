package com.ethanho.incidents;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point. {@code @SpringBootApplication} turns on component scanning and auto-configuration,
 * so Spring finds every {@code @Component}, {@code @Service}, {@code @RestController} etc. under this package.
 */
@SpringBootApplication
@EnableScheduling            // needed for the @Scheduled republisher job
@ConfigurationPropertiesScan // binds the "app.*" settings in application.yml to AppProperties
public class IncidentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IncidentServiceApplication.class, args);
    }
}
