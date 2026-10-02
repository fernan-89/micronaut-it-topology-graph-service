# ADR-032: Two Top-Level Aggregates in One Service Domain, with Opaque Caller-Defined Types

## Status
Accepted

## Context
The graph needs two kinds of records, Nodes and Edges, each with its own identity, lifecycle and audit
ledger. Neither is a child of the other: an Edge references two Nodes but is not stored inside either.
The platform already has a precedent for two top-level aggregates in one Service Domain:
`workflow-approval` hosts `ApprovalPolicy` and `ApprovalRequest`, one at the root and one under a route
prefix, sharing error codes and a controller.

Separately, the entities that become nodes (Assets, Sites, later others) belong to other Service
Domains, and the kinds of relationship worth modelling are open-ended.

## Decision
1. **Node is the Control Record (root routes); Edge is a second top-level aggregate under `/edge`**, in
   one controller, with shared `ERR-TPG-*` codes, exactly like `workflow-approval`.
2. **`nodeType` and `relationshipType` are opaque, caller-defined strings** ("ASSET", "SITE",
   "DEPENDS_ON", "HOSTS", ...), the same posture as `ApprovalRequest.subjectType`; `externalId` is the
   id of the owning entity in its own Service Domain. This service validates none of them against
   another Service Domain, which keeps it decoupled and lets callers model things it has never heard of.
3. **A Node is unique per `(organisationId, nodeType, externalId)`**, enforced by a unique index. It is
   not an upsert: a repeated registration is a 409 and the caller retrieves the existing Node instead.
4. **An Edge is never a blind write**: both endpoint Nodes are loaded first and must exist, belong to
   the same tenant and be ACTIVE; a self-loop is rejected. Reactivating an Edge requires both endpoints
   to be ACTIVE again.
5. **Retiring a Node does not cascade to its Edges.** Impact queries only report ACTIVE Nodes, so a
   retired Node simply disappears from results; its Edges stay on record and become usable again if the
   Node is reactivated.
6. **v1 is populated only through this service's own API.** Nothing pushes Nodes or Edges from the Asset
   Registry or the Site directory; automatic population is a future journey.

## Consequences
- Positive: a familiar shape for anyone who has read `workflow-approval`; no coupling to other domains'
  taxonomies.
- Positive: no cascading writes, so no partial-cascade failure mode.
- Negative: nothing stops a caller from registering a Node whose `externalId` points at nothing real;
  keeping the graph truthful is the caller's responsibility until automatic population exists.
