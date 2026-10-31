// ABOUTME: Starts the application locally with PostgreSQL and SFTP from Testcontainers (./gradlew bootTestRun).
// ABOUTME: Drop pain.001 files into the container's inbound directory to watch the full pipeline run.
package dev.thinkahead.sepa;

import org.springframework.boot.SpringApplication;

public class TestPaymentOrderValidatorApplication {

    public static void main(String[] args) {
        SpringApplication.from(PaymentOrderValidatorApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
