# Backend prompt — project sort order API

Paste the block below into a session opened on the Tracky backend repo.

---

The Tracky client now persists a user-defined manual project order and needs the API to support it.
Please implement the following, plus a migration and tests.

## Context

Each project carries a nullable `sortIndex`. The client already sends and reads it on the existing
project endpoints. Under the "Custom" sort filter the user long-presses a project card and drags it
to a new position; on release the client writes every shifted index in one local transaction and
pushes the whole reorder as **one** request. It must stay one request — the client previously issued
one `PUT /api/projects/{id}` per moved card, and a failure partway through left two projects sharing
an index, which no retry could repair.

## 1. New endpoint: `PUT /api/projects/sort`

Same authentication and ownership scoping as the other `/api/projects` routes.

Request body:

```json
{
  "updatedAtUtc": "2026-08-01T10:12:03.145Z",
  "items": [
    { "projectId": "6f2a…", "sortIndex": 1 },
    { "projectId": "b813…", "sortIndex": 2 }
  ]
}
```

- `items` contains **only the projects whose index changed**. Any project not listed must keep the
  `sortIndex` it already has — this is not a full-collection replace.
- `updatedAtUtc` is one ISO-8601 instant for the whole gesture; stamp it on every listed project's
  `updatedAt` so the client's last-write-wins conflict resolution stays consistent.

Behaviour:

- Apply all items in a **single database transaction** — all indices land or none do. A partial
  write is the exact failure this endpoint exists to prevent.
- Set `sortIndex` and `updatedAt` for each listed project **owned by the caller**.
- **Silently ignore** ids that are unknown or not owned by the caller. Do not 404 and do not fail
  the batch — the client can legitimately send an id for a project deleted on another device, and
  failing the whole gesture over it would strand the user's reorder.
- Fully **idempotent**. The client retries this exact request from its offline queue, so replaying
  it must be a no-op.

Response: **`204 No Content`**, empty body.

Deliberately *not* the updated projects — the client treats its local order as authoritative for
this call, and an echo that omitted `sortIndex` would overwrite the order the user just dragged.

Errors:

| Status | When |
|---|---|
| `400` | malformed body, duplicate `projectId` in `items`, or negative `sortIndex` |
| `401` | unauthenticated |
| `5xx` | server-side failure — the client treats these as retryable and re-queues |

Routing note: register the literal `/sort` segment **before** `/api/projects/{id}` if the router
matches routes in declaration order, otherwise `sort` gets parsed as a project id.

## 2. Schema

`sortIndex` on projects: nullable integer (64-bit to match the client's `Long`), default `NULL`.

- **No unique constraint** on `(owner, sortIndex)`. Duplicate indices are legal between batches, and
  a unique index would reject valid intermediate states.
- `NULL` means "never manually ordered". The client sorts nulls first, so newly created projects
  appear at the top of the custom order.
- Add a migration if the column does not exist yet.

## 3. Existing endpoints must carry `sortIndex` through

These already accept it in their request bodies. Verify they also **persist and echo** it:

- `GET /api/projects` — include `sortIndex` in each project.
- `POST /api/projects` — persist the `sortIndex` field from `CreateProjectRequest`, echo it back.
- `PUT /api/projects/{id}` — persist the `sortIndex` field from `UpdateProjectRequest`, echo it back.

If any of these silently drops `sortIndex`, every ordinary edit (pin, rename, archive) wipes the
user's manual order on the next sync. The client has a defensive fallback that keeps the local index
when the server echoes `null`, but that only masks the problem — it does not survive a fresh install
or a second device.

## 4. Tests

- Batch of N items applied in one transaction; a forced failure on the last item rolls back the
  earlier ones.
- Unlisted projects keep their existing `sortIndex`.
- Unknown / not-owned ids are ignored, response is still `204`.
- Replaying the same request twice produces the same state.
- Duplicate `projectId` in `items` → `400`.
- Round trip: reorder, then `GET /api/projects` returns the new indices.

Deploy it to the VPS server and update the Swagger UI documentation
