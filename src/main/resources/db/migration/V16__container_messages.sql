-- Messages split from a DK XML container are payment files of their own, linked to the container they came from.
ALTER TABLE payment_file ADD COLUMN container_file_id UUID REFERENCES payment_file (id);
ALTER TABLE payment_file ADD COLUMN container_index INTEGER;
ALTER TABLE payment_file ADD CONSTRAINT payment_file_container_message_uq UNIQUE (container_file_id, container_index);
