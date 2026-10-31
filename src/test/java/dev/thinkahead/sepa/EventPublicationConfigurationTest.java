// ABOUTME: Unit tests for the shipped event publication settings in application.yaml.
// ABOUTME: Events only notify; nothing is republished on restart and completed publications are not kept.
package dev.thinkahead.sepa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class EventPublicationConfigurationTest {

    private final Properties application = load();

    @Test
    void doesNotRepublishOutstandingEventsOnRestart() {
        assertThat(application.getProperty("spring.modulith.events.republish-outstanding-events-on-restart"))
                .isEqualTo("false");
    }

    @Test
    void deletesCompletedPublications() {
        assertThat(application.getProperty("spring.modulith.events.completion-mode")).isEqualTo("delete");
    }

    private static Properties load() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        return yaml.getObject();
    }
}
