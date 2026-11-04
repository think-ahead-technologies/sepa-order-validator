// ABOUTME: Gradle build for the SEPA Payment Order Validator (Spring Boot 4, Spring Modulith, Java 25).
// ABOUTME: Dependency versions come from the Spring Boot and Spring Modulith BOMs via the version catalog.

plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spotless)
    // Spring Boot embeds the CycloneDX SBOM in bootJar and serves it at /actuator/sbom/application.
    alias(libs.plugins.cyclonedx)
}

group = "dev.thinkahead"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.modulith.bom))

    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.jackson)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.opentelemetry)
    implementation(libs.opentelemetry.logback.appender)
    implementation(libs.spring.integration.sftp)
    implementation(libs.spring.integration.jdbc)
    implementation(libs.spring.modulith.starter.core)
    implementation(libs.spring.modulith.starter.jdbc)
    implementation(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.micrometer.registry.prometheus)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(platform(libs.spring.modulith.bom))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.grafana)
    testImplementation(libs.micrometer.observation.test)
    testImplementation(libs.opentelemetry.sdk.testing)
    testImplementation(libs.awaitility)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Formatting: palantir-java-format (4 spaces, 120 columns). spotlessCheck runs with check, so build fails on
// unformatted code; spotlessApply fixes it.
spotless {
    java {
        palantirJavaFormat(libs.versions.palantir.java.format.get())
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// No mocks in this project: keeping Mockito off the test classpath enforces it.
configurations.testImplementation {
    exclude(group = "org.mockito")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// The SBOM embedded in the jar lists what ships: the runtime classpath, not test or build tooling dependencies.
tasks.cyclonedxDirectBom {
    includeConfigs = listOf("runtimeClasspath")
}

// The container image (Dockerfile) packages this jar under a fixed name.
tasks.bootJar {
    archiveFileName = "payment-order-validator.jar"
}

// `test`, and with it `build`, runs the unit tests only: no Spring context, PostgreSQL or SFTP container.
tasks.test {
    filter {
        excludeTestsMatching("*IntegrationTest")
        excludeTestsMatching("*EndToEndTest")
    }
}

// The full suite, including integration and end-to-end tests against PostgreSQL and SFTP containers.
// Meant for CI; locally only when a change can affect integration or end-to-end behaviour (see CLAUDE.md).
tasks.register<Test>("fullTest") {
    description = "Runs all tests, including integration and end-to-end tests with Testcontainers."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
}
