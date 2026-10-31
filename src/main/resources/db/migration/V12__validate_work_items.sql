-- Validation moves from the event chain to the work queue (ADR-0017): files still awaiting validation become VALIDATE
-- items, and the event publications of the removed validation listener are dropped so they are never resubmitted.
INSERT INTO work_item (file_id, step)
SELECT id, 'VALIDATE' FROM payment_file WHERE status = 'RECEIVED'
ON CONFLICT DO NOTHING;

DELETE FROM event_publication
WHERE completion_date IS NULL AND listener_id LIKE '%PaymentFileValidationListener%';
