package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidNodeStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeTest {

    private static final String EXECUTOR = "ops-admin";

    private UUID id;
    private UUID organisationId;
    private UUID externalId;
    private Node node;

    @BeforeEach
    void setUp() {
        id = UUID.randomUUID();
        organisationId = UUID.randomUUID();
        externalId = UUID.randomUUID();
        node = Node.createNew(id, organisationId, "ASSET", externalId, "core-switch", Map.of("rack", "A1"), EXECUTOR);
    }

    @Test
    @DisplayName("createNew starts ACTIVE with an INITIATED audit entry")
    void createNew() {
        assertEquals(ElementStatus.ACTIVE, node.getStatus());
        assertEquals("ASSET", node.getNodeType());
        assertEquals(externalId, node.getExternalId());
        assertEquals("A1", node.getAttributes().get("rack"));
        assertEquals(1, node.getAuditTrail().size());
        AuditEntry first = node.getAuditTrail().get(0);
        assertEquals("INITIATED", first.action());
        assertNull(first.fromStatus());
        assertEquals(ElementStatus.ACTIVE, first.toStatus());
        assertEquals(node.getCreatedAt(), node.getUpdatedAt());
    }

    @Test
    @DisplayName("createNew accepts null attributes and rejects every missing mandatory field")
    void createNewGuards() {
        assertTrue(Node.createNew(id, organisationId, "ASSET", externalId, "l", null, EXECUTOR).getAttributes().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> Node.createNew(null, organisationId, "t", externalId, "l", null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> Node.createNew(id, null, "t", externalId, "l", null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> Node.createNew(id, organisationId, "t", null, "l", null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> Node.createNew(id, organisationId, null, externalId, "l", null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> Node.createNew(id, organisationId, " ", externalId, "l", null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> Node.createNew(id, organisationId, "t", externalId, null, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> Node.createNew(id, organisationId, "t", externalId, " ", null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> Node.createNew(id, organisationId, "t", externalId, "l", null, null));
        assertThrows(IllegalArgumentException.class, () -> Node.createNew(id, organisationId, "t", externalId, "l", null, " "));
    }

    @Test
    @DisplayName("exposed views are read-only")
    void readOnlyViews() {
        assertThrows(UnsupportedOperationException.class, () -> node.getAttributes().put("x", "y"));
        assertThrows(UnsupportedOperationException.class, () -> node.getAuditTrail().add(null));
    }

    @Test
    @DisplayName("reconstitute restores persisted state and defaults missing status/timestamps/trail")
    void reconstitute() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Instant updated = Instant.parse("2026-02-01T00:00:00Z");
        List<AuditEntry> trail = new ArrayList<>();
        trail.add(new AuditEntry(created, "INITIATED", "x", null, ElementStatus.ACTIVE, "d"));

        Node restored = Node.reconstitute(id, organisationId, "ASSET", externalId, "l", Map.of("a", "b"),
                ElementStatus.RETIRED, created, updated, trail);
        assertEquals(ElementStatus.RETIRED, restored.getStatus());
        assertEquals(created, restored.getCreatedAt());
        assertEquals(updated, restored.getUpdatedAt());
        assertEquals(1, restored.getAuditTrail().size());

        Node defaults = Node.reconstitute(id, organisationId, "ASSET", externalId, "l", null, null, null, null, null);
        assertEquals(ElementStatus.ACTIVE, defaults.getStatus());
        assertNotNull(defaults.getCreatedAt());
        assertEquals(defaults.getCreatedAt(), defaults.getUpdatedAt());
        assertTrue(defaults.getAuditTrail().isEmpty());
        assertTrue(defaults.getAttributes().isEmpty());
    }

    @Test
    @DisplayName("reconstitute rejects missing mandatory persisted fields")
    void reconstituteGuards() {
        assertThrows(IllegalArgumentException.class, () -> Node.reconstitute(null, organisationId, "t", externalId, "l", null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> Node.reconstitute(id, null, "t", externalId, "l", null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> Node.reconstitute(id, organisationId, null, externalId, "l", null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> Node.reconstitute(id, organisationId, "t", null, "l", null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> Node.reconstitute(id, organisationId, "t", externalId, null, null, null, null, null, null));
    }

    @Test
    @DisplayName("update replaces label/attributes and appends an UPDATED entry")
    void update() {
        AuditEntry entry = node.update("core-switch-2", Map.of("rack", "B2"), "tech");

        assertEquals("core-switch-2", node.getLabel());
        assertEquals("B2", node.getAttributes().get("rack"));
        assertEquals("UPDATED", entry.action());
        assertEquals(ElementStatus.ACTIVE, entry.fromStatus());
        assertEquals(2, node.getAuditTrail().size());
    }

    @Test
    @DisplayName("update rejects a blank label, a missing executor and a RETIRED node")
    void updateGuards() {
        assertThrows(IllegalArgumentException.class, () -> node.update(" ", Map.of(), EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> node.update(null, Map.of(), EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> node.update("l", Map.of(), null));
        node.retire(EXECUTOR);
        InvalidNodeStatusException ex = assertThrows(InvalidNodeStatusException.class, () -> node.update("l", Map.of(), EXECUTOR));
        assertEquals("ERR-TPG-00409", ex.getErrorCode());
    }

    @Test
    @DisplayName("retire and reactivate move between ACTIVE and RETIRED, recording each transition")
    void lifecycle() {
        AuditEntry retired = node.retire("a");
        assertEquals(ElementStatus.RETIRED, node.getStatus());
        assertEquals(ElementStatus.ACTIVE, retired.fromStatus());
        assertEquals("RETIRED", retired.action());

        AuditEntry back = node.reactivate("b");
        assertEquals(ElementStatus.ACTIVE, node.getStatus());
        assertEquals("REACTIVATED", back.action());
        assertEquals(3, node.getAuditTrail().size());
    }

    @Test
    @DisplayName("redundant transitions and missing executors are rejected")
    void lifecycleGuards() {
        assertThrows(InvalidNodeStatusException.class, () -> node.reactivate(EXECUTOR));
        node.retire(EXECUTOR);
        assertThrows(InvalidNodeStatusException.class, () -> node.retire(EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> node.reactivate(null));
        assertThrows(IllegalArgumentException.class, () -> node.reactivate(" "));
    }
}
