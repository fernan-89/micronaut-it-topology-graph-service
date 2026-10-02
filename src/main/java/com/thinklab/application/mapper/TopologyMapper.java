package com.thinklab.application.mapper;

import com.thinklab.application.dto.request.InitiateEdgeRequest;
import com.thinklab.application.dto.request.InitiateNodeRequest;
import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.dto.response.EdgeResponse;
import com.thinklab.application.dto.response.NodeResponse;
import com.thinklab.domain.model.AuditEntry;
import com.thinklab.domain.model.Edge;
import com.thinklab.domain.model.Node;

import java.util.UUID;

/** Static factory mapper for Node/Edge DTOs and domain entities (DTO Isolation Pattern). */
public final class TopologyMapper {

    private TopologyMapper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static Node toDomain(InitiateNodeRequest request, UUID sovereignId, UUID organisationId, String executor) {
        return Node.createNew(sovereignId, organisationId, request.nodeType(), request.externalId(),
                request.label(), request.attributes(), executor);
    }

    public static Edge toDomain(InitiateEdgeRequest request, UUID sovereignId, UUID organisationId, String executor) {
        return Edge.createNew(sovereignId, organisationId, request.relationshipType(), request.sourceNodeId(),
                request.targetNodeId(), executor);
    }

    public static NodeResponse toResponse(Node node) {
        return new NodeResponse(node.getId(), node.getOrganisationId(), node.getNodeType(), node.getExternalId(),
                node.getLabel(), node.getAttributes(), node.getStatus().name(), node.getCreatedAt(), node.getUpdatedAt());
    }

    public static EdgeResponse toResponse(Edge edge) {
        return new EdgeResponse(edge.getId(), edge.getOrganisationId(), edge.getRelationshipType(), edge.getSourceNodeId(),
                edge.getTargetNodeId(), edge.getStatus().name(), edge.getCreatedAt(), edge.getUpdatedAt());
    }

    public static AuditEntryResponse toResponse(AuditEntry entry) {
        return new AuditEntryResponse(entry.occurredAt(), entry.action(), entry.executor(),
                entry.fromStatus() != null ? entry.fromStatus().name() : null, entry.toStatus().name(), entry.detail());
    }
}
