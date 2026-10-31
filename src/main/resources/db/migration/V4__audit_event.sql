-- Append-only audit trail of every module event per payment file (audit module).
CREATE TABLE audit_event
(
    id          UUID PRIMARY KEY,
    file_id     UUID                     NOT NULL,
    event_type  TEXT                     NOT NULL,
    payload     JSONB                    NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX audit_event_file_id_idx ON audit_event (file_id);

CREATE FUNCTION audit_event_reject_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only: % is not allowed', TG_OP;
END;
$$;

CREATE TRIGGER audit_event_append_only
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION audit_event_reject_change();
