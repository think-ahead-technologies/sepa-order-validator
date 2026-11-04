# container-image Specification

## Purpose

Define the container image that is the deployable artifact of the application: what goes into it, how it runs on OpenShift, and when CI publishes it.

## Requirements

### Requirement: Image contents
The image SHALL contain a Java 25 runtime and the application jar produced by the verified Gradle build, and nothing else from the repository: no sources, tests, build caches, git metadata or credentials. Every base image SHALL be referenced by tag and digest, so a moved tag cannot change what is built.

#### Scenario: Base image pinned
- **WHEN** the image definition is read
- **THEN** every base image reference carries both a tag and an `@sha256:` digest

#### Scenario: Only the application jar enters the build context
- **WHEN** the image is built from a working copy that contains sources, build caches and git metadata
- **THEN** the build context contains only the application jar and the image definition

### Requirement: Non-root runtime compatible with OpenShift
The image SHALL run the application as a fixed numeric non-root user by default. It SHALL also start when the container runs under an arbitrary UID with group 0, as OpenShift's restricted security context assigns. The runtime user SHALL be able to read the application files but not modify them.

#### Scenario: Default user
- **WHEN** the image is started without a user override
- **THEN** the application process runs with a non-zero UID

#### Scenario: Arbitrary UID with group 0
- **WHEN** the image is started with an arbitrary UID such as `1000690000` and group `0`
- **THEN** the application starts and reports readiness

#### Scenario: Application files are read-only
- **WHEN** the runtime user tries to modify the application files in the image
- **THEN** the write is denied

### Requirement: Runtime configuration and probes
The image SHALL start the application, listen for HTTP on port 8080 and serve the liveness and readiness probes and the Prometheus endpoint there. Database, SFTP connection and SFTP known_hosts SHALL be supplied at run time through environment variables and mounted files; the image SHALL contain no defaults for them. Logs SHALL go to standard output as ECS JSON, as specified in `observability`.

#### Scenario: File processed by the containerised application
- **WHEN** the image runs with environment variables pointing to PostgreSQL and an SFTP server, a mounted known_hosts file and an arbitrary UID with group 0, and a customer drops a valid pain.001 file in the inbound directory
- **THEN** the customer receives a pain.002 status report with group status `ACCP` in the outbound directory

#### Scenario: Probes on port 8080
- **WHEN** the containerised application has started against reachable PostgreSQL and SFTP
- **THEN** `/actuator/health/liveness` and `/actuator/health/readiness` on port 8080 answer with status `UP`

#### Scenario: Logs are ECS JSON on standard output
- **WHEN** the containerised application starts and processes a file
- **THEN** every log line it writes to standard output is an ECS JSON object

### Requirement: Image publication
GitHub Actions SHALL build and scan the image on every run, as specified in `supply-chain` (vulnerability gate). It SHALL publish the image to the GitHub container registry only for pushes to `main` after all tests and the vulnerability scan of that image have passed, tagged with the full commit SHA, and SHALL record the published image digest in the run summary. Pull requests and other branches SHALL NOT publish an image. GitLab CI SHALL NOT build, scan or publish the image.

#### Scenario: Push to main
- **WHEN** a commit is pushed to `main` and the build job, the full test job and the vulnerability scan pass
- **THEN** the image is pushed to `ghcr.io` tagged with the commit SHA, and the run summary shows its `sha256` digest

#### Scenario: Scan fails on main
- **WHEN** a commit is pushed to `main` and the vulnerability scan of its image fails
- **THEN** no image is published for that commit

#### Scenario: Pull request
- **WHEN** CI runs for a pull request
- **THEN** the image is built but not pushed to any registry

#### Scenario: Tests fail on main
- **WHEN** the full test job fails for a push to `main`
- **THEN** no image is published for that commit
