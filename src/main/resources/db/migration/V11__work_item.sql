-- Work queue of processing steps per payment file (processing module, ADR-0017).
CREATE TABLE work_item
(
    file_id         UUID                     NOT NULL,
    step            TEXT                     NOT NULL,
    state           TEXT                     NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING', 'DONE', 'FAILED')),
    attempts        INT                      NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    lease_token     UUID,
    locked_until    TIMESTAMP WITH TIME ZONE,
    last_error      TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    completed_at    TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (file_id, step)
);

CREATE INDEX work_item_due_idx ON work_item (step, next_attempt_at) WHERE state = 'PENDING';

-- Delivery moves from the event chain to the work queue: reports not yet delivered become DELIVER items, and the
-- event publications of the removed delivery listener are dropped so they are never resubmitted.
INSERT INTO work_item (file_id, step)
SELECT file_id, 'DELIVER' FROM status_report WHERE delivered_at IS NULL
ON CONFLICT DO NOTHING;

DELETE FROM event_publication
WHERE completion_date IS NULL AND listener_id LIKE '%StatusReportDeliveryListener%';
