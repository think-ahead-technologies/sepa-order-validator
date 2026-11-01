## Purpose

Receive payment order files from the customer SFTP server, exactly once across all running instances, store them with their size and SHA-256 hash, and alert when a customer gets no reply in time.

## ADDED Requirements

### Requirement: Verified and authenticated SFTP connection
The system SHALL connect to the customer SFTP server only after verifying the server's host key against the configured known_hosts file (`sepa.sftp.known-hosts`), and there SHALL be no setting that skips this check. The system SHALL authenticate with exactly one of a configured password (`sepa.sftp.password`) or a configured private key (`sepa.sftp.private-key`, optional `sepa.sftp.private-key-passphrase`), and SHALL NOT use any other keys available on the host. Connecting, logging in and waiting on an unresponsive server SHALL be bounded by `sepa.sftp.timeout` (default 30 seconds). The system SHALL reuse its SSH connections across polls instead of opening one per file operation. Startup SHALL fail when the host, username, known_hosts file or staging directory is missing, when the port is outside 1 to 65535, when the poll interval or timeout is not positive, or when both or neither of password and private key are configured.

#### Scenario: Login with an authorized private key
- **WHEN** the system is configured with a private key the SFTP account authorizes
- **THEN** it logs in and processes files over that connection

#### Scenario: Login with an unauthorized private key is refused
- **WHEN** the system is configured with a private key the SFTP account does not authorize
- **THEN** the login fails and no password or other key is tried

#### Scenario: Missing known_hosts stops startup
- **WHEN** `sepa.sftp.known-hosts` is not configured
- **THEN** the application refuses to start

#### Scenario: Password and private key together stop startup
- **WHEN** both `sepa.sftp.password` and `sepa.sftp.private-key` are configured
- **THEN** the application refuses to start

#### Scenario: Connections are reused across polls
- **WHEN** several polls run in a row
- **THEN** they share the open SSH connection instead of logging in again for each poll

### Requirement: Only finished XML uploads are taken in
The system SHALL poll the inbound directory (`sepa.sftp.inbound-directory`) every `sepa.sftp.poll-interval` (default 5 seconds, measured from the end of one poll to the start of the next). Only regular files whose name ends in `.xml` (case-insensitive) SHALL be considered, so customers can upload under a temporary name and rename. A file SHALL be taken in only once two consecutive polls see it with the same size and modification time.

#### Scenario: Non-XML files are left alone
- **WHEN** a customer uploads a file whose name does not end in `.xml`
- **THEN** the file stays in the inbound directory and is not received

#### Scenario: A file still being uploaded is not taken in
- **WHEN** an `.xml` file grows or changes its modification time between two polls
- **THEN** it is not taken in until a later poll sees it unchanged

#### Scenario: A slowly uploaded file is validated only once complete
- **WHEN** a customer writes a file slowly under its final `.xml` name
- **THEN** no status report is produced while the upload runs, and the complete file is later accepted

### Requirement: Each file is claimed exactly once
The system SHALL claim a finished upload by atomically renaming it into the processing directory (`sepa.sftp.processing-directory`) under a name that carries a newly assigned file id: `<file id>-<original name>` for the regular inbox and `<file id>.instant-<original name>` for the SEPA Instant inbox. When several claims race for the same file, exactly one SHALL succeed. After a claimed file is stored, the system SHALL move it to the processed directory (`sepa.sftp.processed-directory`).

#### Scenario: Only one of two competing claims succeeds
- **WHEN** two claims are made for the same inbound file
- **THEN** the first succeeds, the second reports that the file is gone, and the processing directory holds the file once

#### Scenario: A processed file leaves the inbound directory
- **WHEN** a finished upload has been received
- **THEN** it is no longer in the inbound directory and is in the processed directory under its claimed name

### Requirement: Intake survives a crash at any point
The system SHALL finish every claimed file found in the processing directory on the next poll, whichever instance claimed it, so that a crash between claiming, storing and archiving neither loses nor duplicates a file. Storing SHALL be idempotent per file id: a file id that is already stored SHALL NOT be stored, scheduled, audited or announced again. Files in the processing directory whose name does not carry a file id SHALL be ignored with a warning.

#### Scenario: Claimed but not yet stored
- **WHEN** an instance crashed after claiming a file and before storing it
- **THEN** the next poll stores the file once under the id from its claimed name and original file name, announces it once, and archives it

#### Scenario: Stored but not yet archived
- **WHEN** an instance crashed after storing a claimed file and before archiving it
- **THEN** the next poll archives the file without storing or announcing it again

#### Scenario: Receiving the same claim twice
- **WHEN** the same claimed file is received twice
- **THEN** it is stored and announced only once

#### Scenario: A file left in processing is reported exactly once
- **WHEN** a valid file is found in the processing directory under a claimed name
- **THEN** the customer receives exactly one status report for it

