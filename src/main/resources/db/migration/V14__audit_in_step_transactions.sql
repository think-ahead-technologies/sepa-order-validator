-- Audit entries are written inside the step transactions (ADR-0017); the audit event listener is removed. Entries it
-- had not written yet are taken from its pending event publications, unless the entry exists already.
INSERT INTO audit_event (id, file_id, event_type, payload)
SELECT gen_random_uuid(), (p.serialized_event::jsonb ->> 'fileId')::uuid,
       regexp_replace(p.event_type, '^.*\.', ''), p.serialized_event::jsonb
FROM event_publication p
WHERE p.completion_date IS NULL AND p.listener_id LIKE '%audit.AuditLog%'
  AND NOT EXISTS (SELECT 1 FROM audit_event a
                  WHERE a.file_id = (p.serialized_event::jsonb ->> 'fileId')::uuid
                    AND a.event_type = regexp_replace(p.event_type, '^.*\.', ''));

DELETE FROM event_publication
WHERE completion_date IS NULL AND listener_id LIKE '%audit.AuditLog%';
