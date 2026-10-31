-- One status report per payment file, so a redelivered validation outcome never creates a second report.
DROP INDEX status_report_file_id_idx;
CREATE UNIQUE INDEX status_report_file_id_uq ON status_report (file_id);

-- When the report was last delivered to the customer; null until the first successful delivery.
ALTER TABLE status_report ADD COLUMN delivered_at TIMESTAMP WITH TIME ZONE;
