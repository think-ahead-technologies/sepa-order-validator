-- Keeps the audit trail append-only against TRUNCATE, which bypasses the row-level UPDATE/DELETE trigger.
CREATE TRIGGER audit_event_no_truncate
    BEFORE TRUNCATE ON audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION audit_event_reject_change();
