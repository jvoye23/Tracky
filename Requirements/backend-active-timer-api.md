# Backend prompt — the active timer resource

Paste the block below into a session opened on the Tracky backend repo.

---

The Tracky client is adding cross-device timer sync: a timer started on one device must be visible
on the user's other devices and stoppable from any of them. That needs the server to arbitrate
which timer is running, because two offline devices cannot agree on it by themselves. Please add
the endpoints and the column below, plus a migration and tests.

Read `Requirements/backend-delta-sync-api.md` first if it has not shipped — this resource assumes
the `change_seq` sequence exists.

## Context

On the client a running timer is not a flag. It is a `task_intervals` or `sub_task_intervals` row
whose `endDateTimeUtc` is `null`, and the elapsed time shown to the user is derived —
`banked + (now − startedAt)` — never counted or stored. That is what makes cross-device display
cheap: two devices holding the same `startedAt` show the same number without exchanging a single
message per second. Only the **transitions** need to travel.

So the server does not need to run a timer, and must not try to. It needs to answer one question
authoritatively: *which interval is currently open, and when did it start?*

The product rule is that **exactly one timer runs per user at a time**, globally, across all their
devices. Starting a timer anywhere stops whatever else was running. That rule cannot be enforced by
last-write-wins on interval rows, which is what the client does for everything else. Consider two
devices, both offline: the phone starts tracking project A at 10:00, the tablet starts project B at
10:05, and both come back online at 10:30. Last-write-wins on `updated_at_utc` picks a winner for
each *row* independently, and the result is two open intervals — the user is tracking two projects
at once, which the product says is impossible, and both are accruing time against the same
wall-clock minutes. There is no client-side rule that fixes this, because neither client knows
about the other's interval until the server tells it.

The server is the only party that sees both. So it decides, and it does so with a rule that loses
no recorded work: **a later start supersedes an earlier one and closes it at the later start
time.** The phone's 10:00–10:05 session survives as a completed interval; the tablet's timer keeps
running. Nobody is billed twice for 10:05.

## 1. `GET /api/timer/active`

Same authentication and ownership scoping as the other `/api/projects` routes.

Returns the caller's currently open interval, or nothing.

```json
{
  "intervalId": "6f2a91b4-0c3d-4e58-a77f-1b9e2c4d8a03",
  "kind": "task",
  "parentProjectId": "a3a2aef0-…",
  "parentTaskId": "06357c1e-…",
  "parentSubTaskId": null,
  "parentTaskIntervalId": null,
  "startedAtUtc": "2026-09-20T08:41:07Z",
  "startedByDeviceId": "d41c7f90-…",
  "serverNowUtc": "2026-09-20T09:14:02.118000000Z"
}
```

**Behaviour:**

- `kind` is `task` or `sub_task`. For `sub_task`, `parentSubTaskId` and `parentTaskIntervalId` are
  both populated and `parentTaskId` names the enclosing task, because starting a subtask timer on
  the client also opens its parent task's interval.
- **`204` with no body when nothing is running.** This matches the existing
  `GET .../intervals/open` convention.
- Always carries `serverNowUtc`, for the same clock-correction reason as the delta endpoint.

## 2. `PUT /api/timer/active` — start

```json
{
  "intervalId": "6f2a91b4-0c3d-4e58-a77f-1b9e2c4d8a03",
  "kind": "task",
  "parentTaskId": "06357c1e-…",
  "parentSubTaskId": null,
  "parentTaskIntervalId": null,
  "startedAtUtc": "2026-09-20T08:41:07Z",
  "deviceId": "d41c7f90-…"
}
```

The client generates `intervalId`, as it does for every entity. `PUT` rather than `POST` because
the client will retry it and the operation must be safe to repeat.

**Behaviour — the three idempotency rules. Get these exactly right; the client's retry policy
depends on them and two of the three are data-loss bugs if wrong:**

