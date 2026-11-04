# ABOUTME: Container image of the SEPA Payment Order Validator: the verified Spring Boot jar on a pinned Temurin JRE.
# ABOUTME: Build the jar first: mise exec -- ./gradlew bootJar && docker build -t payment-order-validator .
#
# Runs as non-root user 10001 in group 0 and also under OpenShift's arbitrary UID; the application files are owned by
# root and read-only for the runtime user. Configuration (database, SFTP, known_hosts) comes from the environment.
# Bump the base image digest together with the JDK in mise.toml and .gitlab-ci.yml.

FROM eclipse-temurin:25.0.5_8-jre-noble@sha256:a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce AS extract
WORKDIR /build
COPY build/libs/payment-order-validator.jar payment-order-validator.jar
RUN java -Djarmode=tools -jar payment-order-validator.jar extract --layers --destination extracted

FROM eclipse-temurin:25.0.5_8-jre-noble@sha256:a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce
WORKDIR /app
# Least to most frequently changing, so a code-only change rebuilds and pushes only the last layer.
COPY --from=extract /build/extracted/dependencies/ ./
COPY --from=extract /build/extracted/spring-boot-loader/ ./
COPY --from=extract /build/extracted/snapshot-dependencies/ ./
COPY --from=extract /build/extracted/application/ ./
USER 10001:0
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "payment-order-validator.jar"]
