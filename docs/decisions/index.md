# Architectural Decision Log

This is the index of Architectural Decision Records (ADRs) for this project, kept in the
[MADR](https://adr.github.io/madr/) format. Each row links to one decision record. New decisions are
appended with the next incrementing ID; accepted records are an immutable historical log — a changed
decision is captured as a new ADR that supersedes the old one.

| ID   | Title                                                                                            | Status   | Date       |
| ---- | ------------------------------------------------------------------------------------------------ | -------- | ---------- |
| 0001 | [Server arbitrates which timer is running, never how long](0001-server-authoritative-active-timer.md) | accepted | 2026-09-22 |
| 0002 | [A server-assigned sequence cursor orders the change feed](0002-sequence-cursor-delta-sync.md)    | accepted | 2026-09-22 |
| 0003 | [The interval merge asks the outbox, not the shape of the row](0003-outbox-driven-interval-merge.md) | accepted | 2026-09-22 |
| 0004 | [The backend is the authority on Pro entitlement](0004-server-enforced-entitlement-via-revenuecat.md) | proposed | 2026-09-22 |

The first four all come from the cross-device timer sync work (issue 45). 0001–0003 are implemented
and verified against a deployed backend; 0004 is specified but not built.
