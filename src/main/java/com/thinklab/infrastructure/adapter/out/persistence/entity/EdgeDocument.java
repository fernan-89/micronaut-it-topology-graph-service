package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.AuditEntry;
import com.thinklab.domain.model.Edge;
import com.thinklab.domain.model.ElementStatus;
import io.micronaut.core.annotation.Introspected;
import org.bson.codecs.pojo.annotations.BsonId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Infrastructure-specific MongoDB representation of the Edge aggregate. */
@Introspected
public class EdgeDocument {

    @BsonId
    private UUID id;
    private UUID organisationId;
    private String relationshipType;
    private UUID sourceNodeId;
    private UUID targetNodeId;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private List<AuditEntryDocument> auditTrail = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganisationId() { return organisationId; }
    public void setOrganisationId(UUID organisationId) { this.organisationId = organisationId; }
    public String getRelationshipType() { return relationshipType; }
    public void setRelationshipType(String relationshipType) { this.relationshipType = relationshipType; }
    public UUID getSourceNodeId() { return sourceNodeId; }
    public void setSourceNodeId(UUID sourceNodeId) { this.sourceNodeId = sourceNodeId; }
    public UUID getTargetNodeId() { return targetNodeId; }
    public void setTargetNodeId(UUID targetNodeId) { this.targetNodeId = targetNodeId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<AuditEntryDocument> getAuditTrail() { return auditTrail; }
    public void setAuditTrail(List<AuditEntryDocument> auditTrail) { this.auditTrail = auditTrail; }

    public static final class EdgePersistenceMapper {

        private EdgePersistenceMapper() { throw new UnsupportedOperationException(); }

        public static EdgeDocument toDocument(Edge edge) {
            EdgeDocument doc = new EdgeDocument();
            doc.setId(edge.getId());
            doc.setOrganisationId(edge.getOrganisationId());
            doc.setRelationshipType(edge.getRelationshipType());
            doc.setSourceNodeId(edge.getSourceNodeId());
            doc.setTargetNodeId(edge.getTargetNodeId());
            doc.setStatus(edge.getStatus().name());
            doc.setCreatedAt(edge.getCreatedAt());
            doc.setUpdatedAt(edge.getUpdatedAt());
            doc.setAuditTrail(edge.getAuditTrail().stream().map(AuditEntryDocument::fromDomain)
                    .collect(Collectors.toCollection(ArrayList::new)));
            return doc;
        }

        public static Edge toDomain(EdgeDocument doc) {
            ElementStatus status = doc.getStatus() != null ? ElementStatus.valueOf(doc.getStatus()) : ElementStatus.ACTIVE;
            List<AuditEntry> trail = doc.getAuditTrail() != null
                    ? doc.getAuditTrail().stream().map(AuditEntryDocument::toDomain).collect(Collectors.toList())
                    : new ArrayList<>();
            return Edge.reconstitute(doc.getId(), doc.getOrganisationId(), doc.getRelationshipType(), doc.getSourceNodeId(),
                    doc.getTargetNodeId(), status, doc.getCreatedAt(), doc.getUpdatedAt(), trail);
        }
    }
}
