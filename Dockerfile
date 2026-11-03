# ABOUTME: Container image of the validator: builds the Spring Boot jar with the pinned JDK, runs it on the matching JRE.
# ABOUTME: Runs as UID 1001 in group 0 with group-readable files, so OpenShift can assign any UID from its namespace range.
FROM eclipse-temurin:25.0.5_8-jdk-noble@sha256:95e350ad1eb738f943e141640f7e3d797e483359a3b33c3864255bfd1ad71f8a AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY src/main src/main
# Tests run in CI (build and fullTest); the image build only packages the jar.
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar \
    && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /app.jar \;

FROM eclipse-temurin:25.0.5_8-jre-noble@sha256:a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce
WORKDIR /app
COPY --from=build --chown=1001:0 --chmod=0440 /app.jar app.jar
USER 1001:0
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
