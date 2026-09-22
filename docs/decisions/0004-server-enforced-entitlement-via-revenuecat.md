---
status: "proposed"
date: 2026-09-22
deciders: Jörg Voye
---

# The backend is the authority on Pro entitlement, fed by a RevenueCat webhook

## Context and Problem Statement

Cross-device sync is the paid feature: the free tier stays local-only, and Pro syncs. That makes
entitlement a question the *server* has to answer, because entitlement decides whether a user's
writes are accepted at all — not merely whether a screen is shown.

Tracky ships on iOS and Android, so subscriptions arrive through two different stores with two
different receipt formats, renewal models and failure modes. Something has to turn both into a
single answer to "is this account Pro right now?", and that answer has to survive the client being
modified, offline, or simply wrong.

## Decision Drivers

- Entitlement gates server writes, so a client-side check is decorative at best.
- Two stores, two receipt formats — normalising them by hand is ongoing work unrelated to the
  product.
- Subscription state changes without the app running: renewals, lapses, refunds, billing retries.
- Existing users must get a free trial and then downgrade to local-only **without losing data**.
- The free tier is intended to be genuinely anonymous — no account, no server-side data at all.

## Considered Options

- **Client-side entitlement checks** against the store SDKs, with the client telling the server.
- **Direct store-to-server integration**, validating App Store and Play receipts ourselves.
- **RevenueCat, with a webhook making the backend the authority.**

## Decision Outcome

Chosen option: **RevenueCat for billing, with a webhook that writes entitlement to our own backend,
which is then the only authority on it.**

The client may read entitlement to decide what to show, but never to decide what is permitted. The
server checks its own record before accepting any sync write, so a modified or stale client gains
nothing.

Downgrade is deliberately non-destructive: server-side data is **retained**, not deleted, when a
subscription lapses. A user who resubscribes finds their history intact, and a user who does not
keeps working locally exactly as the free tier does.

### Consequences

- Good, because entitlement cannot be bypassed by modifying the client — the check that matters is
  on the write path.
- Good, because RevenueCat normalises both stores, so renewals, lapses and refunds arrive as one
  event shape instead of two receipt formats to validate.
- Good, because the webhook means entitlement changes without the app running, which is when most
  of them happen.
- Good, because retaining data on downgrade makes resubscribing lossless and keeps the free tier
  honest rather than punitive.
- Bad, because it adds a third-party dependency on the payment path, with its own availability and
  pricing.
- Bad, because a webhook can be missed or arrive late, so the backend needs reconciliation rather
  than trusting the event stream alone.
- Bad, because the client must handle a downgrade it did not initiate and may learn about
  mid-session.
- Neutral, because the free tier's "no account at all" goal means entitlement and identity are
  entangled: there is no user record to attach entitlement to until someone subscribes.

### Confirmation

Not yet implementable — this ADR is `proposed`. When built, the check to enforce is that a sync
write from an account without entitlement is rejected **by the server**, verified the same way the
sync contract is: an assertion in `scripts/verify_sync_contract.sh` run against a deployed
environment, not a unit test against a mock.

## Pros and Cons of the Options

### Client-side entitlement checks

- Good, because it is the least work and needs no backend change.
- Good, because it responds instantly to a purchase, with no webhook latency.
- Bad, because it is trivially bypassed, and what it guards is server storage rather than a local
  feature flag.
- Bad, because the server would still have to decide whether to accept the write, so the check just
  moves rather than disappears.

### Direct store-to-server integration

- Good, because there is no third party on the payment path and no per-transaction fee beyond the
  stores'.
- Good, because receipt data is not shared with anyone else.
- Bad, because it means maintaining two receipt validators, two renewal models and two sets of edge
  cases, permanently, for a solved problem.
- Bad, because store-side changes become our maintenance burden on a path where failure means
  either a paying user locked out or a non-paying user syncing.

### RevenueCat with a server-side webhook (chosen)

- Good, because one normalised entitlement state across both stores.
- Good, because the authority sits where the enforcement has to happen anyway.
- Bad, because it is a third-party dependency on billing.
- Bad, because webhook delivery needs reconciliation.

## More Information

Specified in `Requirements/backend-pro-entitlement-api.md`. **Not implemented** — phase 4 of the
cross-device sync work (issue 45); the client currently has no entitlement gate at all.

Sequencing note: this must land before any release that exposes sync to users. Shipping sync
ungated and adding the gate later means taking a working feature away from people who already have
it, which is considerably worse than never granting it.

Related: phase 5 (anonymous mode, bulk import on upgrade, trial and downgrade) depends on this, and
is larger than it looks — the app currently requires a session to show anything, and logging out
wipes the local database. Both must change before a genuinely anonymous free tier is possible.

Revisit when phase 4 is built; the reasoning here has not yet met an implementation, and this ADR
should be superseded rather than edited if it turns out to be wrong.
