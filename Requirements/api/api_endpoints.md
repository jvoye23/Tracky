# Tracky API - Endpoints

## Table of Contents
- [Authentication & Security](#authentication--security)
- [API Endpoints](#api-endpoints)

---

## Authentication & Security

### Authentication Methods

1. **JWT Bearer Token** - Primary auth for user sessions
   - Sent via `Authorization: Bearer <token>` header
   - Obtained from `/api/auth/login` or `/api/auth/register`
   - Access tokens expire in 15 minutes; use `/api/auth/refresh` to renew

2. **API Key** - For programmatic access
   - Sent via `X-API-Key: <key>` header
   - Created via `POST /api/auth/apiKey?name=<name>` (requires Bearer auth)
   - Keys are SHA-256 hashed before storage
   - Grants `ROLE_API_KEY` authority

### Security Filter Chain

```
Request -> JwtAuthFilter -> ApiKeyAuthFilter -> AuthorizationFilter -> Controller
```

### Public Endpoints (no auth required)

- `/api/auth/**` - All auth endpoints
- `/swagger-ui/**`, `/swagger-ui.html` - Swagger UI
- `/v3/api-docs/**` - OpenAPI spec
- `/actuator/**` - Health checks
- `/error` - Error handler

> `POST /api/auth/change-password` and `POST /api/auth/apiKey` still require a Bearer token even though `/api/auth/**` is `permitAll` in the filter chain. Authentication for those two is enforced in the controller by the `requestUserId` helper, which throws `UnauthorizedException` (401) when there is no `UUID` principal in the security context.

### Rate Limiting

IP-based rate limiting using Redis (Lua script for atomicity):

| Endpoint | Limit |
|----------|-------|
| `POST /api/auth/register` | 5 requests / 60 seconds |
| `POST /api/auth/login` | 10 requests / 60 seconds |
| `POST /api/auth/resend-verification` | 3 requests / 60 seconds |
| `POST /api/auth/forgot-password` | 3 requests / 60 seconds |
| `POST /api/auth/reset-password` | 5 requests / 60 seconds |

### Password Requirements

Enforced by the custom `@Password` bean-validation constraint (`user/.../api/validation/Password.kt`). Passwords are stored BCrypt-hashed (`BCryptPasswordEncoder`); API keys and refresh tokens are stored SHA-256 hashed.

- Minimum 8 characters
- At least one uppercase letter
- At least one lowercase letter
- At least one digit
- At least one special character

---

## API Endpoints

### Base URL

```
https://tracky.jv-coding-solutions.com
```

### Authentication (`/api/auth`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/register` | None | Register a new user |
| POST | `/api/auth/login` | None | Login with email and password |
| POST | `/api/auth/refresh` | None | Refresh access token |
| POST | `/api/auth/logout` | None | Invalidate refresh token |
| POST | `/api/auth/resend-verification` | None | Resend email verification link |
| GET | `/api/auth/verify?token=` | None | Verify email address |
| POST | `/api/auth/forgot-password` | None | Request password reset email |
| POST | `/api/auth/reset-password` | None | Reset password with token |
| POST | `/api/auth/change-password` | Bearer | Change password |
| POST | `/api/auth/apiKey?name=` | Bearer | Create an API key |

### Projects (`/api/projects`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/projects` | Bearer / API Key | List all projects (includes tasks and intervals) |
| POST | `/api/projects` | Bearer / API Key | Create a new project |
| GET | `/api/projects/{id}` | Bearer / API Key | Get project by ID (includes tasks) |
| PUT | `/api/projects/sort` | Bearer / API Key | Batch-update the manual sort order (204, no body) |
| PUT | `/api/projects/{id}` | Bearer / API Key | Update a project |
| DELETE | `/api/projects/{id}` | Bearer / API Key | Delete project and all tasks |

### Project Tasks (`/api/projects/{projectId}/tasks`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/projects/{projectId}/tasks` | Bearer / API Key | List all tasks |
| POST | `/api/projects/{projectId}/tasks` | Bearer / API Key | Create a task |
| GET | `/api/projects/{projectId}/tasks/{id}` | Bearer / API Key | Get task by ID |
| PUT | `/api/projects/{projectId}/tasks/{id}` | Bearer / API Key | Update a task |
| DELETE | `/api/projects/{projectId}/tasks/{id}` | Bearer / API Key | Delete a task |

### Task Intervals (`/api/projects/{projectId}/tasks/{taskId}/intervals`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/projects/{projectId}/tasks/{taskId}/intervals` | Bearer / API Key | List all intervals for a task |
| POST | `/api/projects/{projectId}/tasks/{taskId}/intervals` | Bearer / API Key | Create an interval |
| GET | `/api/projects/{projectId}/tasks/{taskId}/intervals/open` | Bearer / API Key | Get the currently open (unfinished) interval, or 204 if none |
| GET | `/api/projects/{projectId}/tasks/{taskId}/intervals/{id}` | Bearer / API Key | Get interval by ID |
| PUT | `/api/projects/{projectId}/tasks/{taskId}/intervals/{id}` | Bearer / API Key | Update an interval |
| DELETE | `/api/projects/{projectId}/tasks/{taskId}/intervals/{id}` | Bearer / API Key | Delete an interval |
| DELETE | `/api/projects/{projectId}/tasks/{taskId}/intervals` | Bearer / API Key | Delete all intervals for a task |

### Request/Response Examples

#### Register

```bash
curl -X POST https://tracky.jv-coding-solutions.com/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","username":"myuser","password":"MyPass@123"}'
```

```json
{
  "user": {
    "id": "3010de5f-3239-4c7b-8ed2-f94d54c682cd",
    "email": "user@example.com",
    "username": "myuser",
    "hasVerifiedEmail": false
  },
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "a94d2949-ec74-40db-b171-e65e0fde91f4"
}
```

#### Create Project

```bash
curl -X POST https://tracky.jv-coding-solutions.com/api/projects \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{"id":"00c45a6b-0cf0-4592-ba96-03268ad27eb8","title":"My Project","color":-43230,"useLightTextColor":false,"startDateTimeUtc":"2026-03-28T15:24:24Z"}'
```

`id` is a client-generated UUID (the mobile app uses offline-first storage and assigns the primary key locally). The server persists this id verbatim and returns it in the response. Re-POSTing the same id returns `409 Conflict` with `{"code":"DUPLICATE_RESOURCE"}`.

`color` is a packed ARGB int (e.g. `-43230` = `0xFFFF5733`). `useLightTextColor` defaults to `false` if omitted.

`sortIndex` is an optional nullable 64-bit int holding the client's manual ("Custom") ordering. On the CRUD endpoints the server stores and echoes it verbatim — it never sorts by it and does not validate ranges or uniqueness. Omitting it on create or update stores `null`, which the client reads as "never manually ordered" and sorts first. Duplicate indices across projects are legal, so there is deliberately **no** unique constraint on `(user_id, sort_index)`. To move several projects at once, use `PUT /api/projects/sort` (below) rather than one `PUT /api/projects/{id}` per project.

```json
{
  "id": "00c45a6b-0cf0-4592-ba96-03268ad27eb8",
  "title": "My Project",
  "description": null,
  "color": -43230,
  "totalDuration": null,
  "startDateTimeUtc": "2026-03-28T15:24:24Z",
  "isFinished": false,
  "useLightTextColor": false,
  "endDateTimeUtc": null,
  "isArchived": false,
  "trashedAtUtc": null,
  "isPinned": false,
  "updatedAtUtc": "2026-03-28T15:24:24.968831830Z",
  "sortIndex": null,
  "tasks": []
}
```

#### List Projects

```bash
curl https://tracky.jv-coding-solutions.com/api/projects \
  -H "Authorization: Bearer <accessToken>"
```

Returns **the full tree** — every project the caller owns, each with its complete `tasks` array, and each task with its complete `intervals` array. This is the same shape `GET /api/projects/{id}` returns, for the whole collection, so one call is enough to hydrate the client's offline store; the per-project `/tasks` and per-task `/intervals` endpoints remain available for targeted reads.

A project with no tasks comes back with `"tasks": []`, never `null`; likewise a task with no intervals gets `"intervals": []`.

```json
[
  {
    "id": "00c45a6b-0cf0-4592-ba96-03268ad27eb8",
    "title": "My Project",
    "color": -43230,
    "startDateTimeUtc": "2026-03-28T15:24:24Z",
    "updatedAtUtc": "2026-03-28T15:24:24.968831830Z",
    "sortIndex": null,
    "tasks": [
      {
        "id": "fe316e35-bd3f-4c6f-9d7d-23d6b6e8877e",
        "description": "Work task",
        "durationMillis": 3600000,
        "startDateTimeUtc": "2026-03-28T15:16:40Z",
        "endDateTimeUtc": null,
        "isFinished": false,
        "isTimerRunning": true,
        "updatedAtUtc": "2026-03-28T15:16:40.112000000Z",
        "intervals": [
          {
            "id": "9c1f0b52-6a4e-4f0d-9d16-2b5b0f8c9a31",
            "parentTaskId": "fe316e35-bd3f-4c6f-9d7d-23d6b6e8877e",
            "startDateTimeUtc": "2026-03-28T15:16:40Z",
            "endDateTimeUtc": "2026-03-28T16:16:40Z",
            "durationMillis": 3600000,
            "updatedAtUtc": "2026-03-28T16:16:40.402000000Z"
          }
        ]
      }
    ]
  }
]
```

(Scalar project fields are elided above for brevity — the response carries the same field set as the create-project response shown earlier, plus `tasks`.)

Result order is **not** guaranteed: neither the projects nor the nested tasks and intervals are sorted server-side. In particular `sortIndex` is stored and echoed verbatim but never ordered by — the client applies its own ordering. See the `sortIndex` note above.

#### Reorder Projects

```bash
curl -i -X PUT https://tracky.jv-coding-solutions.com/api/projects/sort \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{"updatedAtUtc":"2026-08-01T10:12:03.145Z","items":[{"projectId":"6f2a1c34-...","sortIndex":1},{"projectId":"b8137af0-...","sortIndex":2}]}'
```

```
HTTP/2 204
```

One request per drag gesture. The whole batch is applied in a single database transaction — every listed index lands or none does, so a failure can never leave two projects sharing an index.

- `items` lists **only the projects whose index changed**. Projects that are not listed keep the `sortIndex` they already have; this is not a full-collection replace.
- `updatedAtUtc` is one ISO-8601 instant for the whole gesture, stamped verbatim onto every listed project's `updatedAtUtc` so last-write-wins conflict resolution stays consistent.
- Ids that are unknown or owned by another user are **silently ignored** — no 404, and the rest of the batch still applies. A project deleted on another device must not strand the reorder.
- **Idempotent.** Every write is an absolute assignment and the timestamp comes from the request, not the server clock, so replaying the request from the offline queue is a no-op.
- The response is `204 No Content` with an empty body — deliberately not the updated projects, because the client's local order is authoritative for this call.

| Status | When |
|--------|------|
| `204` | Sort order applied |
| `400` | Malformed body, duplicate `projectId` in `items`, or negative `sortIndex` |
| `401` | Unauthenticated |
| `5xx` | Server-side failure — retryable, nothing was written |

#### Create Task

```bash
curl -X POST https://tracky.jv-coding-solutions.com/api/projects/{projectId}/tasks \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{"id":"fe316e35-bd3f-4c6f-9d7d-23d6b6e8877e","description":"Work task","durationMillis":3600000,"startDateTimeUtc":"2026-03-28T15:16:40Z","isTimerRunning":true}'
```

```json
{
  "id": "fe316e35-bd3f-4c6f-9d7d-23d6b6e8877e",
  "description": "Work task",
  "durationMillis": 3600000,
  "startDateTimeUtc": "2026-03-28T15:16:40Z",
  "endDateTimeUtc": null,
  "isFinished": false,
  "isTimerRunning": true,
  "updatedAtUtc": "2026-03-28T15:16:40.845724101Z",
  "intervals": []
}
```

`id` is a client-generated UUID and is the only required field besides `startDateTimeUtc`. `description` and `durationMillis` are both optional and nullable. `intervals` is empty until the first interval is created via the intervals endpoints.

#### Create Task Interval

```bash
curl -X POST https://tracky.jv-coding-solutions.com/api/projects/{projectId}/tasks/{taskId}/intervals \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{"id":"8e2a91d4-6c2c-4b6d-9e2f-77a8d2c1f5b1","startDateTimeUtc":"2026-03-28T15:16:40Z"}'
```

```json
{
  "id": "8e2a91d4-6c2c-4b6d-9e2f-77a8d2c1f5b1",
  "parentTaskId": "fe316e35-bd3f-4c6f-9d7d-23d6b6e8877e",
  "startDateTimeUtc": "2026-03-28T15:16:40Z",
  "endDateTimeUtc": null,
  "durationMillis": 0,
  "updatedAtUtc": "2026-03-28T15:16:40.694098965Z"
}
```

`id` is a client-generated UUID. `endDateTimeUtc: null` means the interval is open (timer running). Close it by `PUT`-ing the same path with `endDateTimeUtc` and `durationMillis` filled in. `GET .../intervals/open` returns 204 No Content when there is no open interval.

