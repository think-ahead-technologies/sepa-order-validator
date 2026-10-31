# Runbook: re-drive a work item

Each processing step of a payment file (`VALIDATE`, `REPORT`, `DELIVER`) is a row in `work_item` ([ADR-0017](../adr/0017-file-processing-driven-by-persisted-state-with-a-postgresql-work-queue.md)). Failed attempts are retried automatically with backoff. Once a step has used up its attempts, its item is `FAILED`, an ERROR is logged, and nothing moves it on until operations re-drive it.

`VALIDATE` never reaches `FAILED`: after its last attempt the file is rejected technically (`FF01`), so the customer always gets a reply.

## When to use it

- A `REPORT` or `DELIVER` item is `FAILED`, and the cause (e.g. permissions on the customer's SFTP outbound directory) has been fixed.
- A customer asks for a status report again: re-drive the `DONE` `DELIVER` item. The stored report is sent again, with identical content and the same name and `MsgId`.

Do not re-drive a `PENDING` item. It is retried automatically, and the function refuses.

## Find the item

```sql
select w.file_id, f.file_name, f.submitter, w.step, w.state, w.attempts, w.last_error, w.next_attempt_at
from work_item w
join payment_file f on f.id = w.file_id
where w.state = 'FAILED'
order by w.next_attempt_at;
```

The ERROR log line carries the same file id in its `fileId` field ([ADR-0018](../adr/0018-structured-logging-and-operational-endpoints.md)).

## Re-drive it

```sql
select redrive_work_item('<file id>', 'DELIVER', '<your user name>', '<why, with ticket number>');
```

- Operator and reason are mandatory.
- The function returns the item's previous state.
- In the same statement, the function makes the item due now with fresh attempts and writes a `WorkItemRedriven` audit entry. That entry records the step, the operator, the reason and the previous state.

## Check the result

```sql
select step, state, attempts, last_error from work_item where file_id = '<file id>';
select event_type, payload, recorded_at from audit_event where file_id = '<file id>' order by recorded_at;
```

The item is `DONE` again within a few seconds when the cause is fixed. If it fails again, it is retried with backoff and ends up `FAILED` once more.
