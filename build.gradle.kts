// ABOUTME: Gradle build for the SEPA Payment Order Validator (Spring Boot 4, Spring Modulith, Java 25).
// ABOUTME: Dependency versions come from the Spring Boot and Spring Modulith BOMs via the version catalog.

plugins {
    java
    alias(libs.plugins.spring.boot)
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
    implementation(libs.spring.integration.sftp)
    implementation(libs.spring.modulith.starter.core)
    implementation(libs.spring.modulith.starter.jdbc)
    implementation(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(platform(libs.spring.modulith.bom))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.awaitility)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// No mocks in this project: keeping Mockito off the test classpath enforces it.
configurations.testImplementation {
    exclude(group = "org.mockito")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
