// ABOUTME: Unit tests for the shipped event recovery settings in application.yaml.
// ABOUTME: Stuck listener runs are recovered by staleness, never by republishing everything on restart.
package dev.thinkahead.sepa;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.io.ClassPathResource;

class EventRecoveryConfigurationTest {

    private final Properties application = load();

    @Test
    void doesNotRepublishOutstandingEventsOnRestart() {
        assertThat(application.getProperty("spring.modulith.events.republish-outstanding-events-on-restart"))
                .isEqualTo("false");
    }

    @Test
    void marksListenerRunsStuckInProcessingAsFailedSoTheyAreResubmitted() {
        Duration staleAfter = duration("spring.modulith.events.staleness.processing");
        Duration overdueAfter = duration("sepa.intake.reply-overdue-after");

        assertThat(staleAfter).isPositive();
        assertThat(staleAfter).as("recovered before the customer reply is reported overdue")
                .isLessThan(overdueAfter);
    }

    private Duration duration(String name) {
        String value = application.getProperty(name);
        assertThat(value).as(name).isNotNull();
        return DurationStyle.detectAndParse(value);
    }

    private static Properties load() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        return yaml.getObject();
    }
}
