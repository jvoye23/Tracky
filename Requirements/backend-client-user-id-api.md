# Backend prompt — Client-generated user id and pre-registration entitlements

Paste the block below into a session opened on the Tracky backend repo.

---

Tracky's client now generates the user id itself, the same way it already generates project, task
and interval ids. It uses that id as the RevenueCat `app_user_id` from the very first launch, long
before an account exists. Please accept the client's id at registration, keep RevenueCat events
for ids that have no account yet, and attach them to the account when it is created — plus a
migration and tests.

Depends on `Requirements/backend-pro-entitlement-api.md`. **If that prompt has not been implemented
yet, implement it together with this one.** §3–§5 below replace its rule that an unknown
`app_user_id` is dropped.

## Context

The product flow is **pay first, register later**. A new user can use the free tier, which is
purely local, for days or months. They buy Pro inside the app through RevenueCat, and only *then*
create an account so that sync can start. Some users buy during onboarding; others buy months
after installing.

Today two things break that flow:

1. **The server generates the user id at `/api/auth/register`.** The client therefore has no id to
   give RevenueCat at purchase time. RevenueCat falls back to an anonymous id and aliases it to the
   real one later, so every customer ends up with two ids.
2. **The webhook drops events for an `app_user_id` that matches no user**
   (`backend-pro-entitlement-api.md` §3). Every purchase made before registration — which is
   *every* purchase in this flow — is lost on the server. The new account starts as `free`, and the
   client's first `POST /api/sync/import` gets `402` until the next renewal months later.

The fix is one id for both systems:

- The client generates a UUID v4 on first launch and stores it.
- It configures RevenueCat with that UUID, so purchases land on it directly.
- It sends the same UUID when it registers.

The server's job is to accept the client's id, remember what RevenueCat said about an id before it
became an account, and hand that state to the account when it is created.

**The id is not a credential.** Knowing a UUID must never grant access to an existing account.
Everything below only ever *creates* an account with the id or *attaches* pending purchase state to
a newly created one. It never logs anyone in.

## 1. `POST /api/auth/register` — accept the client's id

```json
{
  "id": "6f1c2a7e-4b8d-4f0a-9c3e-2d5b7a1e8f40",
  "email": "user@example.com",
  "username": "myuser",
  "password": "MyPass@123"
}
```

The response is unchanged, and `user.id` echoes the id that was sent:

```json
{
  "user": {
    "id": "6f1c2a7e-4b8d-4f0a-9c3e-2d5b7a1e8f40",
    "email": "user@example.com",
    "username": "myuser",
    "hasVerifiedEmail": false
  },
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "a94d2949-ec74-40db-b171-e65e0fde91f4"
}
```

**Behaviour:**

- **`id` is required.** Persist it verbatim as `users.id`; the column is already a UUID primary key,
  so no schema change is needed. Do not fall back to generating one: a missing id means an old
  client or a bug, and a server-generated id would silently split the user from their purchases.
- **Validate the format.** It must be a canonical lowercase UUID string. Accept any version — the
  client sends v4 — but reject anything that does not parse.
- **A taken id is `409`** with `{"code":"DUPLICATE_RESOURCE"}`, the same code project creation uses.
  This can only mean the same device registered before, for example a sign-up that was never
  email-verified. The client will send the user to log in. **Never** merge into the existing row,
  overwrite it, or log the caller in as it.
- Keep the existing rules unchanged: duplicate email and username, password policy, the verification
  email, rate limits. When both the id and the email are taken, return the existing email error.
  That is the one the user can act on.
- Create the user and claim pending entitlement (§4) **in one transaction**.

| Status | When |
|---|---|
| `201` / `200` | created — keep whatever the endpoint returns today |
| `400` | `id` missing or not a UUID, plus the existing validation errors |
| `409` | `id` already belongs to a user (`DUPLICATE_RESOURCE`), or the existing email/username conflicts |

## 2. `POST /api/auth/google` and `POST /api/auth/apple` — optional id on sign-up

```json
{ "idToken": "eyJ...", "id": "6f1c2a7e-4b8d-4f0a-9c3e-2d5b7a1e8f40" }
```

