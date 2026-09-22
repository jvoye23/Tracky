# Backend prompt — device registry and realtime change delivery

Paste the block below into a session opened on the Tracky backend repo.

---

The Tracky client can now learn about another device's timer by pulling
`GET /api/sync/changes`, but only when it happens to pull. For the timer to feel live, the server
has to tell it. Please add a device registry and two delivery channels — a WebSocket for
foregrounded apps and push for backgrounded ones — plus a migration and tests.

Depends on `Requirements/backend-delta-sync-api.md` and `Requirements/backend-active-timer-api.md`.

## Context

A running timer needs no per-second traffic: every device derives the elapsed value from
`startedAt`, so two devices holding the same start agree on the number forever without talking.
What has to arrive quickly is the **transition** — a start, a stop, a supersede. Those are rare
(a handful a day per user), which is what makes realtime delivery affordable here: this is not a
stream, it is an occasional nudge.

The client's fallback already works. It pulls on connectivity and foreground transitions, so a
device that missed a nudge catches up the moment the user opens the app. Everything below is an
optimisation on top of a correct system, and **must stay that way** — no message may be the only
carrier of a fact. The delta feed remains the source of truth, and the channels below only ever
say "there is something newer than cursor N, come and get it".

That is also why the payloads are thin. Sending the changed rows over the socket would mean two
code paths that can disagree, ordering problems between a socket message and a concurrent pull, and
a second place to get authorisation right. One exception is carved out below, for the timer, and it
is an exception about latency rather than content.

## 1. `POST /api/devices` — register a device

Same authentication as the other authenticated routes.

```json
{
  "deviceId": "d41c7f90-5b2e-4a18-9c60-7e3f1a8b2d45",
  "platform": "android",
  "displayName": "Pixel 9",
  "pushToken": "fcm-token-…",
  "liveActivityToken": null
}
```

**Behaviour:**

- `deviceId` is a client-generated UUID, minted once per install and stable across launches.
- **Upsert, keyed on `(user_id, deviceId)`.** The client calls this on every launch and whenever
  its push token rotates; it must never accumulate duplicates.
- `platform` is `android` or `ios`. `displayName` is for the UI only ("running on Pixel 9") and
  may be absent.
- `pushToken` may be null — a user who declined notifications still needs the device registered,
  because the registry is also what tells the fan-out which devices to skip.
- `liveActivityToken` is iOS-only and short-lived; see section 4.
- A token string that the push provider later rejects as unregistered is cleared from the row, not
  the whole row deleted — the device is still real, it just cannot be pushed to.

`DELETE /api/devices/{deviceId}` removes a registration, called on logout.

| Status | When |
|---|---|
| `200` | registered or updated |
| `400` | `deviceId` not a UUID, or unknown `platform` |
| `401` | missing or invalid credentials |

## 2. `wss://tracky.jv-coding-solutions.com/api/realtime`

**Behaviour:**

- Authenticated with the same JWT as the REST routes, sent in the `Authorization` header on the
  upgrade request. Do **not** accept it as a query parameter — query strings end up in access logs
  and proxy logs.
- The client sends one envelope after connecting: `{"type":"hello","deviceId":"…","cursor":84213}`.
- The server replies `{"type":"ready"}`, and, **if the user's current maximum `change_seq` is
  higher than the cursor the client announced**, immediately follows with an `invalidate`. This
  closes the gap between the client's last pull and the socket opening, which is otherwise a hole
  that only the next nudge would fill.
- Thereafter, on every change to that user's data:

  ```json
  {"type":"invalidate","cursor":84219}
  ```

  The client pulls `GET /api/sync/changes?since=<its own cursor>`. It deliberately does not trust
  the number for anything but "something is newer than what I have".
- **Fan-out excludes the originating device.** The device that made the change already applied the
  response; nudging it makes it pull its own write back. Match on the `deviceId` that authenticated
  the socket against the one that made the mutation.
