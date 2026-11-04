-- Payment information ids (PmtInfId) of accepted files per submitter; the primary key makes cross-file duplicate
-- detection (DU02) race-free (validation module). Kept without expiry, like accepted_message (roadmap Q12).
CREATE TABLE accepted_payment_information
(
    submitter              TEXT                     NOT NULL,
    payment_information_id VARCHAR(35)              NOT NULL,
    file_id                UUID                     NOT NULL,
    accepted_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    PRIMARY KEY (submitter, payment_information_id)
);

-- Lets a file whose ids conflict remove its own registrations within the acceptance transaction.
CREATE INDEX accepted_payment_information_file_idx ON accepted_payment_information (file_id);
CREATE INDEX accepted_message_file_idx ON accepted_message (file_id);
