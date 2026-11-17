# ADR 033: Node and edge writes are guarded writes

## Status
Accepted (2026-11-17). Found by the platform race audit (ACTION-PLAN 0.5, `thinklab-platform/docs/race-audit.md`).

## Context
A node or edge change read the element, validated it in the domain and wrote it by `_id` only. Measured by `TransitionRaceIT` on the old code: ten
retirements of one node at once all answered OK and left eleven entries in the trail.

## Decision
`Node.updateStatus`, `Node.updateInfo` and `Edge.updateStatus` are **guarded writes** on the status the caller read (the `fromStatus` of the audit entry
they carry, so no use case changed): no match is `409 ERR-TPG-00409` when the element exists and `404` when it does not (ADR-019).

## Consequences
- **Positive:** one winner per race; the stored status and the trail agree.
- **Not changed:** reactivating an edge checks that both of its nodes are active in the use case; a node retired between that check and the edge write is
  not caught (the two documents are separate by design, ADR-032). The blast radius only follows ACTIVE nodes, so such an edge is simply not followed.
