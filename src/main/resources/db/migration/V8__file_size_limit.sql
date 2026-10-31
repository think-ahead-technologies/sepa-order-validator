-- Size of every received file; files above the maximum size are recorded with size and hash but without content.
ALTER TABLE payment_file ADD COLUMN size_bytes BIGINT;
UPDATE payment_file SET size_bytes = octet_length(content) WHERE size_bytes IS NULL;
ALTER TABLE payment_file ALTER COLUMN size_bytes SET NOT NULL;
ALTER TABLE payment_file ALTER COLUMN content DROP NOT NULL;
