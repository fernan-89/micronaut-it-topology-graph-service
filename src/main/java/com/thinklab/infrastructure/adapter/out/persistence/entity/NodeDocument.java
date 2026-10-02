package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.ElementStatus;
import com.thinklab.domain.model.Node;
import io.micronaut.core.annotation.Introspected;
import org.bson.codecs.pojo.annotations.BsonId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Infrastructure-specific MongoDB representation of the Node aggregate (keeps the domain annotation-free). */
@Introspected
public class NodeDocument {

    @BsonId
    private UUID id;
    private UUID organisationId;
    private String nodeType;
    private UUID externalId;
    private String label;
    private Map<String, String> attributes = new LinkedHashMap<>();
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private List<AuditEntryDocument> auditTrail = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganisationId() { return organisationId; }
    public void setOrganisationId(UUID organisationId) { this.organisationId = organisationId; }
    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }
    public UUID getExternalId() { return externalId; }
    public void setExternalId(UUID externalId) { this.externalId = externalId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Map<String, String> getAttributes() { return attributes; }
    public void setAttributes(Map<String, String> attributes) { this.attributes = attributes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<AuditEntryDocument> getAuditTrail() { return auditTrail; }
    public void setAuditTrail(List<AuditEntryDocument> auditTrail) { this.auditTrail = auditTrail; }

    public static final class NodePersistenceMapper {

        private NodePersistenceMapper() { throw new UnsupportedOperationException(); }

        public static NodeDocument toDocument(Node node) {
            NodeDocument doc = new NodeDocument();
            doc.setId(node.getId());
            doc.setOrganisationId(node.getOrganisationId());
            doc.setNodeType(node.getNodeType());
            doc.setExternalId(node.getExternalId());
            doc.setLabel(node.getLabel());
            doc.setAttributes(new LinkedHashMap<>(node.getAttributes()));
            doc.setStatus(node.getStatus().name());
            doc.setCreatedAt(node.getCreatedAt());
            doc.setUpdatedAt(node.getUpdatedAt());
            doc.setAuditTrail(node.getAuditTrail().stream().map(AuditEntryDocument::fromDomain)
                    .collect(Collectors.toCollection(ArrayList::new)));
            return doc;
        }

        public static Node toDomain(NodeDocument doc) {
            ElementStatus status = doc.getStatus() != null ? ElementStatus.valueOf(doc.getStatus()) : ElementStatus.ACTIVE;
            List<com.thinklab.domain.model.AuditEntry> trail = doc.getAuditTrail() != null
                    ? doc.getAuditTrail().stream().map(AuditEntryDocument::toDomain).collect(Collectors.toList())
                    : new ArrayList<>();
            return Node.reconstitute(doc.getId(), doc.getOrganisationId(), doc.getNodeType(), doc.getExternalId(),
                    doc.getLabel(), doc.getAttributes(), status, doc.getCreatedAt(), doc.getUpdatedAt(), trail);
        }
    }
}
