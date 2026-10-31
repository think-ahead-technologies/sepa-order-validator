-- Submitter identity of each received file: the authenticated channel identity, e.g. "sftp:<account>" (intake module).
ALTER TABLE payment_file ADD COLUMN submitter TEXT;
UPDATE payment_file SET submitter = 'unknown' WHERE submitter IS NULL;
ALTER TABLE payment_file ALTER COLUMN submitter SET NOT NULL;

-- Message ids of accepted files; the primary key makes duplicate detection race-free (validation module).
CREATE TABLE accepted_message
(
    submitter   TEXT                     NOT NULL,
    message_id  VARCHAR(35)              NOT NULL,
    file_id     UUID                     NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    PRIMARY KEY (submitter, message_id)
);
