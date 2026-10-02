package com.thinklab.application.mapper;

import com.thinklab.application.dto.request.InitiateEdgeRequest;
import com.thinklab.application.dto.request.InitiateNodeRequest;
import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.dto.response.EdgeResponse;
import com.thinklab.application.dto.response.NodeResponse;
import com.thinklab.domain.model.AuditEntry;
import com.thinklab.domain.model.Edge;
import com.thinklab.domain.model.ElementStatus;
import com.thinklab.domain.model.Node;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TopologyMapperTest {

    @Test
    @DisplayName("toDomain(node) builds an ACTIVE aggregate from the request, ID and tenant")
    void nodeToDomain() {
        UUID id = UUID.randomUUID();
        UUID organisationId = UUID.randomUUID();
        UUID externalId = UUID.randomUUID();

        Node node = TopologyMapper.toDomain(new InitiateNodeRequest("ASSET", externalId, "sw", Map.of("k", "v")), id, organisationId, "exec");

        assertEquals(id, node.getId());
        assertEquals(organisationId, node.getOrganisationId());
        assertEquals(externalId, node.getExternalId());
        assertEquals("exec", node.getAuditTrail().get(0).executor());
    }

    @Test
    @DisplayName("toDomain(edge) builds an ACTIVE aggregate from the request, ID and tenant")
    void edgeToDomain() {
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        Edge edge = TopologyMapper.toDomain(new InitiateEdgeRequest("HOSTS", source, target), UUID.randomUUID(), UUID.randomUUID(), "exec");

        assertEquals("HOSTS", edge.getRelationshipType());
        assertEquals(source, edge.getSourceNodeId());
        assertEquals(target, edge.getTargetNodeId());
    }

    @Test
    @DisplayName("toResponse maps every Node and Edge field")
    void toResponses() {
        Node node = Node.createNew(UUID.randomUUID(), UUID.randomUUID(), "ASSET", UUID.randomUUID(), "sw", Map.of("k", "v"), "exec");
        Edge edge = Edge.createNew(UUID.randomUUID(), UUID.randomUUID(), "HOSTS", UUID.randomUUID(), UUID.randomUUID(), "exec");

        NodeResponse nodeResponse = TopologyMapper.toResponse(node);
        EdgeResponse edgeResponse = TopologyMapper.toResponse(edge);

        assertEquals(node.getId(), nodeResponse.id());
        assertEquals("ASSET", nodeResponse.nodeType());
        assertEquals("ACTIVE", nodeResponse.status());
        assertEquals(Map.of("k", "v"), nodeResponse.attributes());
        assertEquals(node.getUpdatedAt(), nodeResponse.updatedAt());
        assertEquals(edge.getId(), edgeResponse.id());
        assertEquals(edge.getSourceNodeId(), edgeResponse.sourceNodeId());
        assertEquals("ACTIVE", edgeResponse.status());
    }

    @Test
    @DisplayName("toResponse(audit entry) tolerates a null fromStatus")
    void auditEntry() {
        Instant now = Instant.now();

        AuditEntryResponse initiating = TopologyMapper.toResponse(new AuditEntry(now, "INITIATED", "x", null, ElementStatus.ACTIVE, "d"));
        AuditEntryResponse transition = TopologyMapper.toResponse(new AuditEntry(now, "RETIRED", "x", ElementStatus.ACTIVE, ElementStatus.RETIRED, "d"));

        assertNull(initiating.fromStatus());
        assertEquals("ACTIVE", initiating.toStatus());
        assertEquals("ACTIVE", transition.fromStatus());
        assertEquals("RETIRED", transition.toStatus());
    }

    @Test
    @DisplayName("the mapper is a non-instantiable utility class")
    void utilityClass() throws Exception {
        Constructor<TopologyMapper> constructor = TopologyMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException ex = assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertInstanceOf(UnsupportedOperationException.class, ex.getCause());
    }
}
