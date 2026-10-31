-- Files from the SEPA Instant inbox (like DK order type CIP) are marked, and their work items are claimed first.
ALTER TABLE payment_file ADD COLUMN instant_order BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE work_item ADD COLUMN priority INTEGER NOT NULL DEFAULT 0;
DROP INDEX work_item_due_idx;
CREATE INDEX work_item_due_idx ON work_item (step, priority DESC, next_attempt_at) WHERE state = 'PENDING';
