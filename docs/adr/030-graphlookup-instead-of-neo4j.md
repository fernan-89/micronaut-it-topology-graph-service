# ADR-030: MongoDB `$graphLookup` Instead of Neo4j for the Topology Graph

## Status
Accepted

## Context
The topology graph answers one question that matters operationally: "if this node goes down (or is
decommissioned), what else is affected?" — a bounded transitive closure over directed relationships.
A dedicated graph database (Neo4j) is the textbook tool, but this platform runs on exactly one
persistence technology (MongoDB) in every Service Domain, with a single, well-understood operational,
backup, testing (Testcontainers) and CI story. Adding Neo4j means a second database to run, secure,
back up, containerize and test, for a v1 whose graphs are small (a home lab or one organisation's
estate) and whose only traversal is a hop-limited impact query.

## Decision
The graph lives in MongoDB (`nodes` and `edges` collections) and the blast radius is computed with the
`$graphLookup` aggregation stage. Hop count is always capped (default 3, hard cap 10), which keeps each
traversal bounded regardless of graph size, and `restrictSearchWithMatch` pins the tenant and ACTIVE
status on every recursion step (ADR-031).

Neo4j remains a legitimate future evolution if real traversal needs outgrow what `$graphLookup`
delivers (path queries, weighted shortest paths, very deep or very large graphs). Because callers only
see the `GraphTraversalPort`, replacing the adapter would not touch the use cases or the HTTP contract.

## Consequences
- Positive: no new infrastructure; one database technology, one test harness, one backup story.
- Positive: the traversal is covered by the same Testcontainers integration suite as the rest of the
  persistence layer, against a real MongoDB, which is the only place `$graphLookup` semantics can be
  proven.
- Negative: no native path queries or graph algorithms; `$graphLookup` is breadth-first with a depth
  limit and returns edges, not paths, so richer questions need new application-side logic or a different
  engine.
