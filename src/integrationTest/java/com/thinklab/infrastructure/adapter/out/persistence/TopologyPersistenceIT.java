package com.thinklab.infrastructure.adapter.out.persistence;

import com.mongodb.reactivestreams.client.MongoClient;
import com.thinklab.application.dto.response.BlastRadiusResponse;
import com.thinklab.application.usecase.BlastRadiusUseCase;
import com.thinklab.domain.exception.DuplicateEdgeException;
import com.thinklab.domain.exception.DuplicateNodeException;
import com.thinklab.domain.exception.NodeNotFoundException;
import com.thinklab.domain.model.Edge;
import com.thinklab.domain.model.ElementStatus;
import com.thinklab.domain.model.Node;
import com.thinklab.domain.model.TraversalDirection;
import com.thinklab.domain.repository.EdgeRepository;
import com.thinklab.domain.repository.NodeRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nodes and Edges through their repositories against a real MongoDB, plus the part no mock can prove:
 * the two {@code $graphLookup} passes returning the right transitive set, in the right direction,
 * isolated per tenant and ignoring RETIRED edges (ADR-031).
 */
@MicronautTest(packages = "com.thinklab", transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TopologyPersistenceIT implements TestPropertyProvider {

    private static final String DATABASE = "it_topology_graph_it";
    private static final String EXECUTOR = "topology-admin";

    @Override
    public Map<String, String> getProperties() {
        return Map.of("mongodb.uri", MongoContainer.uri(DATABASE));
    }

    @Inject NodeRepository nodes;
    @Inject EdgeRepository edges;
    @Inject BlastRadiusUseCase blastRadius;
    @Inject MongoClient mongoClient;

    private Node node(UUID organisationId, String label) {
        return nodes.create(Node.createNew(UUID.randomUUID(), organisationId, "ASSET", UUID.randomUUID(), label, Map.of(), EXECUTOR)).block();
    }

    private Edge edge(UUID organisationId, Node source, Node target) {
        return edges.create(Edge.createNew(UUID.randomUUID(), organisationId, "DEPENDS_ON", source.getId(), target.getId(), EXECUTOR)).block();
    }

    private static Map<String, Integer> hopsByLabel(BlastRadiusResponse response) {
        return response.impactedNodes().stream().collect(Collectors.toMap(n -> n.label(), n -> n.hops()));
    }

    @Test
    @DisplayName("a created Node is read back, updated and retired with its ledger")
    void nodeLifecycle() {
        UUID organisationId = UUID.randomUUID();
        Node created = node(organisationId, "sw");

        Node updated = nodes.findById(created.getId()).block();
        AuditEntryHolder.apply(nodes, updated);

        Node found = nodes.findById(created.getId()).block();
        assertEquals(ElementStatus.RETIRED, found.getStatus());
        assertEquals("sw-2", found.getLabel());
        assertEquals(3, found.getAuditTrail().size());
    }

    @Test
    @DisplayName("the unique node key and edge key indexes reject duplicates")
    void uniqueKeys() {
        UUID organisationId = UUID.randomUUID();
        Node a = node(organisationId, "a");
        Node b = node(organisationId, "b");
        edge(organisationId, a, b);

        assertThrows(DuplicateNodeException.class, () -> nodes.create(
                Node.createNew(UUID.randomUUID(), organisationId, a.getNodeType(), a.getExternalId(), "dup", Map.of(), EXECUTOR)).block());
        assertThrows(DuplicateEdgeException.class, () -> edge(organisationId, a, b));
    }

    @Test
    @DisplayName("unknown nodes are empty on read and NodeNotFoundException on update")
    void notFound() {
        UUID unknown = UUID.randomUUID();
        Node ghost = Node.createNew(unknown, UUID.randomUUID(), "ASSET", UUID.randomUUID(), "g", Map.of(), EXECUTOR);

        assertNull(nodes.findById(unknown).block());
        assertThrows(NodeNotFoundException.class, () -> nodes.updateStatus(unknown, ElementStatus.RETIRED, ghost.getAuditTrail().get(0)).block());
    }

    @Test
    @DisplayName("blast radius follows edges in the right direction and honours maxHops")
    void blastRadiusDirections() {
        UUID organisationId = UUID.randomUUID();
        Node web = node(organisationId, "web");
        Node app = node(organisationId, "app");
        Node db = node(organisationId, "db");
        edge(organisationId, web, app);
        edge(organisationId, app, db);

        BlastRadiusResponse downstream = blastRadius.execute(organisationId, web.getId(), 3, TraversalDirection.DOWNSTREAM).block();
        assertEquals(Map.of("app", 1, "db", 2), hopsByLabel(downstream));

        BlastRadiusResponse oneHop = blastRadius.execute(organisationId, web.getId(), 1, TraversalDirection.DOWNSTREAM).block();
        assertEquals(Map.of("app", 1), hopsByLabel(oneHop));

        BlastRadiusResponse upstream = blastRadius.execute(organisationId, db.getId(), 3, TraversalDirection.UPSTREAM).block();
        assertEquals(Map.of("app", 1, "web", 2), hopsByLabel(upstream));

        BlastRadiusResponse both = blastRadius.execute(organisationId, app.getId(), null, TraversalDirection.BOTH).block();
        assertEquals(Map.of("web", 1, "db", 1), hopsByLabel(both));
    }

    @Test
    @DisplayName("blast radius never crosses tenants and ignores RETIRED edges")
    void blastRadiusIsolation() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        Node a1 = node(tenantA, "a1");
        Node a2 = node(tenantA, "a2");
        Node a3 = node(tenantA, "a3");
        Node b1 = node(tenantB, "b1");
        Node b2 = node(tenantB, "b2");
        edge(tenantA, a1, a2);
        Edge retired = edge(tenantA, a2, a3);
        edge(tenantB, b1, b2);
        edges.updateStatus(retired.getId(), ElementStatus.RETIRED, retired.retire(EXECUTOR)).block();

        BlastRadiusResponse response = blastRadius.execute(tenantA, a1.getId(), 5, TraversalDirection.DOWNSTREAM).block();

        assertEquals(Map.of("a2", 1), hopsByLabel(response));
        assertThrows(NodeNotFoundException.class, () -> blastRadius.execute(tenantA, b1.getId(), 3, TraversalDirection.BOTH).block());
    }

    @Test
    @DisplayName("cycles terminate and never report the root")
    void cycles() {
        UUID organisationId = UUID.randomUUID();
        Node x = node(organisationId, "x");
        Node y = node(organisationId, "y");
        edge(organisationId, x, y);
        edge(organisationId, y, x);

        BlastRadiusResponse response = blastRadius.execute(organisationId, x.getId(), 10, TraversalDirection.DOWNSTREAM).block();

        assertEquals(Map.of("y", 1), hopsByLabel(response));
    }

    @Test
    @DisplayName("the uniqueness and traversal indexes exist with the documented key order")
    void indexesExist() {
        node(UUID.randomUUID(), "seed");
        List<Document> edgeIndexes = Flux.from(mongoClient.getDatabase(DATABASE).getCollection("edges").listIndexes()).collectList().block();

        assertTrue(edgeIndexes.stream().anyMatch(i -> new Document("organisationId", 1).append("status", 1).append("sourceNodeId", 1)
                .equals(i.get("key", Document.class))), () -> "edges: " + edgeIndexes);
        assertTrue(edgeIndexes.stream().anyMatch(i -> new Document("organisationId", 1).append("status", 1).append("targetNodeId", 1)
                .equals(i.get("key", Document.class))), () -> "edges: " + edgeIndexes);
    }

    /** Applies update + retire to a node through the repository, the way the use cases do. */
    private static final class AuditEntryHolder {
        static void apply(NodeRepository repository, Node node) {
            repository.updateInfo(node.getId(), "sw-2", Map.of(), node.update("sw-2", Map.of(), EXECUTOR)).block();
            repository.updateStatus(node.getId(), ElementStatus.RETIRED, node.retire(EXECUTOR)).block();
        }
    }
}
