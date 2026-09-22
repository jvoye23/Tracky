# Backend prompt — delta sync, tombstones and bulk import

Paste the block below into a session opened on the Tracky backend repo.

---

The Tracky client is adding multi-device sync, and the current pull cannot support it. Please add a
delta change feed with a server-assigned cursor, tombstones for deletions, and a bulk import
endpoint — plus migrations and tests.

## Context

The client is offline-first: Room is the source of truth for every read and write, writes go local
first, and anything the network refuses is queued in a local outbox and retried. The only way the
server's state reaches a device today is `GET /api/projects`, which returns the entire tree and is
throttled to once every five minutes.

That has two consequences which block multi-device sync.

**Deletions never propagate at all.** The client's tree-merge deliberately never deletes a local
row that the server's response omits, because an omitted row is ambiguous: it may have been deleted
on another device, or it may have been created on *this* device seconds ago and not pushed yet.
Deleting on that signal would destroy offline work, so the client keeps everything. A project
deleted on a phone therefore lives on the tablet forever. The server has to say "this was deleted"
explicitly; absence is not a statement.

**A five-minute full-tree pull cannot carry a running timer.** The client is about to let a timer
started on one device be seen and stopped on another, and a transition has to reach the other
device in seconds, not minutes. Re-downloading every project to learn that one interval closed is
not something we can do at that frequency.

Using `updated_at_utc` as the cursor was considered and rejected. Those columns are ISO-8601
`varchar` supplied by the client and persisted verbatim — that is the documented contract, and it
is the right one, because the client owns the wall-clock meaning of its records. But it makes them
useless as a cursor: they are not assigned by the server, not monotonic, and two devices with
skewed clocks routinely write values out of order. A device could miss a change permanently by
advancing its cursor past a row that was written later but stamped earlier. The cursor has to be a
value only the server assigns.

## 1. A per-user change sequence

Add `change_seq bigint NOT NULL` to all five syncable tables: projects, tasks, task intervals,
subtasks and subtask intervals.

**Behaviour:**

- Assigned by the server on **every** write to the row — insert, update, and the sort endpoints.
  A single database sequence shared by all tables and all users is fine; only ordering *within* one
  user matters, and gaps are expected and harmless.
- **Strictly increasing.** Two rows written in the same transaction may share a value; a later
  transaction must never produce a lower one.
- **Never reset, never reused**, including when a row is updated repeatedly.
- The client never sends it and never stores it per row — it is a server-side ordering device only,
  so **do not add it to any response payload**.

Index `(user_id, change_seq)` on each table. That index serves every query in section 2.

Bumping this on the sort endpoints is easy to forget, because they update rows in bulk outside the
normal single-row update path. A reorder that does not bump `change_seq` is invisible to every
other device.

## 2. `GET /api/sync/changes`

Same authentication and ownership scoping as the other `/api/projects` routes.

Query parameters: `since` (optional `bigint`; omitted or `0` means "everything"), `limit`
(optional, default 500, max 1000).

```json
{
  "cursor": 84213,
  "serverNowUtc": "2026-09-20T09:14:02.118000000Z",
  "fullResyncRequired": false,
  "hasMore": false,
  "projects": [ /* full project objects, WITHOUT nested tasks */ ],
  "tasks": [ /* full task objects, WITHOUT nested intervals or subtasks */ ],
  "taskIntervals": [],
  "subTasks": [],
  "subTaskIntervals": [],
  "tombstones": [
    { "entityType": "project", "entityId": "6f2a…", "deletedAtUtc": "2026-09-20T08:02:11Z" }
  ]
}
```

**Behaviour:**

- Returns every row owned by the caller whose `change_seq` is **greater than** `since`, across all
  five types, plus every tombstone with `change_seq > since`.
- The collections are **flat, not nested**. This is deliberate and differs from `GET /api/projects`:
  a delta routinely carries a task whose project did not change, and nesting it would force the
  server to send unchanged parents.
- **Every row below project level must carry `parentProjectId`**, including tasks, task intervals,
  subtasks and subtask intervals. This is the one field the nested payload does *not* have, because
  there it is implied by the enclosing project — but the client stores it on every level as a
  `NOT NULL` column backing a cascading foreign key onto projects, so a flat row without it cannot
  be written at all. The client drops such a row rather than failing the whole delta, which means
  omitting the field loses data silently. The other parent ids (`parentTaskId`,
  `parentSubTaskId`, `parentTaskIntervalId`) are already on the wire and stay as they are.
- Order within each collection does not matter. The **client** applies them parents-first
  (projects → tasks → task intervals → subtasks → subtask intervals) and skips any row whose parent
  it does not have.
- `cursor` is the **highest `change_seq` actually included in this response**, not the current
  maximum for the user. A client that stores it and passes it back must never skip a row.
- Empty collections are always `[]`, never `null` — the client decodes strictly on shape.
- When more rows remain than `limit` allows, truncate at a `change_seq` boundary (**never split
  rows sharing one `change_seq` across two pages**, or a client that stops between them loses the
  remainder forever), set `hasMore: true`, and set `cursor` to the last included value. The client
  immediately re-requests with the new cursor.
