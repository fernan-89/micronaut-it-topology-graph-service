package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidNodeStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EdgeTest {

    private static final String EXECUTOR = "ops-admin";

    private UUID id;
    private UUID organisationId;
    private UUID source;
    private UUID target;
    private Edge edge;

    @BeforeEach
    void setUp() {
        id = UUID.randomUUID();
        organisationId = UUID.randomUUID();
        source = UUID.randomUUID();
        target = UUID.randomUUID();
        edge = Edge.createNew(id, organisationId, "DEPENDS_ON", source, target, EXECUTOR);
    }

    @Test
    @DisplayName("createNew starts ACTIVE with an INITIATED audit entry")
    void createNew() {
        assertEquals(ElementStatus.ACTIVE, edge.getStatus());
        assertEquals("DEPENDS_ON", edge.getRelationshipType());
        assertEquals(source, edge.getSourceNodeId());
        assertEquals(target, edge.getTargetNodeId());
        assertEquals(1, edge.getAuditTrail().size());
        assertEquals("INITIATED", edge.getAuditTrail().get(0).action());
        assertNull(edge.getAuditTrail().get(0).fromStatus());
        assertThrows(UnsupportedOperationException.class, () -> edge.getAuditTrail().add(null));
    }

    @Test
    @DisplayName("createNew rejects every missing mandatory field and a self-loop")
    void createNewGuards() {
        assertThrows(IllegalArgumentException.class, () -> Edge.createNew(null, organisationId, "t", source, target, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> Edge.createNew(id, null, "t", source, target, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> Edge.createNew(id, organisationId, "t", null, target, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> Edge.createNew(id, organisationId, "t", source, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> Edge.createNew(id, organisationId, null, source, target, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> Edge.createNew(id, organisationId, " ", source, target, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> Edge.createNew(id, organisationId, "t", source, source, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> Edge.createNew(id, organisationId, "t", source, target, null));
        assertThrows(IllegalArgumentException.class, () -> Edge.createNew(id, organisationId, "t", source, target, " "));
    }

    @Test
    @DisplayName("reconstitute restores persisted state and defaults missing status/timestamps/trail")
    void reconstitute() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Instant updated = Instant.parse("2026-02-01T00:00:00Z");
        List<AuditEntry> trail = new ArrayList<>();
        trail.add(new AuditEntry(created, "INITIATED", "x", null, ElementStatus.ACTIVE, "d"));

        Edge restored = Edge.reconstitute(id, organisationId, "HOSTS", source, target, ElementStatus.RETIRED, created, updated, trail);
        assertEquals(ElementStatus.RETIRED, restored.getStatus());
        assertEquals(created, restored.getCreatedAt());
        assertEquals(updated, restored.getUpdatedAt());
        assertEquals(1, restored.getAuditTrail().size());

        Edge defaults = Edge.reconstitute(id, organisationId, "HOSTS", source, target, null, null, null, null);
        assertEquals(ElementStatus.ACTIVE, defaults.getStatus());
        assertNotNull(defaults.getCreatedAt());
        assertEquals(defaults.getCreatedAt(), defaults.getUpdatedAt());
        assertTrue(defaults.getAuditTrail().isEmpty());
        assertEquals(organisationId, defaults.getOrganisationId());
        assertEquals(id, defaults.getId());
    }

    @Test
    @DisplayName("reconstitute rejects missing mandatory persisted fields")
    void reconstituteGuards() {
        assertThrows(IllegalArgumentException.class, () -> Edge.reconstitute(null, organisationId, "t", source, target, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> Edge.reconstitute(id, null, "t", source, target, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> Edge.reconstitute(id, organisationId, null, source, target, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> Edge.reconstitute(id, organisationId, "t", null, target, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> Edge.reconstitute(id, organisationId, "t", source, null, null, null, null, null));
    }

    @Test
    @DisplayName("retire and reactivate move between ACTIVE and RETIRED, recording each transition")
    void lifecycle() {
        AuditEntry retired = edge.retire("a");
        assertEquals(ElementStatus.RETIRED, edge.getStatus());
        assertEquals("RETIRED", retired.action());
        assertEquals(ElementStatus.ACTIVE, retired.fromStatus());

        AuditEntry back = edge.reactivate("b");
        assertEquals(ElementStatus.ACTIVE, edge.getStatus());
        assertEquals("REACTIVATED", back.action());
        assertEquals(3, edge.getAuditTrail().size());
    }

    @Test
    @DisplayName("redundant transitions and missing executors are rejected")
    void lifecycleGuards() {
        assertThrows(InvalidNodeStatusException.class, () -> edge.reactivate(EXECUTOR));
        edge.retire(EXECUTOR);
        assertThrows(InvalidNodeStatusException.class, () -> edge.retire(EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> edge.reactivate(null));
        assertThrows(IllegalArgumentException.class, () -> edge.reactivate(" "));
    }
}
