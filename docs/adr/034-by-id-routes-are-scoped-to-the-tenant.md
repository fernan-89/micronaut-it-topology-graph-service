# ADR 034: The by-id routes are scoped to the tenant

## Status
Accepted (2026-11-18). Part of the platform tenant audit (`thinklab-platform/docs/TENANT-AUDIT-2026-11.md`); same rule as workflow-approval ADR-037, change management ADR-038, asset registry ADR-029, hardware maintenance ADR-036, operation window ADR-022, discovery ADR-034, the site directory ADR-033 and ci-type-catalog ADR-034.

## Context
The by-id routes of nodes and edges (retrieve, update, control, audit log) found the object by id alone. The cross-tenant probe (`scripts/e2e/tenant-probe.sh` of the platform) showed a node of tenant X answering 200 to tenant Y. Only blast-radius, which already took the tenant, compared it after loading (ADR-031).

## Decision
- Every by-id route takes `X-Tenant-Id` (required, 400 if blank) and finds the node or edge only in that tenant: another tenant's object is the same as none (404 `ERR-TPG-00404`), nothing is read or written.
- `NodeRepository` and `EdgeRepository` have no unscoped `findById`; the filter in the adapter is `_id` and `organisationId` together. The old load-then-compare in blast-radius and in edge initiate became the same scoped lookup.
- Reactivating an edge looks at its endpoints in the same tenant.

## Consequences
- Positive: a UUID is no longer enough to read or change another tenant's topology.
- Negative: a client that sends no tenant on a by-id route now gets a 400.
