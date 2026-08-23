# Backend prompt — subtask interval nesting

Paste the block below into a session opened on the Tracky backend repo.

---

The Tracky client cannot persist subtask intervals it pulls from the server, because the payload
does not say which task interval each one sits inside. Please add that field, plus a migration and
tests.

## Context

On the client, a subtask timer never runs on its own. Starting a subtask also starts its parent
task's timer — opening a task interval if one is not already running — so **every subtask interval
sits inside exactly one task interval**. Stopping the subtask that opened the task's interval closes
that interval too; stopping one that merely joined an already-running task leaves it alone.

The client's local schema encodes that invariant directly: `sub_task_intervals.parentTaskIntervalId`
is `NOT NULL` with a cascading foreign key onto `task_intervals`, so deleting a task interval takes
its nested subtask intervals with it.

The server's subtask-interval payload carries only `parentSubTaskId`. That is enough to *push* a
subtask interval — the route already names every ancestor — but not to *pull* one: a client
rehydrating from `GET /api/projects` on a fresh install has no value for a `NOT NULL` column and
cannot insert the row at all. Subtask intervals are therefore the one part of the tree that does not
round-trip today.

Inferring the parent client-side was considered and rejected. The only available signal is
timestamp containment — find the parent task's interval whose span contains the subtask interval's
start — which is guesswork the moment two devices have any clock skew, and which has no answer at
all for a subtask interval whose enclosing task interval was pruned or edited. The nesting is a fact
the server already has in its rows; it should say so rather than make every client re-derive it.

## 1. Add `parentTaskIntervalId` to the subtask-interval response

Include it **everywhere a subtask interval is serialised**:

- `GET /api/projects/{projectId}/tasks/{taskId}/subtasks/{subTaskId}/intervals`
- `GET .../intervals/{id}`
- `GET .../intervals/open`
- `POST .../intervals` and `PUT .../intervals/{id}` (the echo)
- nested inside `GET /api/projects` and `GET /api/projects/{id}`

```json
{
  "id": "7a41e0c9-2b8d-4f31-8c05-9e6a3d1f4b72",
  "parentSubTaskId": "3d90b1ac-51f7-4a02-9e64-1c7b2f0e5d48",
  "parentTaskIntervalId": "9c1f0b52-6a4e-4f0d-9d16-2b5b0f8c9a31",
  "startDateTimeUtc": "2026-03-28T15:16:40Z",
  "endDateTimeUtc": "2026-03-28T15:26:40Z",
  "durationMillis": 600000,
  "updatedAtUtc": "2026-03-28T15:26:40.771000000Z"
}
```

## 2. Accept it on create

`CreateSubTaskIntervalRequest` gains a **required** `parentTaskIntervalId`. Validate that the
referenced task interval belongs to the same task as the subtask's parent:

| Status | When |
|---|---|
| `400` | the task interval exists but belongs to a different task than the subtask's parent |
| `404` | no task interval with that id is owned by the caller |

Treat it as **immutable on `PUT`**. Re-nesting an interval is not a client operation — a `PUT` only
ever closes a running interval by filling in `endDateTimeUtc` and `durationMillis`. Either ignore
the field on update or reject a changed value with `400`; do not silently re-parent.

## 3. Schema

`parent_task_interval_id` on subtask intervals: `NOT NULL`, foreign key onto the task-intervals
table, `ON DELETE CASCADE` — matching the client's Room schema exactly, so deleting a task interval
removes its nested subtask intervals on both sides.

The feature has not shipped to any client yet, so the table is expected to be empty in production:
add the column `NOT NULL` directly. If it is **not** empty, backfill by start-time containment
within the parent task's intervals (`sub.start >= ti.start AND (ti.end IS NULL OR sub.start < ti.end)`),
falling back to that task's earliest interval, then add the constraint.

## 4. Tests

- Create with a valid `parentTaskIntervalId` → `201`, and the echo carries it.
- Create with a task interval belonging to a different task → `400`.
- Create with an unknown task interval id → `404`.
- Create with the field omitted → `400`.
- `PUT` closing an interval does not change `parentTaskIntervalId`.
- Deleting a task interval cascades to its subtask intervals.
- `GET /api/projects` round trip: the field is present on every nested subtask interval.
- `GET .../intervals/open` carries the field.

## Until this ships

The client pushes subtask intervals fine — the route names every ancestor, so the field is not
needed to address them — but it will not pull them back down. Subtasks themselves already
round-trip. Once this lands, the client enables the last pull slice and the tree is complete.

Deploy it to the VPS server and update the Swagger UI documentation