**Behaviour:**

- `id` is **optional** and **only used when this call creates a new user**. Validate it as in §1,
  use it as `users.id`, and claim pending entitlement (§4) in the same transaction.
- **When the social identity already belongs to a user, ignore `id` completely.** Do not validate
  it, do not claim anything with it, and log the user in as their existing account. The client
  handles the switch on its side.
- When `id` is absent on sign-up, generate one as today. Older clients keep working; they simply
  get no pre-registration entitlement.
- On sign-up with an `id` that is already taken, return `409` `DUPLICATE_RESOURCE`, as in §1.

| Status | When |
|---|---|
| `200` | logged in (existing user) or created (new user) |
| `400` | `id` present but not a UUID, on a sign-up |
| `401` | invalid `idToken`, as today |
| `409` | sign-up with an `id` that already belongs to a user |

## 3. Pending entitlements — webhook amendment

New table:

```
pending_entitlements
  app_user_id             uuid primary key
  entitlement             not null          -- free | pro
  entitlement_expires_at  null
  last_event_timestamp_ms bigint not null
  updated_at              not null
```

**Behaviour.** This changes `POST /api/webhooks/revenuecat` from `backend-pro-entitlement-api.md`
§3:

- **An event whose `app_user_id` matches no user is no longer dropped.** Apply it to
  `pending_entitlements` using **exactly** the same event mapping, idempotency on event id, and
  `event_timestamp_ms` ordering as for a user. Factor the mapping into one function that both
  targets call. Two copies of the mapping are how the pending path ends up granting Pro on a
  `CANCELLATION`.
