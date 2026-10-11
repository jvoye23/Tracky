---
status: "accepted"
date: 2026-10-11
deciders: Jörg Voye
---

# The client mints the user id, and RevenueCat and the backend share it

## Context and Problem Statement

Onboarding sells Pro **before** an account exists: a user buys first and registers afterwards,
sometimes right away, sometimes after months on the local-only free tier. The purchase therefore
needs an id to land on, and the account created later has to be found by that same id. If it
isn't, the server never learns that the account is Pro (ADR 0004).

Until now the backend minted the user id at `/api/auth/register`, and RevenueCat ran on an
anonymous `$RCAnonymousID` that was aliased to the account at login. The backend also dropped any
webhook event whose `app_user_id` matched no user. In this flow that is every purchase made before
sign-up, so a new paying account started as `free`. Which side should own the user id, and when
should it exist?

## Decision Drivers

- Pay-first, register-later is the product flow, and the gap between the two can be months long.
- The server is the authority on entitlement (ADR 0004), so a purchase made before sign-up must
  still reach the account that is created later.
- The free tier must keep working offline and without an account, so the id cannot depend on a
  network call at first launch.
- One customer per person in RevenueCat, instead of anonymous ids aliased onto accounts.
- An id must never act as a credential: knowing a UUID must not grant access to any account.

## Considered Options

- **Anonymous RevenueCat customer, linked at registration** with `logIn(serverId)` (the previous
  design).
- **Backend-issued id at first launch**, through a call that creates a provisional user before the
  paywall.
- **Client-minted UUID from first launch**, used as the RevenueCat `appUserID` and sent at
  registration, where the server stores it as the user id.

## Decision Outcome

Chosen option: **the client mints a UUID v4 on first launch and that one id is the RevenueCat
customer id and, after sign-up, the backend user id.** It is the only option that needs no network
at first launch and leaves nothing to reconcile at registration.

The rules that follow from it, which code in both repos must keep:

- **App start.** `AppUserIdStore` mints the id once and persists it in DataStore. RevenueCat is
  configured with it before anything else touches `Purchases`. The app never runs on an anonymous
  RevenueCat customer.
- **Sign-up** (`/api/auth/register`, and later social sign-up) sends the id. The server stores it
  verbatim and, in the same transaction, claims anything RevenueCat reported for that id before the
  account existed. It keeps such events in `pending_entitlements` instead of dropping them, and asks
  RevenueCat's REST API directly when none have arrived yet.
- **A `409 DUPLICATE_RESOURCE` at sign-up** can only mean this device registered before. The client
  sends the user to log in and **never mints a new id to retry**: a new id would leave the purchase
  on the old one.
- **Logging in to an existing account** adopts the account's id: the app switches RevenueCat to it,
  stores it, and calls `syncPurchases()` so that a receipt bought under the old id moves to the
  account.
- **Losing the session** (logout or expiry) mints a fresh id, stores it and switches RevenueCat to
  it. **Never call RevenueCat's `logOut()`**: it falls back to an anonymous id, which the server
  ignores.
- **A failed switch is retried with backoff, and the order survives a process death.** Signing in
  switches first and stores second, so a killed app finds the ids still different at the next launch
  and switches and syncs again. Losing the session stores first and switches second, so a killed app
  configures RevenueCat with the fresh id at the next launch instead of the departed account's.
- **Existing accounts keep their server-minted ids.** Nothing is migrated.

`AppUserIdentitySync` implements the client side of these rules by watching the session.
`Requirements/backend-client-user-id-api.md` specifies the server side.

### Consequences

- Good, because a purchase made before the account existed lands directly on the account's id. No
  aliasing is needed, and the account is Pro from the moment it is created.
- Good, because the id exists offline from first launch, so the free tier keeps needing no server.
- Good, because it matches how the app already treats every other primary key: projects, tasks and
  intervals are client-generated UUIDs too.
- Good, because the RevenueCat customer page, the webhook and the users table all name the person
  by the same id, which makes support and debugging direct.
- Bad, because the server has to accept an id from the client. It is safe only because the server
  validates the format, rejects a taken id, and never logs anyone in by id. Every future auth
  endpoint has to keep that.
- Bad, because the server now stores entitlement state for ids that may never register. That needs
  a retention job (12 months after expiry) and `TRANSFER` handling.
- Bad, because reinstalling before registering loses the stored id. The purchase comes back only
  through *Restore purchases*, which moves the receipt to the new id.
- Neutral, because every logout and every session expiry leaves an empty RevenueCat customer
  behind, and installs that predate this change create one throwaway customer once.

### Confirmation

- Unit tests in `commonTest`:
  - `AppUserIdentitySyncTest`: registering with the local id changes nothing, logging in to an
    existing account adopts its id and syncs purchases, logging out switches to a fresh id, a
    failed sign-in switch keeps the old id stored and is retried, and a logout stores its fresh id
    before the switch;
  - `DataStoreAppUserIdStoreTest`: the id is minted once, survives a restart, and changes only
    through `replace`;
  - `KtorAuthServiceTest`: register and social sign-in send the id.
- Verified end to end on 2026-10-11 on a Pixel 8 Pro against the deployed backend, with the
  RevenueCat Test Store:
  - first launch created the RevenueCat customer under the stored UUID;
  - a purchase made months-later style, from the in-app paywall, landed on that UUID;
  - register returned the same `user.id` with `"entitlement":"pro"`;
  - `/api/sync/import` succeeded on the first try;
  - logout created a separate customer, while the old one kept its purchase;
  - logging back in adopted the account's id.
- Not verified end to end:
  - reinstall followed by *Restore purchases* (the `TRANSFER` path);
  - social sign-up, which the backend does not implement yet.

## Pros and Cons of the Options

### Anonymous RevenueCat customer, linked at registration

- Good, because the client needs no id of its own, and RevenueCat aliases the anonymous purchase
  onto the account at `logIn`.
- Bad, because the server never sees a purchase made before sign-up, since its `app_user_id` is
  anonymous. The account starts `free`, and sync is refused with `402` until a later webhook happens
  to name the real id.
- Bad, because every customer carries two aliased ids, and the backend would have to verify a
  purchase under an id it never chose.

### Backend-issued id at first launch

- Good, because the server stays the only one minting user ids.
- Bad, because the paywall would need the network before it can sell anything, which goes against
  a free tier that works offline.
- Bad, because the server would create a provisional user row for every install, most of which
  never pay or register.

### Client-minted UUID from first launch (chosen)

- Good, because it needs no network at first launch and leaves nothing to reconcile at sign-up.
- Good, because it follows the app's existing practice of client-generated primary keys.
- Bad, because the server must treat a client-supplied id carefully and keep pending purchase state
  for unregistered ids.

## More Information

- Builds on ADR 0004 (*The backend is the authority on Pro entitlement*). It resolves that ADR's
  neutral consequence, "there is no user record to attach entitlement to until someone subscribes".
  RevenueCat events for an id that has no account yet are kept in `pending_entitlements` and claimed
  when the account is created. It does not supersede 0004.
- Server contract: `Requirements/backend-client-user-id-api.md` (shipped 2026-10-11) and
  `Requirements/api/backend_documentation.md`.
- Client: `core/domain/identity/AppUserIdStore.kt`, `core/data/identity/DataStoreAppUserIdStore.kt`,
  `core/domain/purchases/AppUserIdentitySync.kt`, `core/data/purchases/RevenueCat.kt`.
- Revisit when social sign-up is implemented on the backend. It has to follow the same rules: use
  the id only when the call creates the account, and never let it select an existing one.
