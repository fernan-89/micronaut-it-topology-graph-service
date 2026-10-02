package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidNodeStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Edge Aggregate: one directed relationship {@code source -> target} between two {@link Node}s,
 * stored exactly once and never mirrored (ADR-031). {@code relationshipType} is an opaque,
 * caller-defined string ("DEPENDS_ON", "HOSTS", ...). Strictly pure Java.
 */
public class Edge {

    private final UUID id;
    private final UUID organisationId;
    private final String relationshipType;
    private final UUID sourceNodeId;
    private final UUID targetNodeId;
    private ElementStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<AuditEntry> auditTrail;

    private Edge(UUID id, UUID organisationId, String relationshipType, UUID sourceNodeId, UUID targetNodeId,
                 ElementStatus status, Instant createdAt, Instant updatedAt, List<AuditEntry> auditTrail) {
        this.id = id;
        this.organisationId = organisationId;
        this.relationshipType = relationshipType;
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
        this.status = status != null ? status : ElementStatus.ACTIVE;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.auditTrail = auditTrail != null ? new ArrayList<>(auditTrail) : new ArrayList<>();
    }

    public static Edge createNew(UUID id, UUID organisationId, String relationshipType, UUID sourceNodeId,
                                 UUID targetNodeId, String executor) {
        if (id == null || organisationId == null || sourceNodeId == null || targetNodeId == null) {
            throw new IllegalArgumentException("ID, Organisation ID, Source and Target Node IDs are mandatory for Edge creation.");
        }
        if (relationshipType == null || relationshipType.isBlank()) {
            throw new IllegalArgumentException("Relationship type is mandatory for Edge creation.");
        }
        if (sourceNodeId.equals(targetNodeId)) {
            throw new IllegalArgumentException("An Edge cannot connect a Node to itself.");
        }
        requireExecutor(executor);
        Instant now = Instant.now();
        Edge edge = new Edge(id, organisationId, relationshipType, sourceNodeId, targetNodeId, ElementStatus.ACTIVE, now, now, null);
        edge.auditTrail.add(new AuditEntry(now, "INITIATED", executor, null, ElementStatus.ACTIVE, "Edge registered in the topology graph."));
        return edge;
    }

    public static Edge reconstitute(UUID id, UUID organisationId, String relationshipType, UUID sourceNodeId,
                                    UUID targetNodeId, ElementStatus status, Instant createdAt, Instant updatedAt,
                                    List<AuditEntry> auditTrail) {
        if (id == null || organisationId == null || relationshipType == null || sourceNodeId == null || targetNodeId == null) {
            throw new IllegalArgumentException("ID, Organisation ID, Relationship Type, Source and Target are mandatory to reconstitute an Edge.");
        }
        return new Edge(id, organisationId, relationshipType, sourceNodeId, targetNodeId, status, createdAt, updatedAt, auditTrail);
    }

    public AuditEntry retire(String executor) {
        return transition(ElementStatus.RETIRED, "RETIRED", executor);
    }

    public AuditEntry reactivate(String executor) {
        return transition(ElementStatus.ACTIVE, "REACTIVATED", executor);
    }

    private AuditEntry transition(ElementStatus target, String action, String executor) {
        requireExecutor(executor);
        if (status == target) {
            throw new InvalidNodeStatusException(String.format(
                    "Idempotency Violation: The Edge is already in the [%s] state.", status));
        }
        ElementStatus previous = status;
        this.status = target;
        this.updatedAt = Instant.now();
        AuditEntry entry = new AuditEntry(updatedAt, action, executor, previous, target,
                String.format("Lifecycle transition %s -> %s.", previous, target));
        auditTrail.add(entry);
        return entry;
    }

    private static void requireExecutor(String executor) {
        if (executor == null || executor.isBlank()) {
            throw new IllegalArgumentException("Executor is mandatory for auditable Edge mutations.");
        }
    }

    public UUID getId() { return id; }
    public UUID getOrganisationId() { return organisationId; }
    public String getRelationshipType() { return relationshipType; }
    public UUID getSourceNodeId() { return sourceNodeId; }
    public UUID getTargetNodeId() { return targetNodeId; }
    public ElementStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<AuditEntry> getAuditTrail() { return Collections.unmodifiableList(auditTrail); }
}
