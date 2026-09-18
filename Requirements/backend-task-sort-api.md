# Backend prompt — task and subtask sort order API

Paste the block below into a session opened on the Tracky backend repo.

---

The Tracky client now persists a user-defined manual order for a project's **tasks** and for a
task's **subtasks**, the same way it already does for projects. The API needs to support both.
Please implement the following, plus migrations and tests.

## Context

`PUT /api/projects/sort` and `projects.sort_index` already exist and work; this is that same
feature one and two levels down. `project_tasks` and `project_subtasks` have no `sort_index` column
and no `/sort` route, so the client's reorder currently queues offline and retries forever.

On the client the user turns on edit mode and drags a row by the grip beside it. On release the
client writes every shifted index in one local transaction and pushes the whole reorder as **one**
request. It must stay one request — the project feature learned this the hard way: one
`PUT /api/projects/{id}` per moved card left two projects sharing an index when a call failed
partway through, which no retry could repair.

Two scoping rules matter and are already enforced client-side:

- A task's order is scoped to its **project**. A drag never moves a task to another project.
- A subtask's order is scoped to its **parent task**. A subtask is never re-parented, so a reorder
  must never change `parent_task_id`.

## 1. New endpoint: `PUT /api/projects/{projectId}/tasks/sort`

Same authentication and ownership scoping as the other `/api/projects/{projectId}/tasks` routes.

Request body:

```json
{
  "updatedAtUtc": "2026-09-18T10:12:03.145Z",
  "items": [
    { "id": "6f2a…", "sortIndex": 1 },
    { "id": "b813…", "sortIndex": 2 }
  ]
}
```

Note `id`, not `taskId`: one request type serves both levels on the client, because the parent is
already in the path.

Behaviour:

- `items` contains **only the tasks whose index changed**. Any task not listed keeps the
  `sortIndex` it already has — this is not a full-collection replace.
- `updatedAtUtc` is one ISO-8601 instant for the whole gesture; stamp it on every listed task's
  `updatedAt` so the client's last-write-wins conflict resolution stays consistent.
- Apply all items in a **single database transaction** — all indices land or none do. A partial
  write is the exact failure this endpoint exists to prevent.
- Only touch tasks **belonging to `{projectId}`** and owned by the caller.
- **Silently ignore** ids that are unknown, not owned by the caller, or that belong to a different
  project. Do not 404 and do not fail the batch — the client can legitimately send an id for a task
  deleted on another device, and failing the whole gesture over it would strand the user's reorder.
- Fully **idempotent**. The client retries this exact request from its offline queue, so replaying
  it must be a no-op.

Response: **`204 No Content`**, empty body.

Deliberately *not* the updated tasks — the client treats its local order as authoritative for this
call, and an echo that omitted `sortIndex` would overwrite the order the user just dragged.

Errors:

| Status | When |
|---|---|
| `400` | malformed body, duplicate `id` in `items`, or negative `sortIndex` |
| `401` | unauthenticated |
| `404` | the project itself is unknown or not owned by the caller |
| `5xx` | server-side failure — the client treats these as retryable and re-queues |

## 2. New endpoint: `PUT /api/projects/{projectId}/tasks/{taskId}/subtasks/sort`

Identical in every respect, one level down: same body shape, same 204, same silent-ignore rule.
Scope it to subtasks whose `parent_task_id` is `{taskId}`, and ignore any id that is not one of
them. A `404` applies when the parent task is unknown or not owned by the caller.

The reorder must not write `parent_task_id` or `parent_project_id` — a subtask never moves between
tasks, and a reorder that could re-parent one would also orphan its intervals, which are nested
inside the parent task's intervals.

## 3. Routing

Register the literal `/sort` segment **before** `/api/projects/{projectId}/tasks/{id}` and before
`.../subtasks/{id}` if the router matches routes in declaration order, otherwise `sort` is parsed
as a task or subtask id. `ProjectController` already does this for the project-level route and has
the comment to match — follow it.

## 4. Schema

`sort_index` on both `project_service.project_tasks` and `project_service.project_subtasks`:
nullable `bigint` (to match the client's `Long?`), default `NULL`.

- **No unique constraint** on `(parent, sort_index)`. Duplicate indices are legal between batches,
  and a unique index would reject valid intermediate states. This mirrors the deliberate absence of
  one on `(user_id, sort_index)` for projects.
- `NULL` means "never manually ordered". Note the client sorts nulls **last** here, the opposite of
  projects: tasks are numbered `01, 02, 03…` top-down, so a newly created task belongs at the
  bottom. The server does not need to know this — it never sorts by the column — but it explains
  why no backfill is wanted. Leave existing rows `NULL`.
- Add migrations for both columns if they do not exist yet.

## 5. Existing endpoints must carry `sortIndex` through

The client already sends `sortIndex` in these request bodies. Verify each one **persists and echoes**
it:

- `GET /api/projects` — include `sortIndex` on every nested task and subtask.
- `POST|PUT /api/projects/{projectId}/tasks[/{id}]`
- `POST|PUT /api/projects/{projectId}/tasks/{taskId}/subtasks[/{id}]`

If any of these silently drops `sortIndex`, every ordinary edit — a rename, finishing a task,
stopping a timer — wipes the user's manual order on the next sync. The client has a defensive
fallback (`withLocalSortIndexFallback`) that keeps the local index when the server echoes `null`,
but that only masks the problem: it does not survive a fresh install or a second device.

## 6. Tests

Per level:

- Batch of N items applied in one transaction; a forced failure on the last item rolls back the
  earlier ones.
- Unlisted tasks/subtasks keep their existing `sortIndex`.
- Unknown, not-owned, and wrong-parent ids are ignored; the response is still `204`.
- Replaying the same request twice produces the same state.
- Duplicate `id` in `items` → `400`.
- An unknown parent (project, or task for the subtask route) → `404`.
- A subtask reorder leaves `parent_task_id` and `parent_project_id` untouched.
- Round trip: reorder, then `GET /api/projects` returns the new indices on the nested rows.

Deploy it to the VPS server and update the Swagger UI documentation.
