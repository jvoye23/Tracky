# Backend prompt — Pro entitlement and sync gating

Paste the block below into a session opened on the Tracky backend repo.

---

Tracky is going freemium. Syncing across devices becomes the paid feature; everything else stays
free and local. Please add an entitlement to the user, a RevenueCat webhook to keep it current, and
gating on every endpoint that moves a user's data — plus a migration and tests.

Depends on `Requirements/backend-delta-sync-api.md`, `backend-active-timer-api.md` and
`backend-realtime-and-devices-api.md`.

## Context

The product split is: **free users keep everything on the device and never touch the server; Pro
users sync and can track across devices.** That makes the free tier cost nothing to run, and it
makes the boundary easy to describe to a user — there is no device counting, no storage quota and
no partial sync to explain. The gate *is* sync.

The important consequence for this repo: **the client's paywall is not what protects these
endpoints.** It is a purchase screen, it runs on a device we do not control, and it can be
bypassed. The server must refuse to serve a free account, and must decide that from its own
record rather than from anything the client sends. In particular, do not accept an entitlement
claim in the JWT, a header, or a request field.

RevenueCat is the store abstraction on the client — it handles Google Play and the App Store
behind one SDK, and it validates receipts server-side. So the server does not talk to either store
and does not validate receipts itself. It learns about entitlement changes from RevenueCat's
webhook, and treats RevenueCat as the authority on what was purchased while remaining the
authority on what is served.

## 1. Entitlement on the user

Add to the users table:

```
entitlement            not null default 'free'   -- free | pro
entitlement_expires_at null                      -- when the current pro period lapses
entitlement_source     null                      -- revenuecat | trial | grandfathered | manual
trial_started_at       null
is_grandfathered       not null default false
```

**Behaviour:**

- A user counts as Pro when `entitlement = 'pro'` **and** (`entitlement_expires_at` is null or in
  the future), **or** `is_grandfathered` is true. Put that in one function and call it from
  everywhere; an entitlement check duplicated across controllers is how one endpoint ends up free.
- **`is_grandfathered` is set for every account that existed before the paywall ships.** Those
  users signed up for an app where sync was simply how it worked, and taking it away from them is
  not a growth tactic, it is a broken promise. Set it in the migration, from a fixed cutoff
  timestamp recorded in the migration itself so a re-run is idempotent.
- Never downgrade a grandfathered account, including when RevenueCat reports a cancellation — they
  may also have subscribed and then cancelled, and the flag outranks the subscription.
- Include the entitlement in the `/api/auth/login`, `/api/auth/register` and `/api/auth/refresh`
  responses so the client can gate its UI without an extra call:

  ```json
  { "entitlement": "pro", "entitlementExpiresAt": "2026-10-20T00:00:00Z", "isGrandfathered": false }
  ```

## 2. `GET /api/entitlement`

Same authentication as the other authenticated routes. Returns the block above. The client polls
this on resume and after a purchase, because RevenueCat's webhook and the client's own purchase
completion race, and the client needs a way to find out who won.

| Status | When |
|---|---|
| `200` | always, for an authenticated caller — a free user gets `"entitlement": "free"`, not an error |
| `401` | missing or invalid credentials |

## 3. `POST /api/webhooks/revenuecat`

**Behaviour:**

- **Verify the shared secret** in the `Authorization` header against configuration before doing
  anything else, and return `401` on a mismatch. This endpoint is unauthenticated in the JWT sense
  and publicly reachable; the secret is the only thing standing between a stranger and free Pro
  for any account they can name.
- RevenueCat's `app_user_id` is the Tracky user id. The client sets it at login. An
  `app_user_id` that matches no user is **`200` with no action, logged** — never an error, or
  RevenueCat will retry it forever.
- Map the event types: `INITIAL_PURCHASE`, `RENEWAL`, `UNCANCELLATION`, `PRODUCT_CHANGE` →
  `entitlement = 'pro'` with `entitlement_expires_at` from the event. `EXPIRATION` →
  `'free'`. `CANCELLATION` → **leave Pro in place** and keep the existing expiry; a cancellation
  means auto-renew is off, not that access ended today. `BILLING_ISSUE` → leave Pro in place, log
  it. Unknown event type → `200`, log, do nothing.
