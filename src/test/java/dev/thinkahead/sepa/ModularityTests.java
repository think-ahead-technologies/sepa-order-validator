// ABOUTME: Verifies the Spring Modulith module structure: no cycles, no access to other modules' internals.
// ABOUTME: Fails the build when a module boundary from ADR-0002 is violated.
package dev.thinkahead.sepa;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(PaymentOrderValidatorApplication.class);

    @Test
    void verifiesModuleBoundaries() {
        modules.verify();
    }

    @Test
    void containsTheWalkingSkeletonModules() {
        assertThat(modules.stream().map(ApplicationModule::getIdentifier).map(Object::toString))
                .containsExactlyInAnyOrder("intake", "validation", "reporting", "audit", "processing");
    }
}
