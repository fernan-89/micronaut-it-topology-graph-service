# ADR-031: Each Edge Is Stored Once; Bidirectional Traversal Is Two `$graphLookup` Passes

## Status
Accepted

## Context
Impact analysis is bidirectional. Given an edge `A DEPENDS_ON B`, the DOWNSTREAM question ("what does A
depend on?") follows source -> target, and the UPSTREAM question ("what is affected if B goes down?")
follows target -> source. `$graphLookup` recurses in a single direction per stage.

One way to get both directions is to write each relationship twice (a forward document and a mirrored
reverse document with a flag). That doubles every write and creates a dual-write consistency problem:
a `retire` or `reactivate` that updates one copy and not the other leaves the graph silently
inconsistent, and nothing notices until an impact query returns a wrong answer. The platform already
avoids this class of problem elsewhere (the transactional outbox lesson).

## Decision
1. **A single directed document per relationship** `source -> target`, never mirrored. Uniqueness is
   enforced by a unique index on `(organisationId, relationshipType, sourceNodeId, targetNodeId)`, so a
   duplicate edge is a 409 (`ERR-TPG-00409`), also under concurrency.
2. **Two separate `$graphLookup` passes** over the same collection, run only for the requested
   direction: DOWNSTREAM uses `connectFromField: targetNodeId`, `connectToField: sourceNodeId`; UPSTREAM
   is the reverse.
3. **Tenant isolation and ACTIVE status are enforced on every recursion step** via
   `restrictSearchWithMatch: {organisationId, status: "ACTIVE"}`, not only on the starting document; the
   starting node must also belong to the caller's tenant (a foreign node is a 404, never leaked).
4. **Index per traversal direction**, matching the exact key order of each pass:
   `{organisationId, status, sourceNodeId}` and `{organisationId, status, targetNodeId}`.
5. **Reduction happens in the use case, not the database**: the port returns raw edge hops; the use case
   keeps the minimum hop count per node, drops the root (cycles), and reports a node reached both ways
   at the same minimum as `BOTH`. Only ACTIVE nodes are reported.
6. `maxHops` is 1..10 (default 3); anything else is a 400.

## Consequences
- Positive: one write per relationship, one source of truth, no mirror to keep in sync.
- Positive: the traversal logic that can silently be wrong (direction, tenant, retired edges, cycles) is
  isolated in two small, separately tested pieces (adapter pipeline, use-case reduction) plus a real
  MongoDB integration test.
- Negative: a BOTH query costs two aggregation passes instead of one. Accepted; both are bounded by
  `maxHops` and served by dedicated indexes.
