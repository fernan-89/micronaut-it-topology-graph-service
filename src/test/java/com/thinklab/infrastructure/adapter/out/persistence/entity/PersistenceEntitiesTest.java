package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.AuditEntry;
import com.thinklab.domain.model.Edge;
import com.thinklab.domain.model.ElementStatus;
import com.thinklab.domain.model.Node;
import com.thinklab.infrastructure.adapter.out.persistence.entity.EdgeDocument.EdgePersistenceMapper;
import com.thinklab.infrastructure.adapter.out.persistence.entity.NodeDocument.NodePersistenceMapper;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceEntitiesTest {

    @Test
    @DisplayName("Node round-trips through its document including the audit ledger")
    void nodeRoundTrip() {
        Node node = Node.createNew(UUID.randomUUID(), UUID.randomUUID(), "ASSET", UUID.randomUUID(), "sw", Map.of("rack", "A"), "ops");
        node.retire("ops");

        NodeDocument doc = NodePersistenceMapper.toDocument(node);
        Node restored = NodePersistenceMapper.toDomain(doc);

        assertEquals("RETIRED", doc.getStatus());
        assertEquals(2, doc.getAuditTrail().size());
        assertEquals(node.getId(), restored.getId());
        assertEquals(node.getOrganisationId(), restored.getOrganisationId());
        assertEquals("ASSET", restored.getNodeType());
        assertEquals(node.getExternalId(), restored.getExternalId());
        assertEquals("sw", restored.getLabel());
        assertEquals(Map.of("rack", "A"), restored.getAttributes());
        assertEquals(ElementStatus.RETIRED, restored.getStatus());
        assertEquals(node.getCreatedAt(), restored.getCreatedAt());
        assertEquals(node.getUpdatedAt(), restored.getUpdatedAt());
        assertEquals(node.getAuditTrail(), restored.getAuditTrail());
    }

    @Test
    @DisplayName("Node document with missing status/trail defaults to ACTIVE and an empty ledger")
    void nodeDefaults() {
        NodeDocument doc = new NodeDocument();
        doc.setId(UUID.randomUUID());
        doc.setOrganisationId(UUID.randomUUID());
        doc.setNodeType("ASSET");
        doc.setExternalId(UUID.randomUUID());
        doc.setLabel("l");
        doc.setAuditTrail(null);

        Node restored = NodePersistenceMapper.toDomain(doc);

        assertEquals(ElementStatus.ACTIVE, restored.getStatus());
        assertTrue(restored.getAuditTrail().isEmpty());
    }

    @Test
    @DisplayName("Edge round-trips through its document including the audit ledger")
    void edgeRoundTrip() {
        Edge edge = Edge.createNew(UUID.randomUUID(), UUID.randomUUID(), "HOSTS", UUID.randomUUID(), UUID.randomUUID(), "ops");
        edge.retire("ops");

        EdgeDocument doc = EdgePersistenceMapper.toDocument(edge);
        Edge restored = EdgePersistenceMapper.toDomain(doc);

        assertEquals("RETIRED", doc.getStatus());
        assertEquals(edge.getId(), restored.getId());
        assertEquals(edge.getOrganisationId(), restored.getOrganisationId());
        assertEquals("HOSTS", restored.getRelationshipType());
        assertEquals(edge.getSourceNodeId(), restored.getSourceNodeId());
        assertEquals(edge.getTargetNodeId(), restored.getTargetNodeId());
        assertEquals(ElementStatus.RETIRED, restored.getStatus());
        assertEquals(edge.getCreatedAt(), restored.getCreatedAt());
        assertEquals(edge.getUpdatedAt(), restored.getUpdatedAt());
        assertEquals(edge.getAuditTrail(), restored.getAuditTrail());
    }

    @Test
    @DisplayName("Edge document with missing status/trail defaults to ACTIVE and an empty ledger")
    void edgeDefaults() {
        EdgeDocument doc = new EdgeDocument();
        doc.setId(UUID.randomUUID());
        doc.setOrganisationId(UUID.randomUUID());
        doc.setRelationshipType("HOSTS");
        doc.setSourceNodeId(UUID.randomUUID());
        doc.setTargetNodeId(UUID.randomUUID());
        doc.setAuditTrail(null);

        Edge restored = EdgePersistenceMapper.toDomain(doc);

        assertEquals(ElementStatus.ACTIVE, restored.getStatus());
        assertTrue(restored.getAuditTrail().isEmpty());
    }

    @Test
    @DisplayName("AuditEntryDocument converts a null fromStatus both ways")
    void auditEntry() {
        AuditEntry entry = new AuditEntry(Instant.parse("2026-03-01T10:00:00Z"), "INITIATED", "ops", null, ElementStatus.ACTIVE, "d");

        AuditEntryDocument doc = AuditEntryDocument.fromDomain(entry);

        assertNull(doc.fromStatus());
        assertEquals("ACTIVE", doc.toStatus());
        assertEquals(entry, doc.toDomain());

        AuditEntry transition = new AuditEntry(Instant.now(), "RETIRED", "ops", ElementStatus.ACTIVE, ElementStatus.RETIRED, "d");
        assertEquals(transition, AuditEntryDocument.fromDomain(transition).toDomain());
    }

    @Test
    @DisplayName("plain accessors expose what was set (POJO codec contract)")
    void accessors() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        NodeDocument node = new NodeDocument();
        node.setStatus("ACTIVE");
        node.setCreatedAt(now);
        node.setUpdatedAt(now);
        node.setAttributes(Map.of("a", "b"));
        node.setId(id);
        EdgeDocument edge = new EdgeDocument();
        edge.setCreatedAt(now);
        edge.setUpdatedAt(now);
        edge.setStatus("RETIRED");

        assertEquals("ACTIVE", node.getStatus());
        assertEquals(now, node.getCreatedAt());
        assertEquals(now, node.getUpdatedAt());
        assertEquals(Map.of("a", "b"), node.getAttributes());
        assertEquals(id, node.getId());
        assertEquals(now, edge.getCreatedAt());
        assertEquals(now, edge.getUpdatedAt());
        assertEquals("RETIRED", edge.getStatus());
    }

    @Test
    @DisplayName("the persistence mappers are non-instantiable utility classes")
    void utilityClasses() throws Exception {
        for (Class<?> type : new Class<?>[]{NodePersistenceMapper.class, EdgePersistenceMapper.class}) {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            InvocationTargetException ex = assertThrows(InvocationTargetException.class, constructor::newInstance);
            assertInstanceOf(UnsupportedOperationException.class, ex.getCause());
        }
    }
}
