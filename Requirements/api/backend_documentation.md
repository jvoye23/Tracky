# Tracky API - Complete Documentation

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Module Structure](#module-structure)
- [Dependencies](#dependencies)
- [External Services](#external-services)
- [Credentials & Configuration](#credentials--configuration)
- [Database](#database)
- [Authentication & Security](#authentication--security)
- [API Endpoints](#api-endpoints)
- [Deployment](#deployment)
- [Local Development](#local-development)

---

## Overview

Tracky API is the backend for the Tracky mobile time-tracking app. It provides user authentication, project management, and task tracking via a REST API.

- **Language:** Kotlin 2.3.0
- **Framework:** Spring Boot 4.0.5
- **Java:** 25 (Eclipse Temurin)
- **Build Tool:** Gradle 9.4.1 with Kotlin DSL
- **Base Package:** `com.jvcodingsolutions.trackyapi`

---

## Architecture

```
                   +------------------+
                   |   Mobile App     |
                   +--------+---------+
                            |
                    HTTPS (port 443)
                            |
                   +--------v---------+
                   |   Caddy (TLS)    |
                   |  Let's Encrypt   |
                   +--------+---------+
                            |
                    HTTP via docker net
                            |
                   +--------v---------+
                   |   Tracky API     |
                   |  (Spring Boot)   |
                   +--+----+----+--+--+
                      |    |    |  |
          +-----------+    |    |  +------------+
          |                |    |               |
+---------v--+  +----------v-+  +-v----------+  +v-----------+
| PostgreSQL |  |   Redis    |  | RabbitMQ   |  |  Mailgun   |
| (Supabase) |  | (Cloud)    |  | (Docker)   |  |  (SMTP)    |
+------------+  +------------+  +------------+  +------------+
```

TLS is terminated at the Caddy reverse proxy; Caddy obtains and renews certificates from Let's Encrypt automatically (HTTP-01 challenge on port 80). The Spring Boot app speaks plain HTTP on the internal Docker network and trusts `X-Forwarded-*` headers from Caddy via `server.forward-headers-strategy: native`.

---

## Module Structure

```
Tracky-Api/
+-- app/                  # Main Spring Boot application, configs, entry point
+-- common/               # Shared utilities: JWT, RabbitMQ config, exceptions
+-- user/                 # User auth: registration, login, JWT, API keys, rate limiting
+-- project/              # Project & task CRUD operations
+-- notification/         # Email notifications via Thymeleaf + Mailgun
+-- build-logic/          # Gradle convention plugins
```

### Convention Plugins

| Plugin | Purpose |
|--------|---------|
| `tracky.kotlin-common` | Base Kotlin config, Spring dependency management, JUnit 5, JVM 25 |
| `tracky.spring-boot-service` | Extends kotlin-common with Spring Web, test starters |
| `tracky.spring-boot-app` | Extends service with Spring Boot plugin, JPA allOpen annotations |

---

## Dependencies

### Version Catalog (`gradle/libs.versions.toml`)

| Dependency | Version |
|------------|---------|
| Kotlin | 2.3.0 |
| Spring Boot | 4.0.5 |
| Spring Dependency Management | 1.1.7 |
| Jackson (Kotlin module) | 3.0.2 declared → **3.1.0 resolved** (upgraded by the Spring Boot BOM) |
| JJWT (JWT library) | 0.12.6 |
| SpringDoc OpenAPI | 2.8.6 |
| kotlinx-datetime | 0.6.1 |

Jackson 3 lives under the `tools.jackson.*` group; only the annotations stayed in `com.fasterxml.jackson.annotation`.

### By Module

Only dependencies declared in each module's own `build.gradle.kts` are listed. The `tracky.spring-boot-service` convention plugin additionally puts `kotlin-reflect`, `kotlin-stdlib` and `spring-boot-starter-web` on `implementation`, and `spring-boot-starter-test`, `kotlin-test-junit5`, `mockito-kotlin` + `junit-platform-launcher` on the test configurations of every module that applies it (`user`, `project`, `notification`, and `app` via `tracky.spring-boot-app`).

#### app

| Dependency | Scope |
|------------|-------|
| `spring-boot-starter-security` | implementation |
| `spring-boot-starter-mail` | implementation |
| `spring-boot-starter-amqp` | implementation |
| `spring-boot-starter-data-redis` | implementation |
| `spring-boot-starter-data-jpa` | implementation |
| `spring-boot-starter-actuator` | implementation |
| `springdoc-openapi-starter-webmvc-ui` | implementation |
| `postgresql` | runtimeOnly |
| `spring-security-test` | testImplementation |
| `h2` | testRuntimeOnly |
| Module: `common` | implementation |
| Module: `user` | implementation |
| Module: `project` | implementation |
| Module: `notification` | implementation |

#### common

| Dependency | Scope |
|------------|-------|
| `kotlin-reflect` | api |
| `jackson-module-kotlin` | api |
| `spring-boot-starter-amqp` | implementation |
| `spring-boot-starter-security` | implementation |
| `jjwt-api` | implementation |
| `jjwt-impl` | runtimeOnly |
| `jjwt-jackson` | runtimeOnly |
| `spring-boot-starter-test` | testImplementation |
| `kotlin-test-junit5` | testImplementation |
| `mockito-kotlin` (5.4.0) | testImplementation |
| `junit-platform-launcher` | testRuntimeOnly |

> `common` applies only `tracky.kotlin-common`, so unlike the other modules it declares its own test dependencies.

#### user

| Dependency | Scope |
|------------|-------|
| `spring-boot-starter-security` | implementation |
| `spring-boot-starter-validation` | implementation |
| `spring-boot-starter-data-redis` | implementation |
| `spring-boot-starter-data-jpa` | implementation |
| `springdoc-openapi-starter-webmvc-ui` | implementation |
| `jjwt-api` | implementation |
| `jjwt-impl` | runtimeOnly |
| `jjwt-jackson` | runtimeOnly |
| `postgresql` | runtimeOnly |
| `spring-security-test` | testImplementation |
| `h2` | testRuntimeOnly |
| Module: `common` | implementation |

#### project

| Dependency | Scope |
|------------|-------|
| `kotlinx-datetime` | implementation |
| `spring-boot-starter-security` | implementation |
| `spring-boot-starter-validation` | implementation |
| `spring-boot-starter-data-jpa` | implementation |
| `springdoc-openapi-starter-webmvc-ui` | implementation |
| `postgresql` | runtimeOnly |
| `spring-security-test` | testImplementation |
| `h2` | testRuntimeOnly |
| Module: `common` | implementation |

#### notification

| Dependency | Scope |
|------------|-------|
| `spring-boot-starter-mail` | implementation |
| `spring-boot-starter-thymeleaf` | implementation |
| `spring-boot-starter-amqp` | implementation |
| Module: `common` | implementation |

---

## External Services

### 1. PostgreSQL (Supabase)

| Property | Value |
|----------|-------|
| Provider | Supabase |
| Project Ref | `mfaxkwmwwxnpmuozryzd` |
| Direct Host | `db.mfaxkwmwwxnpmuozryzd.supabase.co` (IPv6 only) |
| Pooler Host | `aws-1-eu-central-1.pooler.supabase.com` |
| Pooler Port | `6543` |
| Database | `postgres` |
| Username | `postgres.mfaxkwmwwxnpmuozryzd` |
| Password | (see `SUPABASE_DB_PASSWORD` in `.env`; rotate via Supabase → Project Settings → Database) |
| JDBC URL | `jdbc:postgresql://aws-1-eu-central-1.pooler.supabase.com:6543/postgres?prepareThreshold=0` |

> **Note:** The `?prepareThreshold=0` parameter is required because Supabase uses pgbouncer in transaction mode, which does not support prepared statements.

> **Note:** The direct host resolves to IPv6 only. Use the pooler host for IPv4-only servers.

### 2. Redis (Redis Cloud)

| Property | Value |
|----------|-------|
| Provider | Redis Cloud (AWS eu-central-1) |
| Host | `redis-16042.c293.eu-central-1-1.ec2.cloud.redislabs.com` |
| Port | `16042` |
| Password | (see `REDIS_PASSWORD` in `.env`; rotate via Redis Cloud → Databases → Configuration) |
| Cache TTL | 10 minutes (`RedisConfig`, default cache config) |

Redis is used for two things:

- **IP rate limiting** on the auth endpoints (`IpRateLimiter`, atomic via a Lua script).
- **Keep-alive.** `RedisKeepAlive` writes a self-expiring `tracky:keepalive` key every 6 hours (`@Scheduled` cron, disabled under the `test` profile) so the managed Redis Cloud database registers activity and is not deleted for inactivity.

Caching itself is wired up (`@EnableCaching` with a 10-minute TTL) but **nothing is annotated `@Cacheable` yet**, so no cache entries are actually written today.

### 3. RabbitMQ (Docker container)

| Property | Value |
|----------|-------|
| Image | `rabbitmq:4-management-alpine` |
| AMQP Port | `5672` |
| Management UI Port | `15672` |
| Username | `tracky` |
| Password | (see `RABBITMQ_PASSWORD` in `.env`; consumed by `docker-compose.prod.yml`) |
| Management UI (prod) | Bound to `127.0.0.1:15672` only — **not** publicly reachable. Reach it through an SSH tunnel (see [Port matrix](#port-matrix-production)). |

### 4. Mailgun (SMTP)

| Property | Value |
|----------|-------|
| Region | EU |
| Host | `smtp.eu.mailgun.org` |
| Port | `587` (STARTTLS) |
| Sending Domain | `tracky.jv-coding-solutions.com` (verified — DKIM/SPF/MX) |
| Username | `mail-support@tracky.jv-coding-solutions.com` |
| Password | (per-domain SMTP password — only in `.env`; rotate via Mailgun → Sending → Domains → SMTP credentials) |
| From Address | `mail-support@tracky.jv-coding-solutions.com` |
| Status | Production (custom verified domain) |

> Note: the SMTP password is **not** the same as the Mailgun HTTP API key. Get/reset it from Mailgun → Sending → Domains → `tracky.jv-coding-solutions.com` → SMTP credentials.

---

## Credentials & Configuration

### Environment Variables (`.env`)

```env
# Database (Supabase)
SUPABASE_DB_PASSWORD=<Supabase database password>

# Redis (Redis Cloud)
REDIS_HOST=redis-16042.c293.eu-central-1-1.ec2.cloud.redislabs.com
REDIS_PORT=16042
REDIS_PASSWORD=<Redis Cloud database password>

# RabbitMQ (local container)
RABBITMQ_HOST=rabbitmq
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=tracky
RABBITMQ_PASSWORD=<RabbitMQ password>

# Mail (Mailgun SMTP - EU region, verified custom domain)
MAIL_HOST=smtp.eu.mailgun.org
MAIL_PORT=587
MAIL_USERNAME=mail-support@tracky.jv-coding-solutions.com
MAIL_PASSWORD=<per-domain SMTP password from Mailgun dashboard>
MAIL_FROM=mail-support@tracky.jv-coding-solutions.com

# JWT
JWT_SECRET=<base64-encoded HS256 signing key>

# App
APP_BASE_URL=https://tracky.jv-coding-solutions.com
SERVER_PORT=8080
```

### JWT Configuration

| Property | Value |
|----------|-------|
| Algorithm | HS256 |
| Secret | Base64-encoded (see `.env`) |
| Access Token Expiration | 15 minutes (900,000 ms) |
| Refresh Token Expiration | 30 days (2,592,000,000 ms) |

### Test User Account

| Property | Value |
|----------|-------|
| Email | `joerg.voye@gmail.com` |
| Username | `JoergVoye` |
| Password | (see your password manager — not stored in the repo) |
| User ID | `3010de5f-3239-4c7b-8ed2-f94d54c682cd` |

### Fixture Account

A separate account holding a fixed, known dataset for post-deploy verification. Kept distinct from the test user above so its data can be reset without touching anything real.

| Property | Value |
|----------|-------|
| Email | `joerg.voye+fixture@gmail.com` |
| Username | `TrackyFixture` |
| Password | see `FIXTURE_PASSWORD` in `.env` |
| User ID | `7be0792d-8618-4931-96d2-d44cb27834f9` |

```bash
python3 scripts/fixture.py seed     # reset the account and recreate the manifest
python3 scripts/fixture.py verify   # assert GET /api/projects matches exactly (exit 0/1)
```

Credentials come from `FIXTURE_EMAIL` / `FIXTURE_PASSWORD` in the environment, falling back to `.env`. Add `--base-url` to target a local instance instead of production.

The manifest is **4 projects / 6 tasks / 4 intervals** on fixed UUIDs (`f1…` projects, `f2…` tasks, `f3…` intervals), chosen to cover the cases that actually break this endpoint:

| Project | Tasks | Intervals | Covers |
|---------|-------|-----------|--------|
| Fixture Alpha | 3 | 2, 1, 0 | nested tree; one interval is **open** (`endDateTimeUtc: null`, task `isTimerRunning: true`) |
| Fixture Beta | 2 | 0, 0 | tasks with no intervals |
| Fixture Gamma | 0 | — | `"tasks": []`, not `null` |
| Fixture Delta | 1 | 1 | nullable fields null; `isArchived` + `isPinned` true |

Note that `isArchived`, `isPinned`, `isFinished`, `totalDuration`, `endDateTimeUtc` and `trashedAtUtc` are **not** accepted by `POST /api/projects` — they exist only on `UpdateProjectRequest`. The seed sets them with a follow-up `PUT`.

Design rationale: `docs/superpowers/specs/2026-08-13-fixture-account-design.md`.

### VPS Server

| Property | Value |
|----------|-------|
| Hostname | `tracky.jv-coding-solutions.com` |
| OS | Ubuntu |
| SSH User | `root` |
| App Directory | `/opt/tracky-api` |
| API URL | `https://tracky.jv-coding-solutions.com` |
| Swagger UI | `https://tracky.jv-coding-solutions.com/swagger-ui.html` |

---

## Database

### Schemas

| Schema | Module | Tables |
|--------|--------|--------|
| `user_service` | user | `users`, `api_keys`, `refresh_tokens`, `email_verification_tokens`, `password_reset_tokens` |
| `project_service` | project | `projects`, `project_tasks`, `task_intervals`, `project_subtasks`, `subtask_intervals` |

### Entities

#### `user_service.users`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | UUID | PK |
| `email` | String | NOT NULL, UNIQUE |
| `username` | String | NOT NULL, UNIQUE |
| `hashed_password` | String | NOT NULL |
| `has_verified_email` | Boolean | NOT NULL, default `false` |
| `created_at` | Instant | NOT NULL |

#### `user_service.api_keys`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | UUID | PK |
| `user_id` | UUID | NOT NULL |
| `hashed_key` | String | NOT NULL, UNIQUE |
| `name` | String | NOT NULL |
| `created_at` | Instant | NOT NULL |

#### `user_service.refresh_tokens`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | UUID | PK |
| `user_id` | UUID | NOT NULL |
| `hashed_token` | String | NOT NULL, UNIQUE |
| `expires_at` | Instant | NOT NULL |
| `created_at` | Instant | NOT NULL |

#### `user_service.email_verification_tokens`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | UUID | PK |
| `token` | String | NOT NULL, UNIQUE |
| `user_id` | UUID | FK -> users, NOT NULL |
| `expires_at` | Instant | NOT NULL |
| `used_at` | Instant | nullable |
| `created_at` | Instant | NOT NULL |

#### `user_service.password_reset_tokens`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | UUID | PK |
| `token` | String | NOT NULL, UNIQUE |
| `user_id` | UUID | FK -> users, NOT NULL |
| `expires_at` | Instant | NOT NULL |
| `used_at` | Instant | nullable |
| `created_at` | Instant | NOT NULL |

#### `project_service.projects`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `uuid` | PK — client-generated |
| `user_id` | `uuid` | NOT NULL |
| `title` | `varchar` | NOT NULL |
| `description` | `varchar` | nullable |
| `color` | `integer` | nullable (ARGB packed int) |
| `total_duration` | `bigint` | nullable |
| `start_date_time_utc` | `varchar` | NOT NULL — ISO-8601 instant |
| `is_finished` | `boolean` | NOT NULL |
| `use_light_text_color` | `boolean` | NOT NULL |
| `end_date_time_utc` | `varchar` | nullable — ISO-8601 instant |
| `is_archived` | `boolean` | NOT NULL, default `false` |
| `trashed_at_utc` | `varchar` | nullable — ISO-8601 instant; non-null means the project is in the trash |
| `is_pinned` | `boolean` | NOT NULL, default `false` |
| `sort_index` | `bigint` | nullable, default `NULL` — no unique constraint (duplicates between batches are legal) |
| `created_at_utc` | `varchar` | NOT NULL — ISO-8601 instant, not updatable |
| `updated_at_utc` | `varchar` | NOT NULL — ISO-8601 instant |

#### `project_service.project_tasks`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `uuid` | PK — client-generated |
| `parent_project_id` | `uuid` | NOT NULL, FK → `project_service.projects(id)` `ON DELETE CASCADE` |
| `title` | `varchar` | NOT NULL |
| `description` | `varchar` | nullable |
| `duration_millis` | `bigint` | nullable |
| `start_date_time_utc` | `varchar` | NOT NULL — ISO-8601 instant |
| `end_date_time_utc` | `varchar` | nullable — ISO-8601 instant |
| `is_finished` | `boolean` | NOT NULL |
| `is_timer_running` | `boolean` | NOT NULL |
| `created_at_utc` | `varchar` | NOT NULL — ISO-8601 instant, not updatable |
| `updated_at_utc` | `varchar` | NOT NULL — ISO-8601 instant |

#### `project_service.task_intervals`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `uuid` | PK — client-generated |
| `parent_task_id` | `uuid` | NOT NULL, FK → `project_service.project_tasks(id)` `ON DELETE CASCADE` |
| `start_date_time_utc` | `varchar` | NOT NULL — ISO-8601 instant |
| `end_date_time_utc` | `varchar` | nullable — `NULL` while the interval is open (timer running) |
| `duration_millis` | `bigint` | NOT NULL |
| `created_at_utc` | `varchar` | NOT NULL — ISO-8601 instant, not updatable |
| `updated_at_utc` | `varchar` | NOT NULL — ISO-8601 instant |

#### `project_service.project_subtasks`

An optional fourth level: a subtask always belongs to a parent task, and to that task's project.

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `uuid` | PK — client-generated |
| `parent_task_id` | `uuid` | NOT NULL, FK → `project_service.project_tasks(id)` `ON DELETE CASCADE` |
| `parent_project_id` | `uuid` | NOT NULL, FK → `project_service.projects(id)` `ON DELETE CASCADE` |
| `title` | `varchar` | NOT NULL |
| `description` | `varchar` | nullable |
| `duration_millis` | `bigint` | nullable |
| `start_date_time_utc` | `varchar` | NOT NULL — ISO-8601 instant |
| `end_date_time_utc` | `varchar` | nullable — ISO-8601 instant |
| `is_finished` | `boolean` | NOT NULL, default `false` |
| `is_timer_running` | `boolean` | NOT NULL, default `false` |
| `created_at_utc` | `varchar` | NOT NULL — ISO-8601 instant, not updatable |
| `updated_at_utc` | `varchar` | NOT NULL — ISO-8601 instant |

`parent_task_id` is the **owning** association — it is the one `ProjectTaskEntity.subTasks` maps to
and the one the JPA cascade travels down. `parent_project_id` is a denormalised shortcut, always the
parent task's own project; `ProjectSubTaskService.create` reads it off the task rather than the
request so the two can never disagree.

#### `project_service.subtask_intervals`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `uuid` | PK — client-generated |
| `parent_subtask_id` | `uuid` | NOT NULL, FK → `project_service.project_subtasks(id)` `ON DELETE CASCADE` |
| `parent_task_interval_id` | `uuid` | NOT NULL, FK → `project_service.task_intervals(id)` `ON DELETE CASCADE` |
| `start_date_time_utc` | `varchar` | NOT NULL — ISO-8601 instant |
| `end_date_time_utc` | `varchar` | nullable — `NULL` while the interval is open (timer running) |
| `duration_millis` | `bigint` | NOT NULL, default `0` |
| `created_at_utc` | `varchar` | NOT NULL — ISO-8601 instant, not updatable |
| `updated_at_utc` | `varchar` | NOT NULL — ISO-8601 instant |

A subtask interval has **two** parents. `parent_subtask_id` is the one it is addressed through;
`parent_task_interval_id` records the task interval it is nested inside.

On the client a subtask timer never runs on its own — starting a subtask also starts its parent
task's timer, opening a task interval if one is not already running — so every subtask interval sits
inside exactly one task interval, always one belonging to the subtask's own task. The client's Room
schema encodes that as a `NOT NULL` cascading FK, which means a client rehydrating from
`GET /api/projects` cannot insert the row unless the server names the parent. Timestamp containment
is not a usable substitute: it is guesswork the moment two devices disagree on the clock, and it has
no answer at all for an interval whose enclosing task interval was pruned or edited. Added
2026-08-23 (`scripts/migrations/2026-08-23-subtask-interval-parent.sql`).

#### Notes on `project_service`

- **Timestamps are ISO-8601 `varchar`, not SQL `timestamp`.** The mobile client is the source of truth for them and the server persists them verbatim.
- **Booleans and `duration_millis` have no database-level default** except `projects.is_archived` and `projects.is_pinned` (both `default false`). Every other NOT NULL column relies on the Kotlin entity's default value, so rows inserted outside the application must supply them.
- **Every foreign key is `ON DELETE CASCADE`.** Deleting a project takes its tasks, their intervals, their subtasks and those subtasks' intervals with it — in the API through JPA (`cascade = [CascadeType.ALL], orphanRemoval = true` on the entity) and in raw SQL through the database. The two paths are deliberately redundant; the database one was retrofitted on 2026-08-23 (see below).
- **Every foreign-key column is indexed.** `idx_project_tasks_parent_project`, `idx_task_intervals_parent_task`, `idx_project_subtasks_parent_task`, `idx_project_subtasks_parent_project`, `idx_subtask_intervals_parent_subtask`, `idx_subtask_intervals_parent_task_interval`. Child lookups and the parent-side scan a cascading delete performs both use these.
- The schema is created and evolved by Hibernate `ddl-auto: update` — there is no Flyway/Liquibase migration history. Columns are added when an entity field appears; they are never dropped or altered.
- **Anything `ddl-auto: update` cannot do lives in `scripts/migrations/`,** hand-run against the database *before* the JAR that needs it is deployed. `update` cannot add a `NOT NULL` column to a populated table and never alters an existing foreign key, which is why `project_tasks.title` and the `ON DELETE CASCADE` retrofit are in `scripts/migrations/2026-08-23-subtasks.sql`. The two new tables are created there too, so Hibernate finds them already present and leaves them alone. `scripts/migrations/2026-08-23-subtask-interval-parent.sql` adds `subtask_intervals.parent_task_interval_id` the same way, and must be applied after it.

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
| GET | `/api/projects` | Bearer / API Key | List all projects (full tree: tasks, intervals, subtasks, subtask intervals) |
| POST | `/api/projects` | Bearer / API Key | Create a new project |
| GET | `/api/projects/{id}` | Bearer / API Key | Get project by ID (full tree) |
| PUT | `/api/projects/sort` | Bearer / API Key | Batch-update the manual sort order (204, no body) |
| PUT | `/api/projects/{id}` | Bearer / API Key | Update a project |
| DELETE | `/api/projects/{id}` | Bearer / API Key | Delete project and its whole tree (tasks, intervals, subtasks, subtask intervals) |

### Project Tasks (`/api/projects/{projectId}/tasks`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/projects/{projectId}/tasks` | Bearer / API Key | List all tasks |
| POST | `/api/projects/{projectId}/tasks` | Bearer / API Key | Create a task |
| GET | `/api/projects/{projectId}/tasks/{id}` | Bearer / API Key | Get task by ID |
| PUT | `/api/projects/{projectId}/tasks/{id}` | Bearer / API Key | Update a task |
| DELETE | `/api/projects/{projectId}/tasks/{id}` | Bearer / API Key | Delete a task, its intervals and its subtasks |

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

### Project SubTasks (`/api/projects/{projectId}/tasks/{taskId}/subtasks`)

Subtasks are optional. A task with none returns `"subTasks": []`.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/projects/{projectId}/tasks/{taskId}/subtasks` | Bearer / API Key | List all subtasks of a task |
| POST | `/api/projects/{projectId}/tasks/{taskId}/subtasks` | Bearer / API Key | Create a subtask |
| GET | `/api/projects/{projectId}/tasks/{taskId}/subtasks/{id}` | Bearer / API Key | Get subtask by ID |
| PUT | `/api/projects/{projectId}/tasks/{taskId}/subtasks/{id}` | Bearer / API Key | Update a subtask |
| DELETE | `/api/projects/{projectId}/tasks/{taskId}/subtasks/{id}` | Bearer / API Key | Delete a subtask and all its intervals |

### SubTask Intervals (`/api/projects/{projectId}/tasks/{taskId}/subtasks/{subTaskId}/intervals`)

The same seven operations `Task Intervals` offers, one level down.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `.../subtasks/{subTaskId}/intervals` | Bearer / API Key | List all intervals for a subtask |
| POST | `.../subtasks/{subTaskId}/intervals` | Bearer / API Key | Create an interval |
| GET | `.../subtasks/{subTaskId}/intervals/open` | Bearer / API Key | Get the currently open (unfinished) interval, or 204 if none |
| GET | `.../subtasks/{subTaskId}/intervals/{id}` | Bearer / API Key | Get interval by ID |
| PUT | `.../subtasks/{subTaskId}/intervals/{id}` | Bearer / API Key | Update an interval |
| DELETE | `.../subtasks/{subTaskId}/intervals/{id}` | Bearer / API Key | Delete an interval |
| DELETE | `.../subtasks/{subTaskId}/intervals` | Bearer / API Key | Delete all intervals for a subtask |

(Paths are abbreviated; each is prefixed with `/api/projects/{projectId}/tasks/{taskId}`.)

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

Returns **the full tree** — every project the caller owns, each with its complete `tasks` array, each task with its complete `intervals` and `subTasks` arrays, and each subtask with its own `intervals`. This is the same shape `GET /api/projects/{id}` returns, for the whole collection, so one call is enough to hydrate the client's offline store; the per-project `/tasks`, per-task `/subtasks` and per-parent `/intervals` endpoints remain available for targeted reads.

Empty collections always serialise as `[]`, never `null`: a project with no tasks gets `"tasks": []`, a task with no intervals gets `"intervals": []`, a task with no subtasks gets `"subTasks": []`.

Each of the four levels is fetched with one batched query (`@BatchSize(100)` on every association), so the whole tree costs five statements regardless of how many rows it holds — asserted in `ProjectServiceTreeIntegrationTest`.

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
        "title": "Work task",
        "description": "Quarterly report",
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
        ],
        "subTasks": [
          {
            "id": "3d90b1ac-51f7-4a02-9e64-1c7b2f0e5d48",
            "parentTaskId": "fe316e35-bd3f-4c6f-9d7d-23d6b6e8877e",
            "title": "Draft the outline",
            "description": null,
            "durationMillis": 600000,
            "startDateTimeUtc": "2026-03-28T15:16:40Z",
            "endDateTimeUtc": "2026-03-28T15:26:40Z",
            "isFinished": true,
            "isTimerRunning": false,
            "updatedAtUtc": "2026-03-28T15:26:40.771000000Z",
            "intervals": [
              {
                "id": "7a41e0c9-2b8d-4f31-8c05-9e6a3d1f4b72",
                "parentSubTaskId": "3d90b1ac-51f7-4a02-9e64-1c7b2f0e5d48",
                "parentTaskIntervalId": "9c1f0b52-6a4e-4f0d-9d16-2b5b0f8c9a31",
                "startDateTimeUtc": "2026-03-28T15:16:40Z",
                "endDateTimeUtc": "2026-03-28T15:26:40Z",
                "durationMillis": 600000,
                "updatedAtUtc": "2026-03-28T15:26:40.771000000Z"
              }
            ]
          }
        ]
      }
    ]
  }
]
```

(Scalar project fields are elided above for brevity — the response carries the same field set as the create-project response shown earlier, plus `tasks`.)

Result order is **not** guaranteed: neither the projects nor the nested tasks, subtasks and intervals are sorted server-side. In particular `sortIndex` is stored and echoed verbatim but never ordered by — the client applies its own ordering. See the `sortIndex` note above.

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
  -d '{"id":"fe316e35-bd3f-4c6f-9d7d-23d6b6e8877e","title":"Work task","description":"Quarterly report","durationMillis":3600000,"startDateTimeUtc":"2026-03-28T15:16:40Z","isTimerRunning":true}'
```

```json
{
  "id": "fe316e35-bd3f-4c6f-9d7d-23d6b6e8877e",
  "title": "Work task",
  "description": "Quarterly report",
  "durationMillis": 3600000,
  "startDateTimeUtc": "2026-03-28T15:16:40Z",
  "endDateTimeUtc": null,
  "isFinished": false,
  "isTimerRunning": true,
  "updatedAtUtc": "2026-03-28T15:16:40.845724101Z",
  "intervals": [],
  "subTasks": []
}
```

`id` is a client-generated UUID. `id`, `title` and `startDateTimeUtc` are required — **`title` must be present and non-blank**; see the breaking-change note below. `description` and `durationMillis` are both optional and nullable. `intervals` and `subTasks` are empty until children are created via their own endpoints.

> **Breaking change (API 1.6.0).** `title` was added to `CreateProjectTaskRequest` and
> `UpdateProjectTaskRequest` as a required `@NotBlank` field. A client that omits it now gets
> `400 Bad Request`. Rows that predate the change were backfilled from `description`, falling back
> to `'Untitled task'` — see `scripts/migrations/2026-08-23-subtasks.sql`.

#### Create SubTask

```bash
curl -X POST https://tracky.jv-coding-solutions.com/api/projects/{projectId}/tasks/{taskId}/subtasks \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{"id":"3d90b1ac-51f7-4a02-9e64-1c7b2f0e5d48","title":"Draft the outline","startDateTimeUtc":"2026-03-28T15:16:40Z"}'
```

```json
{
  "id": "3d90b1ac-51f7-4a02-9e64-1c7b2f0e5d48",
  "parentTaskId": "fe316e35-bd3f-4c6f-9d7d-23d6b6e8877e",
  "title": "Draft the outline",
  "description": null,
  "durationMillis": null,
  "startDateTimeUtc": "2026-03-28T15:16:40Z",
  "endDateTimeUtc": null,
  "isFinished": false,
  "isTimerRunning": false,
  "updatedAtUtc": "2026-03-28T15:16:40.845724101Z",
  "intervals": []
}
```

Same contract as a task: `id`, `title` and `startDateTimeUtc` are required, everything else is
optional. The parent project is derived from the parent task, so it is never sent in the body.
Deleting the parent task — or the project above it — deletes the subtask and its intervals.

#### Create SubTask Interval

```bash
curl -X POST https://tracky.jv-coding-solutions.com/api/projects/{projectId}/tasks/{taskId}/subtasks/{subTaskId}/intervals \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{"id":"7a41e0c9-2b8d-4f31-8c05-9e6a3d1f4b72","parentTaskIntervalId":"9c1f0b52-6a4e-4f0d-9d16-2b5b0f8c9a31","startDateTimeUtc":"2026-03-28T15:16:40Z"}'
```

```json
{
  "id": "7a41e0c9-2b8d-4f31-8c05-9e6a3d1f4b72",
  "parentSubTaskId": "3d90b1ac-51f7-4a02-9e64-1c7b2f0e5d48",
  "parentTaskIntervalId": "9c1f0b52-6a4e-4f0d-9d16-2b5b0f8c9a31",
  "startDateTimeUtc": "2026-03-28T15:16:40Z",
  "endDateTimeUtc": null,
  "durationMillis": 0,
  "updatedAtUtc": "2026-03-28T15:16:40.694098965Z"
}
```

Otherwise identical to a task interval, including `GET .../intervals/open` returning 204 No Content
when nothing is running.

**`parentTaskIntervalId` is required** and must name a task interval of the *same* task as the
subtask's parent — see the schema notes above for why the server records the nesting rather than
letting each client re-derive it. It is returned everywhere a subtask interval is serialised,
including nested inside `GET /api/projects` and `GET /api/projects/{id}`.

| Status | When |
|--------|------|
| `400` `VALIDATION_ERROR` | `parentTaskIntervalId` is missing |
| `400` `BAD_REQUEST` | it names a task interval belonging to a different task than the subtask's parent |
| `404` `NOT_FOUND` | no task interval with that id is owned by the caller |
| `409` `DUPLICATE_RESOURCE` | an interval with that `id` already exists |

This is the only `404` in the API. Everywhere else a missing-or-not-yours resource is `403` so the
response never confirms that someone else's id exists; here the `400` branch is only reachable for a
row the caller already owns, so separating the two leaks nothing.

`parentTaskIntervalId` is **immutable on `PUT`**. Re-nesting is not a client operation — an update
only ever closes a running interval by filling in `endDateTimeUtc` and `durationMillis`. Omit the
field, or echo the stored value back; a different value is rejected with `400`.

Deleting a task interval deletes the subtask intervals nested inside it, on both sides of the wire —
through JPA in the API and through `ON DELETE CASCADE` in raw SQL.

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

---

## Deployment

### Prerequisites

- JDK 25 locally (the JAR is built on the dev machine, not on the VPS)
- Docker and Docker Compose installed on the VPS
- SSH access to `root@tracky.jv-coding-solutions.com`
- `.env` file configured in `/opt/tracky-api/`
- Existing `tracky-api` Docker image on the VPS (created once via the legacy full-rebuild path below)

### Standard redeploy (`scripts/deploy.sh`)

```bash
./scripts/deploy.sh                # test, build, ship, restart, verify
./scripts/deploy.sh --skip-tests   # skip the local test run
./scripts/deploy.sh --skip-verify  # skip the post-deploy fixture check
```

Run it from the repo root **on your dev machine**, not on the VPS. It performs the JAR-only flow below and then gates on the fixture check, so a deploy that breaks the API contract exits non-zero instead of passing silently:

1. `./gradlew test`, then `:app:bootJar`
2. Snapshot the current remote JAR to `app.jar.prev` (rollback point)
3. `rsync` the new JAR up and **compare SHA-256** on both ends
4. `docker compose restart tracky-api`
5. Poll `/actuator/health` until 200 (up to ~2.5 min)
6. `python3 scripts/fixture.py verify` — exact assertion against the fixture account

On failure it prints the rollback command:

```bash
ssh root@tracky.jv-coding-solutions.com \
  'cp /opt/tracky-api/app.jar.prev /opt/tracky-api/app.jar && \
   docker compose -f /opt/tracky-api/docker-compose.prod.yml restart tracky-api'
```

Overridable via `VPS`, `REMOTE_DIR`, `BASE_URL` environment variables.

#### The underlying manual flow

`docker-compose.prod.yml` bind-mounts `./app.jar` → `/app/app.jar:ro` into the container, so an ordinary code change ships as just the JAR. No in-container Gradle build.

```bash
# From local machine
./gradlew :app:bootJar
cp app/build/libs/app-0.0.1-SNAPSHOT.jar app.jar
rsync -avz app.jar root@tracky.jv-coding-solutions.com:/opt/tracky-api/

# Restart picks up the new JAR (~5 seconds)
ssh root@tracky.jv-coding-solutions.com 'docker compose -f /opt/tracky-api/docker-compose.prod.yml restart tracky-api'
```

Use `up -d tracky-api` (recreate the container) instead of `restart` when you've also changed the `.env` file or `docker-compose.prod.yml` itself — `restart` does not re-read those.

Verify:

```bash
curl https://tracky.jv-coding-solutions.com/actuator/health           # expect {"status":"UP"}
curl -s https://tracky.jv-coding-solutions.com/v3/api-docs | jq '.info.version'
ssh root@tracky.jv-coding-solutions.com 'docker compose -f /opt/tracky-api/docker-compose.prod.yml logs --tail=50 tracky-api'
```

### Legacy / first-time full rebuild

Use this only on a fresh VPS, or when the build context outside the JAR has changed (Dockerfile, base image upgrade, build tooling). It runs Gradle inside the build container — heavy on VPS CPU/memory. **A previous attempt at this OOM'd the VPS and required a power-cycle**, so prefer the JAR-only path above for everyday code changes.

```bash
# From local machine — full source sync
rsync -avz --exclude '.gradle' --exclude 'build' --exclude '.idea' --exclude '.env' \
  /Users/jvoye/ClaudeCodeProjects/Tracky-Api/ \
  root@tracky.jv-coding-solutions.com:/opt/tracky-api/

# On VPS — multi-stage Docker build
ssh root@tracky.jv-coding-solutions.com 'cd /opt/tracky-api && \
  docker compose -f docker-compose.prod.yml down && \
  docker compose -f docker-compose.prod.yml up -d --build'
```

### Docker Setup

- **Dockerfile:** Multi-stage build (JDK 25 for build, JRE 25 for runtime). The runtime stage's `/app/app.jar` is bind-mount-overridden by the host's `./app.jar` in production.
- **docker-compose.prod.yml:** Runs `tracky-api` + `rabbitmq` + `caddy` containers; bind-mounts `./app.jar:/app/app.jar:ro` and `./Caddyfile:/etc/caddy/Caddyfile:ro`.
- **Caddyfile:** Reverse-proxy config at the repo root. Terminates TLS for `tracky.jv-coding-solutions.com` and proxies to `tracky-api:8080` over the internal Docker network. Auto-renews Let's Encrypt certs (no cron required).

#### Port matrix (production)

| Port | Public | Bound by | Purpose |
|---|---|---|---|
| 80 | Yes | Caddy | HTTP → HTTPS redirect + ACME HTTP-01 challenge |
| 443 | Yes | Caddy | HTTPS (TCP + UDP for HTTP/3) |
| 8080 | No (Docker `expose` only) | tracky-api | Internal Spring Boot port; reachable only via the Docker network from Caddy |
| 5672 | No (Docker `expose` only) | rabbitmq | AMQP; reachable only on the Docker network from tracky-api |
| 15672 | No (loopback only — `127.0.0.1`) | rabbitmq | RabbitMQ management UI; reach via SSH tunnel (see below) |

#### Reaching the RabbitMQ management UI

It's no longer publicly bound. To open it locally, set up an SSH local-forward:

```bash
ssh -L 15672:127.0.0.1:15672 root@tracky.jv-coding-solutions.com
```

Then in your browser go to `http://127.0.0.1:15672/` and log in with `${RABBITMQ_USERNAME}` / `${RABBITMQ_PASSWORD}` from `/opt/tracky-api/.env`.
- Redis and PostgreSQL are external services (not in Docker).
- RabbitMQ has a health check; tracky-api waits for it before starting.
- JVM flag `-Djava.net.preferIPv4Stack=true` is set in the entrypoint.

### Updating environment variables

`.env` is intentionally **not** rsynced (excluded above) so prod credentials never get clobbered by local values. Update the prod env directly on the VPS:

```bash
ssh root@tracky.jv-coding-solutions.com 'nano /opt/tracky-api/.env'
ssh root@tracky.jv-coding-solutions.com 'docker compose -f /opt/tracky-api/docker-compose.prod.yml up -d tracky-api'
```

### Database Schemas

The schemas must be created manually before the first deployment:

```sql
CREATE SCHEMA IF NOT EXISTS user_service;
CREATE SCHEMA IF NOT EXISTS project_service;
```

Via psql:

```bash
PGPASSWORD=$(grep '^SUPABASE_DB_PASSWORD=' .env | cut -d= -f2-) psql \
  -h aws-1-eu-central-1.pooler.supabase.com \
  -p 6543 \
  -U postgres.mfaxkwmwwxnpmuozryzd \
  -d postgres \
  -c "CREATE SCHEMA IF NOT EXISTS user_service; CREATE SCHEMA IF NOT EXISTS project_service;"
```

Hibernate `ddl-auto: update` handles most table creation and column additions automatically.

#### Hand-run migrations

`ddl-auto: update` **cannot** add a `NOT NULL` column to a populated table and **never** alters an
existing foreign key or index. Anything in those categories lives in `scripts/migrations/` and must
be applied **before** the JAR that depends on it is deployed:

```bash
PGPASSWORD=$(grep '^SUPABASE_DB_PASSWORD=' .env | cut -d= -f2-) psql \
  -h aws-1-eu-central-1.pooler.supabase.com \
  -p 6543 \
  -U postgres.mfaxkwmwwxnpmuozryzd \
  -d postgres \
  -f scripts/migrations/2026-08-23-subtasks.sql
```

The Supabase SQL Editor works just as well — the scripts are plain SQL and re-runnable. Each one
ends with verification queries; check their output before deploying.

Apply them in the order listed — later ones alter tables earlier ones create.

| Migration | What it does |
|-----------|--------------|
| `2026-08-23-subtasks.sql` | Adds `project_tasks.title` (backfilled from `description`), creates `project_subtasks` and `subtask_intervals`, retrofits `ON DELETE CASCADE` onto the pre-existing foreign keys, indexes every FK column |
| `2026-08-23-subtask-interval-parent.sql` | Adds `subtask_intervals.parent_task_interval_id` `NOT NULL` with a cascading FK onto `task_intervals`, and indexes it. Backfills by start-time containment within the parent task's intervals if the table is not empty, and aborts rather than guess for any row whose task has no intervals |

---

## Local Development

### Prerequisites

- JDK 25
- Docker (for Redis and RabbitMQ)

### Start Infrastructure

```bash
docker compose up -d
```

This starts Redis (port 6379) and RabbitMQ (ports 5672, 15672) for local development.

### Run the Application

```bash
./gradlew :app:bootRun
```

The app starts at `http://localhost:8080` with default development values from `application.yml`.

### Swagger UI

```
http://localhost:8080/swagger-ui.html
```

### Run Tests

```bash
./gradlew test                                        # every module
./gradlew :project:test                               # one module
./gradlew :project:test --tests '*ProjectServiceTest' # one class
```

The suite is JUnit 5 + Mockito (`mockito-kotlin`), in three shapes:

- **Service unit tests** — the bulk of it. Mocked repositories, no Spring context.
- **Standalone-MockMvc web-layer tests** (`ProjectControllerSortTest`, `SubTaskIntervalControllerTest`) — no context and no database, but they pin the HTTP contract: status codes, routing, JSON binding. `requestUserId` casts the security principal to a `UUID`, so `@WithMockUser` cannot be used; place a `UsernamePasswordAuthenticationToken(userId, null, emptyList())` into the `SecurityContextHolder` by hand. `@Valid` only produces 400s if you also call `.setValidator(LocalValidatorFactoryBean())`, and exception-to-status mapping only applies if you register `CommonExceptionHandler` via `.setControllerAdvice(...)` — which is why that advice lives in `:common` where every module can reach it.
- **One `@DataJpaTest` against H2** (`ProjectServiceTreeIntegrationTest`) — real persistence for the `GET /api/projects` tree. It asserts the `@BatchSize` query count with Hibernate statistics and the cascade behaviour at all four levels, neither of which a mocked repository exercises. `@Transactional(propagation = NOT_SUPPORTED)` strips the ambient transaction `@DataJpaTest` would otherwise add, reproducing production's `open-in-view: false`; without it the test would pass either way. `@DataJpaTest` needs a `@SpringBootConfiguration`, and the real one is in `:app`, so `ProjectModuleTestApplication` stands in for it.

`app/src/test/resources/application-test.yml` configures H2, but nothing activates the `test` profile, so it is currently unused.

H2 will not create the `user_service` / `project_service` schemas on its own — the JDBC URL has to do it, as `ProjectServiceTreeIntegrationTest` does. If you add the first `@SpringBootTest`, note that it has to live in `:app` (the only module that sees `SecurityConfig` and every other module), and its URL needs `INIT=CREATE SCHEMA IF NOT EXISTS user_service\;CREATE SCHEMA IF NOT EXISTS project_service`.
