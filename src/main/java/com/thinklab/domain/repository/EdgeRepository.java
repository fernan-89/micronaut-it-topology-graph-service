package com.thinklab.domain.repository;

import com.thinklab.domain.model.AuditEntry;
import com.thinklab.domain.model.Edge;
import com.thinklab.domain.model.ElementStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Outbound Port for Edge persistence (same granular-update rule as {@link NodeRepository}). */
public interface EdgeRepository {

    Mono<Edge> create(Edge edge);

    Mono<Edge> findById(UUID id);

    /**
     * Tenant-scoped listing; every filter except the tenant is optional. {@code nodeId} matches an edge
     * on either endpoint.
     */
    Flux<Edge> findAllByOrganisationId(UUID organisationId, String relationshipType, UUID nodeId, ElementStatus status);

    Mono<Void> updateStatus(UUID id, ElementStatus status, AuditEntry auditEntry);
}