- Still answer `200`. The secret check, the `401` and the idempotency rules are unchanged.
- **Ignore `app_user_id` values that are not UUIDs** (for example RevenueCat's `$RCAnonymousID:…`).
  Answer `200` and log. These come from clients older than this change. They are not a user id, and
  storing them would only grow the table.
- **Also look at `aliases`.** RevenueCat sends every id the customer is known by. If any of them
  belongs to a user, apply the event to that user rather than creating a pending row.
- **Retention.** Pending rows for free-tier users never get claimed if the user never registers.
  Delete rows whose `entitlement_expires_at` passed more than 12 months ago. That matches the
  data-retention period for lapsed users. Run it as a cleanup job; it is a delete of dead rows,
  not an entitlement change.

## 4. Claim on account creation

When §1 or §2 creates a user with id `X`:

1. If a `pending_entitlements` row for `X` exists, copy `entitlement` and `entitlement_expires_at`
   onto the user, set `entitlement_source = 'revenuecat'`, and delete the row. Do this in the same
   transaction as the insert.
2. **If there is no row, ask RevenueCat directly** with its REST API v2:

   ```
   GET https://api.revenuecat.com/v2/projects/{REVENUECAT_PROJECT_ID}/customers/{X}
   Authorization: Bearer {REVENUECAT_SECRET_API_KEY}
   ```

   Read three values from configuration (environment variables on the VPS), never from the repo or
   from the client:

   | Variable | Value |
   |---|---|
   | `REVENUECAT_SECRET_API_KEY` | A **v2 secret key with read-only permission `customer_information:customers:read`** and nothing else. The key can only read; it can grant, refund or change nothing |
   | `REVENUECAT_PROJECT_ID` | `projc4a0bfac` |
   | `REVENUECAT_PRO_ENTITLEMENT_ID` | `entl8705419ed4`. In v2 the response identifies entitlements by this internal id, **not** by the identifier `project_tracker_tracky_pro` |

   **Behaviour:**

   - URL-encode `X` in the path.
   - Look in `active_entitlements.items` for an item whose `entitlement_id` equals
     `REVENUECAT_PRO_ENTITLEMENT_ID`.
     - If one is present and its `expires_at` is null or in the future, set the user to `pro`.
       `expires_at` is **milliseconds since the epoch**; null means lifetime. Use it as
       `entitlement_expires_at` and set `entitlement_source = 'revenuecat'`.
     - If none is present, the user is `free`.
   - **`404` means RevenueCat has never seen this id**, which is the normal case for a user who
     never bought. Treat it as `free` and do not log it as an error.
   - This covers the race this flow makes common: the user buys and registers within seconds, and
     the webhook has not arrived yet.
   - **A RevenueCat failure must not fail the registration.** That includes a timeout, a `401` or
     `403` (a missing or wrongly scoped key), a `429` or a `5xx`. Create the user as `free`, log it
     at warning level, and let the webhook catch up. Use a short timeout of about 3 seconds, and do
     not retry inside the request.
   - Never log the key or the `Authorization` header.
3. The register and social responses carry the resulting entitlement block, as
   `backend-pro-entitlement-api.md` §1 already requires. The client can then show Pro immediately.
4. A grandfathered flag never comes from a pending row. Grandfathering stays a migration-only
   decision.

## 5. `TRANSFER` events

RevenueCat sends `TRANSFER` when a store receipt moves to a different `app_user_id`. This happens,
for example, when a user reinstalls before registering, gets a fresh UUID, and taps *Restore
purchases*.

**Behaviour:**

- `transferred_from` and `transferred_to` are arrays of ids. Each side may be a user, a pending row,
  or unknown.
- **Move the entitlement:**
  - The `transferred_to` side gets the state the `transferred_from` side had. Create a pending row
    if it is not yet a user.
  - The `transferred_from` side drops to `free`, or its pending row is deleted.
- **Never downgrade a grandfathered user**, as in the entitlement prompt.
- Apply the same idempotency and timestamp ordering as every other event.

## 6. Documentation

Update the Swagger UI and `Requirements/api/backend_documentation.md` in the client repo's copy
when you report back:

- the register request now carries `id`;
- the social sign-up `id`;
- the `409 DUPLICATE_RESOURCE` case on both;
- the `pending_entitlements` table;
- the `TRANSFER` handling.

## 7. Tests

- Register with a valid client `id` → `users.id` equals it, and the response `user.id` echoes it.
- Register without `id` → `400`. With a malformed `id` → `400`. With an `id` already in use → `409`
  `DUPLICATE_RESOURCE`, and the existing user is untouched.
- Register where both the id and the email are taken → the email error.
- Social sign-up with `id` → the user is created with that id. Social sign-up without `id` → a
  generated id. Social login for an existing user with a different `id` → logged in as the existing
  user, the `id` is ignored, and no claim happens.
- Webhook `INITIAL_PURCHASE` for an unknown UUID → a pending row is created. Register with that id
  afterwards → the user is `pro` with the event's expiry, and the pending row is gone.
- Webhook `INITIAL_PURCHASE` then `EXPIRATION` for an unknown UUID, then register → the user is
  `free`.
- Out-of-order events for a pending id follow `event_timestamp_ms`. A duplicate event id is applied
  once.
- Webhook for `$RCAnonymousID:…` → `200`, and no row is created.
- Webhook for an unknown id whose `aliases` contain an existing user → applied to that user.
- Register with no pending row while RevenueCat v2 (mocked) returns an active item with
  `entitlement_id` = `REVENUECAT_PRO_ENTITLEMENT_ID` → `pro`, and the expiry converts from
  milliseconds. With `expires_at: null` → `pro` with no expiry. With an expired item, or only
  another entitlement → `free`.
- RevenueCat (mocked) returns `404` → `free`, and nothing is logged as an error. It times out, or
  returns `401`, `403`, `429` or `5xx` → registration still succeeds as `free`.
- The outgoing request carries `Authorization: Bearer` with the configured key, hits
  `/v2/projects/projc4a0bfac/customers/{id}`, and the key never appears in the logs.
- `TRANSFER` from a pending id to another pending id → the entitlement moves. `TRANSFER` from a
  pending id to an existing user → the user becomes `pro`. `TRANSFER` away from a grandfathered user
  → they stay Pro.
- The cleanup job deletes pending rows that expired more than 12 months ago and keeps everything
  else.

Deploy it to the VPS server and update the Swagger UI documentation.
