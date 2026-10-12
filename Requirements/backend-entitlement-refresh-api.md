# Backend prompt — lifetime purchases and entitlement refresh

Paste the block below into a session opened on the Tracky backend repo.

---

A user who bought **lifetime** Pro still gets `402 ENTITLEMENT_REQUIRED` from every gated route.
RevenueCat reports the entitlement as active, and so does the app's paywall, but the server never
recorded the purchase. Please fix the webhook's event mapping and add an endpoint that re-reads the
entitlement from RevenueCat, so that an account that missed or misread an event can be repaired.
Add tests for both.

Depends on `Requirements/backend-pro-entitlement-api.md` and
`Requirements/backend-client-user-id-api.md`.

## Context

What happened, from RevenueCat's records for customer `edcd7d36-88e9-4d11-8d32-2d4050b15a85` (a
Tracky user id):

- A Test Store subscription with 5-minute sandbox periods was set to not renew and ended at
  08:16:55 UTC. The server correctly started answering `402` after that.
- At 08:38:16 UTC the same customer bought the lifetime product, a one-time purchase in the Test
  Store sandbox. The entitlement is active in RevenueCat.
- The server still answers `402`.

**Cause.** `backend-pro-entitlement-api.md` §3 maps only `INITIAL_PURCHASE`, `RENEWAL`,
`UNCANCELLATION` and `PRODUCT_CHANGE` to Pro. RevenueCat sends a one-time purchase as
**`NON_RENEWING_PURCHASE`**. That fell into "unknown event type → `200`, log, do nothing", so the
purchase was acknowledged and dropped.

**Nothing can repair this today.** The REST v2 lookup in `backend-client-user-id-api.md` §4 only runs
when an account is created. RevenueCat does not resend an event that got a `200`. So an account that
missed or misread an event stays wrong until the next event, and a lifetime purchase never produces
a next event.

## 1. Webhook event mapping

This changes the mapping in `backend-pro-entitlement-api.md` §3. Change it in the one mapping
function that both the user path and the `pending_entitlements` path call
(`backend-client-user-id-api.md` §3).

| Event | Effect |
|---|---|
| `NON_RENEWING_PURCHASE` | `entitlement = 'pro'`, `entitlement_expires_at` from the event's `expiration_at_ms`. **A null `expiration_at_ms` means lifetime**: store null, which the Pro check already reads as "does not lapse" |
| `SUBSCRIPTION_EXTENDED` | `entitlement = 'pro'` with the new `expiration_at_ms` |

Every other row of the existing mapping stays as it is.

**Store which product granted Pro.** Add a nullable `entitlement_product_id` to `users` and to
`pending_entitlements`. Set it from the event's `product_id` on every event that grants or extends
Pro, and carry it across in the pending-row claim and in `TRANSFER`.

**An `EXPIRATION` only revokes the product it names.**

- Apply `EXPIRATION` only when its `product_id` equals the stored `entitlement_product_id`, or when
  the stored value is null (rows from before this change).
- Otherwise answer `200`, log it, and change nothing.
- This is what keeps a lifetime purchase alive when an older subscription on the same customer
  expires later. It also covers a user who moved from a monthly to a yearly plan.
- Ordering by `event_timestamp_ms` cannot cover this case on its own. The subscription's expiry can
  be timestamped after the lifetime purchase.

**Refund of a lifetime purchase.**

- RevenueCat sends a refunded one-time purchase as `CANCELLATION` with `cancel_reason =
  CUSTOMER_SUPPORT`, and no `EXPIRATION` follows.
- When that event's `product_id` equals the stored `entitlement_product_id` and the stored
  `entitlement_expires_at` is null, set the entitlement to `free`.
- A `CANCELLATION` of a subscription keeps its existing behaviour: Pro stays until the expiry.

**Unchanged.** Every existing rule still applies to the new events:

- the shared-secret check;
- idempotency on the event id;
- ordering by `event_timestamp_ms`;
- pending rows and `aliases`;
- non-UUID ids are ignored;
- **a grandfathered account is never downgraded.**

## 2. `POST /api/entitlement/refresh`

Same JWT authentication as `GET /api/entitlement`, and reachable for free accounts. It is not gated.
The app calls it:

- when a gated route answers `402` but the store on the device says the user is Pro;
- straight after an in-app purchase, so that sync works before the webhook lands.

No request body.

**Behaviour:**

- **Ask RevenueCat** with exactly the request, client, configuration and timeout that the
  registration lookup uses (`backend-client-user-id-api.md` §4, step 2):
  - `GET /v2/projects/{REVENUECAT_PROJECT_ID}/customers/{userId}`, using the caller's user id from
    the JWT;
  - the same three environment variables;
  - a timeout of about 3 seconds, with no retry inside the request.

  Factor that call into one function used by both registration and refresh. Do not write a second
  client.