1. **`intervalId` is already the active interval** → `200`, change nothing. Do not move
   `startedAtUtc`, do not bump `change_seq`. This is the ordinary lost-acknowledgement retry.
2. **A different interval is active** → in **one transaction**: close the active one with
   `endDateTimeUtc` set to the **new interval's `startedAtUtc`** and `durationMillis` recomputed
   from its own start, then open the new one. If the closed interval was a task interval with an
   open subtask interval nested inside it, close that too, at the same instant.
3. **`intervalId` exists and is already closed** → **`409`, and never resurrect it.** Without this
   rule, a retry sent after a stop whose response was lost reopens a finished interval, and the
   client then banks a second duration onto a session the user already ended. Respond with the
   current active timer (or `null`) in the error body so the client can reconcile immediately.

Additional behaviour:

- **A start whose `startedAtUtc` is earlier than the active interval's `startedAtUtc`** is a late
  arrival from a device that was offline, not a supersede. File it as a **closed** interval running
  from its `startedAtUtc` to the active interval's `startedAtUtc`, leave the active timer alone,
  and return both. Treating it as a supersede would let a stale message from a phone that has been
  in a drawer for an hour kill a timer the user is watching right now.
- Timestamps stay client-supplied and are persisted verbatim, per the existing contract. The
  server orders by arrival, not by the timestamps, except for the late-arrival rule above.
- `startedByDeviceId` is stored from `deviceId` (section 4).
- A `sub_task` start must also ensure the enclosing task interval named by `parentTaskIntervalId`
  exists and is open, creating it if the client says so — mirror whatever
  `POST .../subtasks/{id}/intervals` already does here rather than inventing a second path.

**Response `200`:**

```json
{
  "active": { /* the same shape as GET /api/timer/active */ },
  "touched": [ /* every interval row this call created, closed or modified */ ],
  "serverNowUtc": "2026-09-20T09:14:02.118000000Z"
}
```

`touched` is the point of the whole endpoint. The device that just superseded another device's
timer learns, in the same response, exactly which interval was closed and at what instant, and
writes that into its own database without waiting for the next delta. **Include the new interval
itself in `touched`**, and the nested subtask interval when one was closed.

| Status | When |
|---|---|
| `200` | started, or already active and unchanged |
| `400` | missing `deviceId`, unparseable `startedAtUtc`, `kind` inconsistent with the parent ids |
| `401` | missing or invalid credentials |
| `403` | a parent task or subtask that is not the caller's — never `404`, per the existing convention of not confirming other users' ids |
| `409` | `intervalId` exists and is closed; body carries the current active timer |
| `5xx` | the client re-queues and retries |

## 3. `POST /api/timer/active/stop` — stop

```json
{
  "intervalId": "6f2a91b4-0c3d-4e58-a77f-1b9e2c4d8a03",
  "endedAtUtc": "2026-09-20T09:22:44Z"
}
```

**This is a compare-and-swap, and it must not be a bare `DELETE /api/timer/active`.** A stop that
just says "stop whatever is running" races: the tablet's stop, sent while the user was looking at a
timer that the phone replaced 200 ms earlier, arrives and kills the phone's fresh timer instead of
the one the tablet meant. Naming the interval makes the request describe a specific intention that
the server can accept or reject. It is also why this is a `POST` and not a `DELETE` — the request
needs a body, and a body on `DELETE` is awkward for several client stacks including the one here.

**Behaviour:**

- `intervalId` is the active interval → close it with `endedAtUtc`, compute `durationMillis` from
  its stored start, and close any subtask interval nested inside it at the same instant.
