// ABOUTME: Wires the log context into async execution: the MDC is cleared after every task of the task executor.
// ABOUTME: JSON log format and IBAN masking are configured in application.yaml (logging.structured.*), see ADR-0018.
package dev.thinkahead.sepa;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;

@Configuration(proxyBeanMethods = false)
class LoggingConfiguration {

    @Bean
    TaskDecorator logContextClearingTaskDecorator() {
        return new LogContext.ClearingTaskDecorator();
    }
}
