-- Received payment files with raw content, SHA-256 hash and lifecycle status (intake module).
CREATE TABLE payment_file
(
    id                UUID PRIMARY KEY,
    channel           TEXT                     NOT NULL,
    file_name         TEXT                     NOT NULL,
    sha256            CHAR(64)                 NOT NULL,
    content           BYTEA                    NOT NULL,
    status            TEXT                     NOT NULL,
    received_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    status_changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX payment_file_sha256_idx ON payment_file (sha256);
CREATE INDEX payment_file_status_idx ON payment_file (status);
