# Thinklab IT Topology Graph Service

**Version:** v1.0.0-BIAN

**Status:** Reference implementation (ThinkLab portfolio project)

## Overview

The Thinklab IT Topology Graph Service records how configuration items relate to each other and answers
the question that matters when something fails or is retired: **what is the blast radius?** (BIAN
`it-topology-graph`). A `Node` is a vertex (an Asset, a Site, anything a caller wants to model); an
`Edge` is one directed relationship `source -> target` ("web DEPENDS_ON app"). Both are registered
through this service's own API; `nodeType` and `relationshipType` are opaque, caller-defined strings
(ADR-032).

The graph lives in MongoDB and the impact set is computed with `$graphLookup`, not a graph database
(ADR-030). Each relationship is stored once and traversed in either direction with two separate passes,
tenant-isolated and ACTIVE-only at every hop (ADR-031).

Built with Java 21 and Micronaut 4.4.2 on a strict Hexagonal Architecture and a fully reactive stack
(Project Reactor, reactive MongoDB driver).

## Technology Stack

* **Runtime:** Java 21 LTS
* **Framework:** Micronaut 4.4.2 (AOT optimized, reflection-free DI and Serde)
* **Reactive Engine:** Project Reactor (Mono / Flux)
* **Persistence:** Reactive MongoDB (`thinklab_topology_graph_db`, collections `nodes` and `edges`), BSON UUID standard representation, unique node/edge keys and two traversal indexes
* **Graph traversal:** MongoDB `$graphLookup`, bounded by `maxHops` (default 3, cap 10)
* **Observability:** W3C Trace Context, SLF4J/Logback, Reactor MDC bridge
* **Containerization:** Google Distroless (nonroot), read-only root filesystem
* **Testing:** JUnit 5, Mockito, Reactor Test; Testcontainers suite that proves the traversal against a real MongoDB
* **Documentation:** OpenAPI 3.0 / Swagger generated at compile time

## Domain Model

```text
Node { id, organisationId, nodeType, externalId, label, attributes{}, status, createdAt, updatedAt, auditTrail[] }
Edge { id, organisationId, relationshipType, sourceNodeId, targetNodeId, status, createdAt, updatedAt, auditTrail[] }
status: ACTIVE | RETIRED  (reversible, never a physical delete)
```

Unique keys: a Node per `(organisationId, nodeType, externalId)`, an Edge per
`(organisationId, relationshipType, sourceNodeId, targetNodeId)`. An Edge needs two existing, ACTIVE
Nodes of the caller's tenant and cannot be a self-loop; reactivating an Edge needs both endpoints ACTIVE.
Retiring a Node does not cascade to its Edges: impact results only list ACTIVE Nodes.

## BIAN Behavior Qualifier Contract (`/it-topology-graph/v1`)

`X-Tenant-Id` is mandatory on `initiate`, collection `retrieve` and `blast-radius/retrieve`;
`X-Executor` is mandatory on every mutation. There is no `DELETE`.

| Behavior Qualifier | Method & Path |
|---|---|
| initiate (Node) | `POST /it-topology-graph/v1/initiate` |
| retrieve (Node) | `GET /it-topology-graph/v1/{id}/retrieve` |
| retrieve (Nodes, filters `nodeType`, `status`) | `GET /it-topology-graph/v1/retrieve` |
| update (Node) | `PUT /it-topology-graph/v1/{id}/update` |
| control/retire, control/reactivate (Node) | `PUT /it-topology-graph/v1/{id}/control/{action}` |
| audit-log/retrieve (Node) | `GET /it-topology-graph/v1/{id}/audit-log/retrieve` |
| **blast-radius/retrieve** | `GET /it-topology-graph/v1/{id}/blast-radius/retrieve?maxHops=&direction=BOTH\|UPSTREAM\|DOWNSTREAM` |
| initiate (Edge) | `POST /it-topology-graph/v1/edge/initiate` |
| retrieve (Edge) | `GET /it-topology-graph/v1/edge/{id}/retrieve` |
| retrieve (Edges, filters `relationshipType`, `nodeId`, `status`) | `GET /it-topology-graph/v1/edge/retrieve` |
| control/retire, control/reactivate (Edge) | `PUT /it-topology-graph/v1/edge/{id}/control/{action}` |
| audit-log/retrieve (Edge) | `GET /it-topology-graph/v1/edge/{id}/audit-log/retrieve` |

`blast-radius/retrieve` returns every ACTIVE Node reachable from the root within `maxHops` edges, with its
minimum hop count and a `direction`: `DOWNSTREAM` (what the root depends on), `UPSTREAM` (what depends on
the root) or `BOTH` when reached both ways at the same minimum.

### Error catalog (RFC 7807, `error_code` field)

| error_code | HTTP | Meaning |
|---|---|---|
| `ERR-TPG-00404` | 404 | Node or Edge not found (also: a node of another tenant) |
| `ERR-TPG-00409` | 409 | Duplicate node/edge key, illegal lifecycle transition, or a RETIRED endpoint (ADR-019/032) |
| `ERR-VALIDATION-00400` | 400 | Payload/header/identifier validation failure, self-loop, or `maxHops` outside 1..10 |
| `ERR-INTERNAL-00500` | 500 | Unexpected technical failure |

Example:

```bash
curl "http://localhost:8092/it-topology-graph/v1/<node-id>/blast-radius/retrieve?direction=UPSTREAM&maxHops=3" \
  -H "X-Tenant-Id: 6f1c7a52-3d0b-4a44-9c3e-0a7d1f6e2b10"
```

## Operational Procedures

```bash
# Build, run AOT optimizations and test
./gradlew clean build

# Start the service (default port 8092)
./gradlew run

# Container image
docker build -t thinklab-it-topology-graph-service:latest .
```

* **Health:** `http://localhost:8092/health`
* **Swagger UI:** `http://localhost:8092/swagger-ui`
* **Postman suite:** `docs/postman/` (graph build-up, real blast-radius assertions, negatives)

### Configuration

| Variable | Default | Purpose |
|---|---|---|
| `MICRONAUT_SERVER_PORT` | `8092` | HTTP port |
| `MONGODB_URI` | `mongodb://localhost:27017/thinklab_topology_graph_db` | MongoDB connection |
| `HASH_SERVICE_URL` | `http://localhost:8080` | Hash Token Registry base URL |

## Architecture Decision Records

`docs/adr/`: 001 hexagonal reactive stack · 005 UUID identity sovereignty · 013 BIAN service domain
conventions · 019 HTTP 409 for state conflicts · 030 `$graphLookup` instead of Neo4j · 031 edge stored
once, bidirectional traversal in two passes · 032 two top-level aggregates and opaque caller-defined types.

### Automated Tests

```bash
./gradlew test                          # unit suite + 100% line/branch coverage gate (no Docker needed)
./gradlew integrationTest               # Testcontainers suite against a real MongoDB (needs Docker)
./gradlew check                         # both, as CI runs it
```

## License

Licensed under the [PolyForm Strict License 1.0.0](LICENSE): you may read and use this software for noncommercial purposes only. Modifying it, creating derivative works, redistributing it and any commercial use are not permitted without a separate written license. This software is not open source.
