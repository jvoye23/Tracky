# Backend prompt — a subtask stop closes its parent interval too eagerly

Paste the block below into a session opened on the Tracky backend repo.

---

The Tracky client's cross-device timer is code-complete and verified against this backend: 46 of 48
contract assertions pass. The two that fail are one bug, described below. It blocks the two-device
end-to-end pass, so it is the only thing outstanding before the client can be tested for real.

## What the client models

Timing a subtask runs two intervals at once: the subtask's own, and the task interval enclosing it.
The client records **which of the two the user actually started**, on the subtask interval row, as
`startedParentTimer`:

- User starts a **subtask** directly → the subtask start is what opened the task interval →
  `startedParentTimer = true`. Stopping the subtask stops both.
- User starts the **task** timer, then drills into a subtask → the task interval was already open →
  `startedParentTimer = false`. Stopping the subtask leaves the task running.

That second case is the ordinary "I'm working on this task, and right now specifically on this
part of it" flow.

## The bug

`POST /api/timer/active/stop` on a `sub_task` interval closes the enclosing task interval
**unconditionally**. Verified on the deployed build, 2026-09-21:

| Probe | Server behaviour | Correct |
|---|---|---|
| `sub_task` start naming a `parentTaskIntervalId` the server does not have | creates it with that id, at the same `startedAtUtc`, both rows in `touched` | yes |
| `sub_task` start naming one that is already open | leaves it open, only the subtask row in `touched` | yes |
| stop a subtask that **opened** its parent | closes both at the same instant, then `GET /api/timer/active` → `204` | yes |
| task timer started **independently**, then a subtask started, then the subtask stopped | **closes both**, then `204` | **no** |

The last row is the bug. Expected: the subtask interval closes, the task interval stays open, and
`GET /api/timer/active` returns the task interval.

It does not stop at a wrong response. The closed task interval goes out on the next
`GET /api/sync/changes`, and the client's merge rule lets a server-closed interval win over a
locally-open one — so the user's task timer disappears from their screen and a duration they never
asked to end is banked.

## Why the client cannot fix this

`startedParentTimer` has no wire counterpart and deliberately never will. Which timer opened which
is a purely local fact, which is why the client's pull merge *preserves* its own copy rather than
taking the server's. The server needs its own record.

## The fix

The signal already exists at start time — you just do not persist it. The two probes above show
this endpoint already branches: a `sub_task` start either **creates** the enclosing task interval
(because the named `parentTaskIntervalId` does not exist) or **finds it already open**.

1. Add `opened_parent_interval boolean not null default false` to `sub_task_intervals`.
2. On `PUT /api/timer/active` with `kind = sub_task`: set it `true` when that call created the
   enclosing task interval, `false` when the interval was already open.
3. On `POST /api/timer/active/stop` for a `sub_task` interval: close the enclosing task interval
   **only when `opened_parent_interval` is true**. Otherwise close the subtask interval alone and
   leave the task timer running — and it then becomes the active timer, so `GET /api/timer/active`
   must return it.

`default false` is the right default for existing rows, and for a subtask interval created through
the plain `POST .../subtasks/{id}/intervals` route where the parent is not created by that call:
**not** closing the parent is the safe failure. The user can always stop a task timer themselves;
a timer silently ended and banked cannot be recovered.

Unchanged: stopping a **task** interval still closes any subtask interval nested inside it, at the
same instant. That direction is correct today.

## Tests

- Task timer started independently → subtask started → subtask stopped → the subtask interval is
  closed, the task interval is still open, `GET /api/timer/active` returns the task interval, and
  the task interval is not reported as closed in `touched`.
- Subtask started directly (creating the enclosing interval) → subtask stopped → both closed at the
  same instant, both in `touched`, then `GET /api/timer/active` → `204`.
- Stopping a **task** interval with an open subtask interval inside it still closes both.
- `opened_parent_interval` is set correctly on both start paths, and existing rows read `false`.
- Every mutation above still bumps `change_seq`.

## Verifying

The client repo has `scripts/verify_sync_contract.sh`, which exercises all of this against a
deployed environment. It currently exits non-zero on exactly these two assertions:

```
FAIL  a task timer started independently survives its subtask being stopped — expected 200, got 204
FAIL    and it is the task interval that is still running
```

Green means done.

Deploy it to the VPS server and update the Swagger UI documentation.