- **Stopping a `sub_task` interval closes its enclosing task interval only when that interval was
  opened by this subtask.** This is currently wrong in the deployed build, which closes the
  enclosing task interval unconditionally — verified 2026-09-21. It matters because the client
  tracks which timer opened which: `SubTaskInterval.startedParentTimer` is true when starting the
  subtask is what opened the task's interval, and false when the user had already started the task
  timer and then drilled into a subtask. In that second case the task must keep running when the
  subtask stops, and today the server stops it, so the next pull closes it on the user's screen
  and banks a duration they did not ask to end.

  The client cannot fix this: `startedParentTimer` has no wire counterpart and never will — which
  timer opened which is a purely local fact. So the server needs its own record. The simplest is
  to store, on a subtask interval, whether it opened its parent, and close the parent on stop only
  when it did.
- `intervalId` is **not** the active interval → **`409`**, change nothing, and return the current
  active timer (or `null`) so the client can show the user what is actually running.
- `intervalId` is already closed with the same `endedAtUtc` → **`200`**, idempotent replay.
- `endedAtUtc` earlier than the interval's start → `400`. Do not clamp silently; a negative
  duration is a client bug worth surfacing.
- Same `touched` array as the start response.

| Status | When |
|---|---|
| `200` | stopped, or an identical replay |
| `400` | `endedAtUtc` precedes the start, or is unparseable |
| `401` | missing or invalid credentials |
| `409` | `intervalId` is not the active interval; body carries the current one |
| `5xx` | retryable |

## 4. `startedByDeviceId` on intervals

Add `started_by_device_id` — nullable `varchar`, no foreign key, no index needed — to **both**
`task_intervals` and `sub_task_intervals`, and include it in every serialisation of an interval:
the interval CRUD routes, the nested copies inside `GET /api/projects`, and the delta feed.

Accept it on `POST .../intervals` too, not only through `/api/timer/active`, so a client that falls
back to the plain interval routes while offline still records provenance.

**Nullable on purpose, and no backfill.** The client reads `NULL` as "this device", which is
exactly the behaviour every existing row should keep. It is what lets a device tell "I crashed with
a timer running, ask the user what to do with it" apart from "the user's other phone is tracking
right now, show it ticking". Without the column the second case is indistinguishable from the
first, and every cold start would interrogate the user about someone else's live timer.

**Treat it as immutable after creation.** A `PUT` that closes an interval must not change it, and
must not clear it when the field is absent from the request body — a stop sent by a second device
would otherwise erase the record of which device started the session.

## 5. Routing

Register `/api/timer/active` and `/api/timer/active/stop` before any `/api/timer/{id}` route, if
one is ever added, since the router matches in declaration order. There is no such route today.

## 6. Tests

- Start with nothing running → `200`, `GET /api/timer/active` returns it, `touched` has one entry.
- Start the same `intervalId` twice → second is `200`, `startedAtUtc` unchanged, no new
  `change_seq`.
- Start B while A is active → A is closed **at B's `startedAtUtc`**, A's `durationMillis` matches
  that span, B is active, `touched` has both.
- Start B while A is active and A has a nested open subtask interval → the subtask interval closes
  at the same instant and appears in `touched`.
- Start an `intervalId` that exists and is closed → `409`, nothing changes, body names the current
  active timer.
- Start with `startedAtUtc` **earlier** than the active interval → filed as a closed interval
  ending at the active one's start; the active timer is untouched.
- Stop naming the active interval → `200`, closed, duration correct.
- Stop naming a non-active interval → `409`, the active timer keeps running.
- Stop replayed with the same `endedAtUtc` → `200`, duration unchanged, no second close.
- Stop with `endedAtUtc` before the start → `400`.
- `GET /api/timer/active` with nothing running → `204`.
- Another user's interval id in any of these → `403`, and their timer is unaffected.
- `started_by_device_id` survives a `PUT` that closes the interval, and survives one that omits the
  field.
- `started_by_device_id` appears in `GET /api/projects`, in the interval routes and in
  `GET /api/sync/changes`.
- Every mutation above bumps `change_seq`, so the other devices see it in their next delta.

Deploy it to the VPS server and update the Swagger UI documentation.