- `since` newer than anything on the server is not an error: return empty collections and echo the
  caller's `since` as `cursor`.
- With `since` omitted or `0`, return everything the user owns and **no tombstones** — a fresh
  client has nothing to delete.

| Status | When |
|---|---|
| `200` | normal, including "nothing changed" |
| `400` | `since` is negative or not a number, or `limit` is out of range |
| `401` | missing or invalid credentials |
| `5xx` | the client treats these as retryable, keeps its cursor unchanged and re-queues |

## 3. Tombstones

New table, one row per deleted entity:

```
user_id      not null
entity_type  not null   -- project | task | task_interval | sub_task | sub_task_interval
entity_id    not null   -- the client-generated UUID of the deleted row
change_seq   bigint not null
deleted_at   not null
```

Index `(user_id, change_seq)`.

**Behaviour:**

- Written on **every** delete, including the cascades. Deleting a project currently removes its
  tasks, intervals, subtasks and subtask intervals through `ON DELETE CASCADE` at all four
  levels — each of those removals needs its own tombstone. A client receiving only the project's
  tombstone deletes it locally and its own cascade handles the children, but a client that has the
  *task* and not the project (because it pulled mid-tree) would be left with orphans.
- Retain for **90 days**, then purge. Keep the purge in a scheduled job, not in the request path.
- Maintain a per-user **minimum retained `change_seq`**. When a request arrives with a `since`
  older than that value, the server cannot prove what was deleted in the gap: respond `200` with
  `"fullResyncRequired": true`, empty collections, and `cursor` unchanged. The client then falls
  back to `GET /api/projects` and re-reads `/api/timer/active` afterwards.
- **Do not** delete the tombstone when an entity with the same id is re-created. Both rows coexist,
  ordered by `change_seq`, and the later one wins. Deleting the tombstone would hide the deletion
  from a device that has not caught up.

Deliberately *not* soft deletes on the entity tables. The client's "trash" is already a soft delete
it owns via `trashed_at_utc`, and conflating the two would make a trashed project indistinguishable
from a deleted one on the wire.

## 4. `serverNowUtc` on the delta response

The client cannot trust its own clock for timer arithmetic. A running timer's elapsed time is
computed as `now − startedAt` independently on every device, so a phone whose clock is forty
seconds fast displays a duration forty seconds wrong — and it is the *other* device's `startedAt`
it is subtracting from.

So: stamp every `GET /api/sync/changes` response with `serverNowUtc`, the server's own time at the
moment the response is generated. The client measures the round trip, derives an offset and applies
it to timer display only. No other endpoint needs to change; one reliable sample every sync cycle
is enough.

## 5. `POST /api/sync/import`

For the one-shot upload when a user who has been working locally subscribes and gets an account for
the first time. The body is the same shape as `GET /api/projects` returns — the whole tree, nested.

**Behaviour:**

- **One database transaction.** Either the entire tree lands or nothing does; a partial import
  leaves the client unable to tell what it still owes.
- Ids are client-generated UUIDs, as everywhere else, and are persisted verbatim.
- **Idempotent on replay**: an id that already exists for this user is skipped, not rejected and
  not overwritten. A client whose connection dropped mid-response must be able to send the same
  payload again and reach the same state.
- Assigns `change_seq` to everything it writes, so the importing device's own next delta is a
  no-op and its other devices pick the tree up normally.
- Response `200` with `{ "cursor": <highest change_seq written>, "serverNowUtc": … }`, so the
  client can store a cursor immediately rather than pulling everything straight back down.

| Status | When |
|---|---|
| `200` | imported, or every id already present |
| `400` | malformed tree, a child naming a parent absent from both the payload and the database, or a duplicate id **within the payload** |
| `401` | missing or invalid credentials |
| `413` | payload above the size cap — say what the cap is in the body so the client can chunk by project |

## 6. Tests

- A row updated after a cursor appears in the next delta; a row updated before it does not.
- `change_seq` strictly increases across separate transactions, including through both sort
  endpoints and through a bulk reorder that touches many rows.
- Deleting a project writes tombstones for the project **and** every cascaded task, interval,
  subtask and subtask interval.
- A tombstone appears in a delta requested with a `since` below its `change_seq`, and not above it.
- Re-creating an entity with a previously deleted id leaves both rows, and the delta carries the
  re-creation after the tombstone.
- `since` older than the retention window returns `fullResyncRequired: true` with empty collections.
- `since` omitted returns the full tree and **no** tombstones.
- Every flat row below project level carries `parentProjectId`, at all four levels, and a
  `GET /api/projects` round trip still omits it inside the nested payload.
- Paging: with `limit` smaller than the change set, `hasMore` is true, the cursor advances, and
  requesting again with it returns the remainder exactly once. Rows sharing one `change_seq` are
  never split across pages.
- A second user's rows never appear in the first user's delta.
- `POST /api/sync/import` replayed twice produces one tree, and a payload with an unresolvable
  parent rolls the whole thing back.
- A `GET /api/projects` round trip after an import returns exactly what was imported.

Deploy it to the VPS server and update the Swagger UI documentation.
