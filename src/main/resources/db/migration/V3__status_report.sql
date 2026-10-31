-- Generated pain.002 status reports, kept for audit and redelivery (reporting module).
CREATE TABLE status_report
(
    message_id   VARCHAR(35) PRIMARY KEY,
    file_id      UUID                     NOT NULL,
    group_status TEXT                     NOT NULL,
    content      BYTEA                    NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX status_report_file_id_idx ON status_report (file_id);
