package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidNodeStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core Domain Model representing the Node Aggregate Root of the {@code it-topology-graph} Service
 * Domain: a vertex of the infrastructure graph. {@code nodeType} is an opaque, caller-defined string
 * ("ASSET", "SITE", ...) and {@code externalId} is the id of the entity that owns the node in its own
 * Service Domain (ADR-032). Strictly pure Java.
 */
public class Node {

    private final UUID id;
    private final UUID organisationId;
    private final String nodeType;
    private final UUID externalId;
    private String label;
    private Map<String, String> attributes;
    private ElementStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<AuditEntry> auditTrail;

    private Node(UUID id, UUID organisationId, String nodeType, UUID externalId, String label,
                 Map<String, String> attributes, ElementStatus status, Instant createdAt, Instant updatedAt,
                 List<AuditEntry> auditTrail) {
        this.id = id;
        this.organisationId = organisationId;
        this.nodeType = nodeType;
        this.externalId = externalId;
        this.label = label;
        this.attributes = copy(attributes);
        this.status = status != null ? status : ElementStatus.ACTIVE;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.auditTrail = auditTrail != null ? new ArrayList<>(auditTrail) : new ArrayList<>();
    }

    public static Node createNew(UUID id, UUID organisationId, String nodeType, UUID externalId, String label,
                                 Map<String, String> attributes, String executor) {
        if (id == null || organisationId == null || externalId == null) {
            throw new IllegalArgumentException("ID, Organisation ID and External ID are mandatory for Node creation.");
        }
        if (nodeType == null || nodeType.isBlank() || label == null || label.isBlank()) {
            throw new IllegalArgumentException("Node type and label are mandatory for Node creation.");
        }
        requireExecutor(executor);
        Instant now = Instant.now();
        Node node = new Node(id, organisationId, nodeType, externalId, label, attributes, ElementStatus.ACTIVE, now, now, null);
        node.auditTrail.add(new AuditEntry(now, "INITIATED", executor, null, ElementStatus.ACTIVE, "Node registered in the topology graph."));
        return node;
    }

    public static Node reconstitute(UUID id, UUID organisationId, String nodeType, UUID externalId, String label,
                                    Map<String, String> attributes, ElementStatus status, Instant createdAt,
                                    Instant updatedAt, List<AuditEntry> auditTrail) {
        if (id == null || organisationId == null || nodeType == null || externalId == null || label == null) {
            throw new IllegalArgumentException("ID, Organisation ID, Node Type, External ID and Label are mandatory to reconstitute a Node.");
        }
        return new Node(id, organisationId, nodeType, externalId, label, attributes, status, createdAt, updatedAt, auditTrail);
    }

    /** Behavior Qualifier: {@code update}. Only legal while ACTIVE. */
    public AuditEntry update(String newLabel, Map<String, String> newAttributes, String executor) {
        requireExecutor(executor);
        if (status == ElementStatus.RETIRED) {
            throw new InvalidNodeStatusException("Compliance Violation: cannot update a RETIRED node; reactivate it first.");
        }
        if (newLabel == null || newLabel.isBlank()) {
            throw new IllegalArgumentException("Label cannot be empty.");
        }
        this.label = newLabel;
        this.attributes = copy(newAttributes);
        return record("UPDATED", executor, status, "Descriptive information updated.");
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
                    "Idempotency Violation: The Node is already in the [%s] state.", status));
        }
        ElementStatus previous = status;
        this.status = target;
        return record(action, executor, previous, String.format("Lifecycle transition %s -> %s.", previous, target));
    }

    private AuditEntry record(String action, String executor, ElementStatus from, String detail) {
        this.updatedAt = Instant.now();
        AuditEntry entry = new AuditEntry(updatedAt, action, executor, from, status, detail);
        auditTrail.add(entry);
        return entry;
    }

    private static void requireExecutor(String executor) {
        if (executor == null || executor.isBlank()) {
            throw new IllegalArgumentException("Executor is mandatory for auditable Node mutations.");
        }
    }

    private static Map<String, String> copy(Map<String, String> map) {
        return map == null ? new LinkedHashMap<>() : new LinkedHashMap<>(map);
    }

    public UUID getId() { return id; }
    public UUID getOrganisationId() { return organisationId; }
    public String getNodeType() { return nodeType; }
    public UUID getExternalId() { return externalId; }
    public String getLabel() { return label; }
    public Map<String, String> getAttributes() { return Collections.unmodifiableMap(attributes); }
    public ElementStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<AuditEntry> getAuditTrail() { return Collections.unmodifiableList(auditTrail); }
}
