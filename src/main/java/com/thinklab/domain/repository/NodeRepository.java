package com.thinklab.domain.repository;

import com.thinklab.domain.model.AuditEntry;
import com.thinklab.domain.model.ElementStatus;
import com.thinklab.domain.model.Node;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Outbound Port for Node persistence. Partial State Mutations (ADR-002): every mutation is a granular
 * update that atomically appends its audit entry. No physical delete exists.
 */
public interface NodeRepository {

    Mono<Node> create(Node node);

    Mono<Node> findById(UUID id);

    /** Tenant-scoped listing; {@code nodeType} and {@code status} are optional filters. */
    Flux<Node> findAllByOrganisationId(UUID organisationId, String nodeType, ElementStatus status);

    /** Tenant-scoped, ACTIVE-only lookup of the nodes reached by a traversal. */
    Flux<Node> findActiveByOrganisationIdAndIds(UUID organisationId, Collection<UUID> ids);

    Mono<Void> updateInfo(UUID id, String label, Map<String, String> attributes, AuditEntry auditEntry);

    Mono<Void> updateStatus(UUID id, ElementStatus status, AuditEntry auditEntry);
}
