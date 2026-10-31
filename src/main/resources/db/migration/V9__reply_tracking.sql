-- When the customer was last sent a status report for the file; files without a reply in time raise an alert.
ALTER TABLE payment_file ADD COLUMN replied_at TIMESTAMP WITH TIME ZONE;
UPDATE payment_file f SET replied_at = r.delivered_at FROM status_report r WHERE r.file_id = f.id;

CREATE INDEX payment_file_unanswered_idx ON payment_file (received_at) WHERE replied_at IS NULL;