- Ping/pong every 30 seconds, and drop a socket that misses two. Mobile sockets die silently.
- A user may hold several sockets at once — phone and tablet both foregrounded is normal. Fan out
  to all of them except the originator.
- **Caddy must be configured to pass the upgrade through.** It does not by default for a reverse
  proxy that was only ever serving REST.

### The one fat message

Timer transitions additionally carry the active-timer state inline:

```json
{"type":"timer","cursor":84219,"active":{ /* GET /api/timer/active shape, or null */ }}
```

This exists purely so the other device can repaint its notification and its iOS Live Activity in
one hop instead of two. **The client still pulls the delta afterwards** — the inline state is a
head start, not a substitute, and the rows it eventually writes come from the delta. If in doubt,
send `invalidate` instead; correctness does not depend on this message existing.

## 3. Push, for a backgrounded app

When a user's change has no live socket for one of their other devices, send a silent data
notification to that device's `pushToken` instead.

**Behaviour:**

- **Data-only.** No `notification` block on either platform — the client decides whether anything
  user-visible appears. The Android app already owns its timer notification and iOS owns its Live
  Activity; a server-authored alert would duplicate them and would be wrong the moment the user
  stopped the timer elsewhere.
- Payload is the same envelope as the socket: `{"type":"invalidate","cursor":84219}`, or `timer`
  with the inline active state.
- Android: FCM, **high priority** for timer transitions — a normal-priority data message can be
  delayed for minutes in Doze, which is exactly the case this feature exists for. Everything else
  goes at normal priority.
- iOS: APNs with `content-available: 1`, `apns-push-type: background`, `apns-priority: 5`.
  **Assume it will sometimes not be delivered.** iOS throttles background pushes by app usage and
  battery state, and there is no way to make them reliable. This is acceptable because the client
  recovers on its next foreground pull — but it is the reason section 4 exists.
- Do not push to the originating device.
- Coalesce: if several changes land within a second or two, send one push, not one per row.

## 4. ActivityKit push, for the iOS Live Activity

A Live Activity is drawn out of the app's process and keeps rendering when the app is not running.
A background push cannot reliably wake the app to update it, so iOS provides a direct channel:
each Live Activity has its own push token, and a push sent to that token updates the card without
the app running at all.

**Behaviour:**

- The client registers `liveActivityToken` through `POST /api/devices` when it starts a Live
  Activity, and clears it when the activity ends.
- On a timer transition affecting a device that has one, send an APNs push with
  `apns-push-type: liveactivity` to that token, carrying the `content-state` the widget expects.
  Coordinate the exact JSON with whoever owns `iosApp/Shared/TrackyTimerAttributes.swift` — the
  keys must match that struct's `ContentState` exactly, and it is the client team's call.
- Tokens are **short-lived and rotate**. Expect to be told a new one often, and expect the old one
  to start failing without warning. Clear a rejected token rather than retrying it.
- A stop must send a push that **ends** the activity, not merely one that marks it not-running.
  A Live Activity left alive is a stale card on the user's Lock Screen.

This is the only channel that works when the other device's app has been killed, which is the most
common real situation for "I started this on my phone this morning".

## 5. Tests

- Registering the same `deviceId` twice updates the row and does not create a second one.
- A change made by device A produces an `invalidate` on device B's socket and **not** on A's.
- A client connecting with a stale cursor receives an `invalidate` immediately after `ready`.
- A client connecting with the current cursor receives `ready` and nothing else.
- Two sockets for the same user both receive the nudge; a third for a different user does not.
- A socket that stops answering pings is dropped and stops receiving fan-out.
- A timer start produces the `timer` envelope with the active state inline; a stop produces one
  with `"active": null`.
- A device with no live socket gets a push instead, and a device with a live socket does not get
  both.
- A push token the provider reports as unregistered is cleared from the row while the registration
  survives.
- Several changes inside the coalescing window produce one push.
- `DELETE /api/devices/{deviceId}` stops both channels for that device.
- An upgrade request without a valid JWT is refused before the socket opens.

Deploy it to the VPS server and update the Swagger UI documentation.