- **Map the answer** the same way as the registration lookup:
  - An active item whose `entitlement_id` equals `REVENUECAT_PRO_ENTITLEMENT_ID`, with `expires_at`
    null or in the future, means `pro`. Set `entitlement_expires_at` from `expires_at` (milliseconds
    since the epoch, null for lifetime) and set `entitlement_source = 'revenuecat'`.
  - RevenueCat v2 does not return the product id here. Set `entitlement_product_id` to null, so that
    the next `EXPIRATION` is trusted, as the rows from before this change are.
  - No such item, or a `404`, means RevenueCat sees no purchase.
- **What a "no purchase" answer may change:**
  - Leave a grandfathered account alone.
  - Leave an active trial alone: `entitlement_source = 'trial'` and `entitlement_expires_at` in the
    future.
  - Leave alone a row with `entitlement_source = 'manual'`.
  - Otherwise set the account to `free`.
  - In short, a refresh only overrides what RevenueCat owns.
- **Write the result to the user row**, then answer with the same body as `GET /api/entitlement`:

  ```json
  { "entitlement": "pro", "entitlementExpiresAt": null, "isGrandfathered": false }
  ```

- **Rate limit: one refresh per user per 30 seconds.** A second call inside the window answers `429`
  and touches nothing. Keep the limit in memory if the backend runs as one instance. Each call costs
  a RevenueCat API request, and RevenueCat rate-limits the project, not the user.
- **A RevenueCat failure answers `503` and touches nothing.** This covers a timeout, `401`, `403`,
  `429` and `5xx`. A failed lookup is not evidence that the user lost Pro, so never downgrade on
  one. Log it at warning level. Never log the key or the `Authorization` header.
- If the key is unset in configuration, answer `503` as well. Do not answer `200` with the stored
  state, because the caller cannot then tell that nothing was checked.

| Status | When |
|---|---|
| `200` | RevenueCat answered (including `404`). The body is the resulting entitlement |
| `401` | missing or invalid credentials |
| `429` | a refresh for this user ran less than 30 seconds ago |
| `503` | RevenueCat failed or timed out, or the key is not configured |

```json
{ "code": "TOO_MANY_REQUESTS", "message": "Try again in a moment." }
{ "code": "ENTITLEMENT_SOURCE_UNAVAILABLE", "message": "Could not reach the purchase service." }
```

## 3. Repair the affected account

Once this is deployed, the account above needs no manual fix. Refreshing as
`dev@jv-coding-solutions.com` must return `pro` with `entitlementExpiresAt: null`. After that,
`GET /api/projects` must answer `200` and include its existing "Dev Project".

## 4. Tests

**Webhook:**

- `NON_RENEWING_PURCHASE` with a null `expiration_at_ms`:
  - the user becomes Pro with a null expiry and the product id stored;
  - a gated route then returns `200`.
- `NON_RENEWING_PURCHASE` for an id that is not a user yet:
  - a pending row is created;
  - the row is claimed at registration, product id included.
- `SUBSCRIPTION_EXTENDED` moves the expiry forward.
- Lifetime survives a different product expiring: a lifetime purchase, then an `EXPIRATION` for an
  older subscription product with a **later** `event_timestamp_ms`. The account stays Pro with a
  null expiry.
- An `EXPIRATION` for the product that granted Pro still revokes it.
- A `CANCELLATION` with `cancel_reason = CUSTOMER_SUPPORT` on the lifetime product sets the account
  to `free`.
- A grandfathered account stays Pro through all of the above.
- The same `NON_RENEWING_PURCHASE` event id twice applies once.

**Refresh:**

- A free account that RevenueCat reports as active becomes `pro`. The `200` body says so, and a gated
  route then returns `200`.
- A lifetime entitlement (`expires_at` null) gives `entitlementExpiresAt: null`.
- A Pro account that RevenueCat reports with no entitlement becomes `free`.
- A `404` from RevenueCat gives `free` and `200`.
- Refresh does not downgrade:
  - a grandfathered account;
  - an account in an active trial;
  - a `manual` account.
- A second call within 30 seconds returns `429` and the row is unchanged.
- A RevenueCat timeout, `401` and `5xx` each return `503`, and the row is unchanged, including a Pro
  row that would otherwise have gone `free`.
- An unset key returns `503`.
- No credentials returns `401`.
- A free account can call it. It is not behind the gate.

## 5. Documentation

Update the Swagger UI, and `Requirements/api/backend_documentation.md` in the client repo's copy,
when you report back:

- the two new events in the webhook mapping;
- the `EXPIRATION` product rule;
- the refund rule;
- `entitlement_product_id` on both tables;
- `POST /api/entitlement/refresh` with its status table.

Deploy it to the VPS server.