- **Idempotent on RevenueCat's event id**, which they will re-send. Store the ids you have
  processed and skip repeats.
- **Apply events in the order RevenueCat's `event_timestamp_ms` says**, not arrival order. A
  retried `EXPIRATION` arriving after a fresh `RENEWAL` must not revoke the renewal.
- Respond `200` quickly. Do the work in the request if it is a single row update; do not make
  RevenueCat wait on anything slow.

| Status | When |
|---|---|
| `200` | processed, ignored as a duplicate, or unknown user |
| `401` | shared secret missing or wrong |
| `5xx` | RevenueCat retries with backoff — acceptable, but log loudly |

## 4. Gating

Refuse these for a non-Pro caller:

- `GET /api/sync/changes`
- `POST /api/sync/import`
- `GET /api/timer/active`, `PUT /api/timer/active`, `POST /api/timer/active/stop`
- the `/api/realtime` WebSocket upgrade
- `POST /api/devices`
- every `/api/projects/**` route

```json
{ "code": "ENTITLEMENT_REQUIRED", "message": "Syncing requires Tracky Pro." }
```

**Behaviour:**

- Status **`402 Payment Required`**. Not `403`: the client has to tell "you are not allowed"
  apart from "you have not paid", because the second one opens a paywall and the first one is a
  bug. `402` is unusual but it is exactly this situation, and the client maps it to one specific
  `DataError`.
- **The client treats `402` as terminal, not transient** — it will stop retrying and drop the
  queued operation rather than re-sending it forever. So do not return `402` for a transient
  condition such as a webhook you have not processed yet. When in doubt, serve the request.
- `/api/auth/**` and `GET /api/entitlement` stay reachable for free accounts. A free user must
  still be able to log in and be told what they are missing.
- A lapsed Pro user's data is **retained, not deleted.** Keep it for at least 12 months. They stop
  being able to sync; they do not stop owning their work, and resubscribing must restore it
  intact.

## 5. Trial

Existing accounts get a trial rather than an immediate wall, per the product decision.

**Behaviour:**

- `POST /api/entitlement/trial` starts one: sets `entitlement = 'pro'`, `entitlement_source =
  'trial'`, `trial_started_at = now`, `entitlement_expires_at = now + 14 days`.
- **One per account, ever.** A second call returns `409` with the original `trial_started_at`.
  `trial_started_at` is what enforces it, which is why it stays populated after the trial lapses.
- A real purchase during a trial replaces it: `entitlement_source` becomes `revenuecat` and the
  expiry comes from the event. Do not stack the remaining trial days on top.
- Expiry is evaluated on read, from `entitlement_expires_at`. **Do not write a scheduled job to
  flip rows to `free`** — a clock-driven mutation that races the webhook is how a paying user gets
  locked out. The column says when it lapses; the check reads the column.

## 6. Tests

- A free user gets `402 ENTITLEMENT_REQUIRED` from every gated route, and `200` from
  `/api/entitlement` and the auth routes.
- A Pro user gets `200` from every gated route.
- A Pro user whose `entitlement_expires_at` has passed is treated as free.
- A grandfathered user is Pro with `entitlement = 'free'` and a null expiry, and stays Pro after an
  `EXPIRATION` event.
- The migration marks every pre-cutoff account grandfathered, and running it twice changes nothing.
- Webhook: a valid secret is accepted, a wrong one is `401`, an absent one is `401`.
- Webhook: the same event id twice applies once.
- Webhook: an `EXPIRATION` with an older `event_timestamp_ms` arriving after a `RENEWAL` does not
  revoke Pro.
- Webhook: `CANCELLATION` leaves Pro and the expiry in place; `EXPIRATION` then removes it.
- Webhook: an unknown `app_user_id` returns `200` and changes nothing.
- Trial: first call grants 14 days, second returns `409`, and a purchase during the trial replaces
  it rather than extending it.
- A lapsed user's projects are still present in the database, and a later `RENEWAL` restores access
  to all of them.
- The WebSocket upgrade is refused for a free user before the socket opens.

Deploy it to the VPS server and update the Swagger UI documentation.
