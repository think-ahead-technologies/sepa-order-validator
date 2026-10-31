-- Operations runbook (docs/operations/redrive-work-items.md): makes a FAILED or DONE work item due again with fresh
-- attempts and records who did it and why in the audit trail, in one statement. Until management APIs exist.
CREATE FUNCTION redrive_work_item(p_file_id UUID, p_step TEXT, p_operator TEXT, p_reason TEXT) RETURNS TEXT
    LANGUAGE plpgsql AS
$$
DECLARE
    previous_state TEXT;
BEGIN
    IF coalesce(trim(p_operator), '') = '' OR coalesce(trim(p_reason), '') = '' THEN
        RAISE EXCEPTION 'redrive_work_item needs an operator and reason';
    END IF;
    SELECT state INTO previous_state FROM work_item WHERE file_id = p_file_id AND step = p_step FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'No % work item for file %', p_step, p_file_id;
    END IF;
    IF previous_state = 'PENDING' THEN
        RAISE EXCEPTION 'The % work item for file % is still PENDING and is retried automatically', p_step, p_file_id;
    END IF;
    UPDATE work_item
    SET state = 'PENDING', attempts = 0, next_attempt_at = now(), lease_token = NULL, locked_until = NULL,
        completed_at = NULL
    WHERE file_id = p_file_id AND step = p_step;
    INSERT INTO audit_event (id, file_id, event_type, payload)
    VALUES (gen_random_uuid(), p_file_id, 'WorkItemRedriven',
            jsonb_build_object('fileId', p_file_id, 'step', p_step, 'operator', p_operator, 'reason', p_reason,
                               'previousState', previous_state));
    RETURN previous_state;
END;
$$;