### Requirement: Files are handled in isolation
A file that cannot be claimed or taken in SHALL be logged at ERROR and retried on the next poll, and SHALL NOT block the files behind it.

#### Scenario: An unreadable file does not block others
- **WHEN** one claimed file fails to be taken in
- **THEN** the other files of the same poll are received and archived, and the failing file is retried on the next poll

### Requirement: Received files are stored with size, hash and submitter
For every received file the system SHALL store the original file name, the channel (`SFTP`), the submitter, the size in bytes, the SHA-256 hash and the raw content, with status `RECEIVED`, and SHALL schedule its validation and write a `PaymentFileReceived` audit entry in the same transaction. The submitter SHALL be the authenticated channel identity `sftp:<account>` and SHALL never be taken from the file content.

#### Scenario: A received file is stored and announced
- **WHEN** a customer uploads a valid `.xml` file to the inbound directory
- **THEN** the file is stored with its content, size and SHA-256 hash, the submitter is `sftp:<account>`, its validation is scheduled and a `PaymentFileReceived` audit entry exists

### Requirement: File size limit
The system SHALL NOT store the content of a file larger than `sepa.intake.max-file-size` (default 100 MB). Such a file SHALL be streamed through SHA-256 without being held in memory, stored with its size and hash only, and never parsed or validated. It SHALL be rejected as a whole with a single violation `FF01` at location `Document` stating that it exceeds the maximum size accepted for processing.

#### Scenario: A file above the maximum size
- **WHEN** a customer uploads a file larger than `sepa.intake.max-file-size`
- **THEN** only its size and SHA-256 hash are stored, the file is rejected with exactly one violation `FF01` at `Document` mentioning the maximum size, and the customer receives a status report with the single reason code `FF01`

### Requirement: One intake leader across instances
Only one running instance at a time SHALL poll the customer SFTP server and check for overdue replies. The leader SHALL be elected through a lock in the shared PostgreSQL database that expires after `sepa.leader-election.lock-time-to-live` (default 10 seconds) unless renewed every `sepa.leader-election.heartbeat` (default 1 second). Startup SHALL fail when either duration is missing or not positive, or when the heartbeat exceeds half the time-to-live. When an instance loses leadership it SHALL discard what it observed about uploads in progress.

#### Scenario: Two instances process every file exactly once
- **WHEN** two instances run and ten files are uploaded
- **THEN** every file is stored once and gets exactly one status report

#### Scenario: Follower takes over when the leader shuts down
- **WHEN** the leader instance shuts down
- **THEN** the other instance becomes leader and takes in newly uploaded files

#### Scenario: Takeover after a crashed leader's lock expires
- **WHEN** a crashed instance still holds an unexpired leader lock
- **THEN** no instance polls until the lock expires, after which another instance becomes leader and takes in waiting files

#### Scenario: Heartbeat above half the time-to-live stops startup
- **WHEN** `sepa.leader-election.heartbeat` is more than half of `sepa.leader-election.lock-time-to-live`
- **THEN** the application refuses to start

### Requirement: SEPA Instant inbox
The system SHALL support an optional second inbox for SEPA Instant orders, `sepa.sftp.instant-inbound-directory`; when blank, the inbox SHALL be disabled. Files from this inbox SHALL be taken in under the same rules as the regular inbox, claimed before the regular inbox in each poll, marked as SEPA Instant orders, and their processing SHALL be scheduled with high priority. The mark SHALL survive a crash between claim and storage.

#### Scenario: A file from the SEPA Instant inbox
- **WHEN** a customer uploads a finished `.xml` file to the SEPA Instant inbox
- **THEN** the file is stored as a SEPA Instant order, its validation is scheduled with high priority, and it is archived under `<file id>.instant-<original name>`

#### Scenario: The inbox is optional
- **WHEN** `sepa.sftp.instant-inbound-directory` is blank
- **THEN** the application starts and polls only the regular inbox

### Requirement: Overdue-reply alert
The system SHALL treat a stored file as overdue when no status report has been delivered to the customer for it within `sepa.intake.reply-overdue-after` (default 10 minutes) of its receipt, whatever the cause. The leader SHALL check every `sepa.intake.reply-check-interval` (default 1 minute) and log an ERROR listing the ids of up to 100 overdue files. The same definition of overdue drives the unanswered-files metric of the observability capability. A container that was split into messages SHALL NOT count as overdue; its messages are tracked instead. Delivering a status report SHALL end the alert for that file.

#### Scenario: A file without reply after the threshold
- **WHEN** a file was received longer ago than `sepa.intake.reply-overdue-after` and no status report was delivered
- **THEN** the check logs an ERROR naming its file id

#### Scenario: The alert ends with the reply
- **WHEN** a status report is delivered for an overdue file
- **THEN** the file no longer appears in the check

#### Scenario: A split container is not overdue
- **WHEN** a container was split into messages longer ago than the threshold
- **THEN** the container does not appear in the overdue check
