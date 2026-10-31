-- Outcome of validating a payment file, read by the REPORT step (validation module, ADR-0017).
CREATE TABLE validation_result
(
    file_id                  UUID PRIMARY KEY,
    original_message_id      TEXT,
    original_message_name_id TEXT,
    accepted                 BOOLEAN                  NOT NULL,
    violations               JSONB                    NOT NULL,
    validated_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Reporting moves from the event chain to the work queue: outcomes still waiting for the removed report listener are
-- taken from its event publications, become REPORT items, and those publications are dropped.
INSERT INTO validation_result (file_id, original_message_id, original_message_name_id, accepted, violations)
SELECT (event ->> 'fileId')::uuid, event ->> 'originalMessageId', event ->> 'originalMessageNameId',
       (event ->> 'accepted')::boolean, coalesce(event -> 'violations', '[]'::jsonb)
FROM (SELECT serialized_event::jsonb AS event
      FROM event_publication
      WHERE completion_date IS NULL AND listener_id LIKE '%StatusReportListener%') pending
ON CONFLICT DO NOTHING;

INSERT INTO work_item (file_id, step)
SELECT v.file_id, 'REPORT' FROM validation_result v
WHERE NOT EXISTS (SELECT 1 FROM status_report r WHERE r.file_id = v.file_id)
ON CONFLICT DO NOTHING;

DELETE FROM event_publication
WHERE completion_date IS NULL AND listener_id LIKE '%StatusReportListener%';
