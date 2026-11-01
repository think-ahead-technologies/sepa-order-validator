// ABOUTME: Entry point of the SEPA Payment Order Validator, a Spring Modulith modular monolith (baseline design A1).
// ABOUTME: Enables scheduling for channel polling and async execution for module event listeners.
package dev.thinkahead.sepa;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class PaymentOrderValidatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentOrderValidatorApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
