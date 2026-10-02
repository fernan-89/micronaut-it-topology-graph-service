package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateEdgeRequest;
import com.thinklab.application.dto.request.InitiateNodeRequest;
import com.thinklab.application.dto.request.UpdateNodeRequest;
import com.thinklab.application.dto.response.ImpactedNodeResponse;
import com.thinklab.domain.exception.EdgeNotFoundException;
import com.thinklab.domain.exception.InvalidNodeStatusException;
import com.thinklab.domain.exception.NodeNotFoundException;
import com.thinklab.domain.model.Edge;
import com.thinklab.domain.model.ElementStatus;
import com.thinklab.domain.model.Node;
import com.thinklab.domain.model.TraversalDirection;
import com.thinklab.domain.port.GraphTraversalPort;
import com.thinklab.domain.port.GraphTraversalPort.EdgeHop;
import com.thinklab.domain.port.GraphTraversalPort.TraversalResult;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.EdgeRepository;
import com.thinklab.domain.repository.NodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopologyUseCaseTest {

    private static final String EXECUTOR = "ops-admin";

    @Mock private NodeRepository nodeRepository;
    @Mock private EdgeRepository edgeRepository;
    @Mock private HashServicePort hashServicePort;
    @Mock private GraphTraversalPort graphTraversalPort;

    private UUID organisationId;
    private Node source;
    private Node target;
    private Edge edge;

    private Node node(String label) {
        return Node.createNew(UUID.randomUUID(), organisationId, "ASSET", UUID.randomUUID(), label, Map.of(), EXECUTOR);
    }

    @BeforeEach
    void setUp() {
        organisationId = UUID.randomUUID();
        source = node("source");
        target = node("target");
        edge = Edge.createNew(UUID.randomUUID(), organisationId, "DEPENDS_ON", source.getId(), target.getId(), EXECUTOR);
    }

    // ---------------------------------------------------------------- nodes

    @Test
    @DisplayName("Initiate node: spends a Sovereign ID, persists and returns the response")
    void initiateNode() {
        UUID sovereignId = UUID.randomUUID();
        when(hashServicePort.generateSovereignId("node-creation")).thenReturn(Mono.just(sovereignId));
        when(nodeRepository.create(any(Node.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(new InitiateNodeUseCase(hashServicePort, nodeRepository)
                        .execute(organisationId, new InitiateNodeRequest("ASSET", UUID.randomUUID(), "sw", null), EXECUTOR))
                .assertNext(response -> {
                    assertEquals(sovereignId, response.id());
                    assertEquals("ACTIVE", response.status());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Retrieve node: found, and 404 when absent")
    void retrieveNode() {
        when(nodeRepository.findById(source.getId())).thenReturn(Mono.just(source));
        when(nodeRepository.findById(target.getId())).thenReturn(Mono.empty());
        RetrieveNodeUseCase useCase = new RetrieveNodeUseCase(nodeRepository);

        StepVerifier.create(useCase.execute(source.getId())).assertNext(r -> assertEquals("source", r.label())).verifyComplete();
        StepVerifier.create(useCase.execute(target.getId())).expectError(NodeNotFoundException.class).verify();
    }

    @Test
    @DisplayName("Retrieve nodes: forwards tenant, type and status filters")
    void retrieveNodes() {
        when(nodeRepository.findAllByOrganisationId(organisationId, "ASSET", ElementStatus.ACTIVE)).thenReturn(Flux.just(source));
        when(nodeRepository.findAllByOrganisationId(organisationId, null, null)).thenReturn(Flux.empty());
        RetrieveNodesUseCase useCase = new RetrieveNodesUseCase(nodeRepository);

        StepVerifier.create(useCase.execute(organisationId, "ASSET", ElementStatus.ACTIVE)).expectNextCount(1).verifyComplete();
        StepVerifier.create(useCase.execute(organisationId, null, null)).verifyComplete();
    }

    @Test
    @DisplayName("Update node: persists with its audit entry, 404 when absent, 409 when RETIRED")
    void updateNode() {
        when(nodeRepository.findById(source.getId())).thenReturn(Mono.just(source));
        when(nodeRepository.findById(target.getId())).thenReturn(Mono.empty());
        when(nodeRepository.updateInfo(eq(source.getId()), eq("renamed"), any(), any())).thenReturn(Mono.empty());
        UpdateNodeUseCase useCase = new UpdateNodeUseCase(nodeRepository);

        StepVerifier.create(useCase.execute(source.getId(), new UpdateNodeRequest("renamed", Map.of()), EXECUTOR)).verifyComplete();
        StepVerifier.create(useCase.execute(target.getId(), new UpdateNodeRequest("x", Map.of()), EXECUTOR))
                .expectError(NodeNotFoundException.class).verify();

        source.retire(EXECUTOR);
        StepVerifier.create(useCase.execute(source.getId(), new UpdateNodeRequest("again", Map.of()), EXECUTOR))
                .expectError(InvalidNodeStatusException.class).verify();
    }

    @Test
    @DisplayName("Control node: actions map to their target status; illegal and missing nodes never write")
    void controlNode() {
        assertEquals(ElementStatus.RETIRED, ControlNodeUseCase.Action.RETIRE.targetStatus());
        assertEquals(ElementStatus.ACTIVE, ControlNodeUseCase.Action.REACTIVATE.targetStatus());
        when(nodeRepository.findById(source.getId())).thenReturn(Mono.just(source));
        when(nodeRepository.findById(target.getId())).thenReturn(Mono.empty());
        when(nodeRepository.updateStatus(eq(source.getId()), any(ElementStatus.class), any())).thenReturn(Mono.empty());
        ControlNodeUseCase useCase = new ControlNodeUseCase(nodeRepository);

        StepVerifier.create(useCase.execute(source.getId(), ControlNodeUseCase.Action.RETIRE, EXECUTOR)).verifyComplete();
        StepVerifier.create(useCase.execute(source.getId(), ControlNodeUseCase.Action.REACTIVATE, EXECUTOR)).verifyComplete();
        StepVerifier.create(useCase.execute(source.getId(), ControlNodeUseCase.Action.RETIRE, EXECUTOR)).verifyComplete();
        StepVerifier.create(useCase.execute(source.getId(), ControlNodeUseCase.Action.RETIRE, EXECUTOR))
                .expectError(InvalidNodeStatusException.class).verify();
        StepVerifier.create(useCase.execute(target.getId(), ControlNodeUseCase.Action.REACTIVATE, EXECUTOR))
                .expectError(NodeNotFoundException.class).verify();
        verify(nodeRepository, org.mockito.Mockito.times(2)).updateStatus(eq(source.getId()), eq(ElementStatus.RETIRED), any());
        verify(nodeRepository).updateStatus(eq(source.getId()), eq(ElementStatus.ACTIVE), any());
    }

    @Test
    @DisplayName("Node audit log: lists the ledger in order, 404 when absent")
    void nodeAuditLog() {
        source.retire(EXECUTOR);
        when(nodeRepository.findById(source.getId())).thenReturn(Mono.just(source));
        when(nodeRepository.findById(target.getId())).thenReturn(Mono.empty());
        RetrieveNodeAuditLogUseCase useCase = new RetrieveNodeAuditLogUseCase(nodeRepository);

        StepVerifier.create(useCase.execute(source.getId()))
                .assertNext(entries -> {
                    assertEquals(2, entries.size());
                    assertEquals("INITIATED", entries.get(0).action());
                    assertEquals("RETIRED", entries.get(1).action());
                })
                .verifyComplete();
        StepVerifier.create(useCase.execute(target.getId())).expectError(NodeNotFoundException.class).verify();
    }

    // ---------------------------------------------------------------- edges

    @Test
    @DisplayName("Initiate edge: both endpoints ACTIVE in the tenant -> Sovereign ID spent, edge persisted")
    void initiateEdge() {
        UUID sovereignId = UUID.randomUUID();
        when(nodeRepository.findById(source.getId())).thenReturn(Mono.just(source));
        when(nodeRepository.findById(target.getId())).thenReturn(Mono.just(target));
        when(hashServicePort.generateSovereignId("edge-creation")).thenReturn(Mono.just(sovereignId));
        when(edgeRepository.create(any(Edge.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(new InitiateEdgeUseCase(hashServicePort, nodeRepository, edgeRepository)
                        .execute(organisationId, new InitiateEdgeRequest("DEPENDS_ON", source.getId(), target.getId()), EXECUTOR))
                .assertNext(response -> assertEquals(sovereignId, response.id()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Initiate edge: a missing endpoint is 404 and never spends a Sovereign ID")
    void initiateEdgeMissingEndpoint() {
        when(nodeRepository.findById(source.getId())).thenReturn(Mono.just(source));
        when(nodeRepository.findById(target.getId())).thenReturn(Mono.empty());

        StepVerifier.create(new InitiateEdgeUseCase(hashServicePort, nodeRepository, edgeRepository)
                        .execute(organisationId, new InitiateEdgeRequest("DEPENDS_ON", source.getId(), target.getId()), EXECUTOR))
                .expectError(NodeNotFoundException.class).verify();

        verifyNoInteractions(hashServicePort);
        verify(edgeRepository, never()).create(any());
    }

    @Test
    @DisplayName("Initiate edge: an endpoint of another tenant is reported as not found")
    void initiateEdgeForeignTenant() {
        Node foreign = Node.createNew(UUID.randomUUID(), UUID.randomUUID(), "ASSET", UUID.randomUUID(), "foreign", Map.of(), EXECUTOR);
        when(nodeRepository.findById(source.getId())).thenReturn(Mono.just(source));
        when(nodeRepository.findById(foreign.getId())).thenReturn(Mono.just(foreign));

        StepVerifier.create(new InitiateEdgeUseCase(hashServicePort, nodeRepository, edgeRepository)
                        .execute(organisationId, new InitiateEdgeRequest("DEPENDS_ON", source.getId(), foreign.getId()), EXECUTOR))
                .expectError(NodeNotFoundException.class).verify();
        verifyNoInteractions(hashServicePort);
    }

    @Test
    @DisplayName("Initiate edge: a RETIRED endpoint is a 409 and never spends a Sovereign ID")
    void initiateEdgeRetiredEndpoint() {
        target.retire(EXECUTOR);
        when(nodeRepository.findById(source.getId())).thenReturn(Mono.just(source));
        when(nodeRepository.findById(target.getId())).thenReturn(Mono.just(target));

        StepVerifier.create(new InitiateEdgeUseCase(hashServicePort, nodeRepository, edgeRepository)
                        .execute(organisationId, new InitiateEdgeRequest("DEPENDS_ON", source.getId(), target.getId()), EXECUTOR))
                .expectError(InvalidNodeStatusException.class).verify();
        verifyNoInteractions(hashServicePort);
    }

    @Test
    @DisplayName("Retrieve edge: found, and 404 when absent")
    void retrieveEdge() {
        when(edgeRepository.findById(edge.getId())).thenReturn(Mono.just(edge));
        UUID unknown = UUID.randomUUID();
        when(edgeRepository.findById(unknown)).thenReturn(Mono.empty());
        RetrieveEdgeUseCase useCase = new RetrieveEdgeUseCase(edgeRepository);

        StepVerifier.create(useCase.execute(edge.getId())).assertNext(r -> assertEquals("DEPENDS_ON", r.relationshipType())).verifyComplete();
        StepVerifier.create(useCase.execute(unknown)).expectError(EdgeNotFoundException.class).verify();
    }

    @Test
    @DisplayName("Retrieve edges: forwards tenant, type, node and status filters")
    void retrieveEdges() {
        when(edgeRepository.findAllByOrganisationId(organisationId, "DEPENDS_ON", source.getId(), ElementStatus.ACTIVE)).thenReturn(Flux.just(edge));
        when(edgeRepository.findAllByOrganisationId(organisationId, null, null, null)).thenReturn(Flux.empty());
        RetrieveEdgesUseCase useCase = new RetrieveEdgesUseCase(edgeRepository);

        StepVerifier.create(useCase.execute(organisationId, "DEPENDS_ON", source.getId(), ElementStatus.ACTIVE)).expectNextCount(1).verifyComplete();
        StepVerifier.create(useCase.execute(organisationId, null, null, null)).verifyComplete();
    }

    @Test
    @DisplayName("Control edge: retire needs no endpoint check; reactivate needs both endpoints ACTIVE")
    void controlEdge() {
        assertEquals(ElementStatus.RETIRED, ControlEdgeUseCase.Action.RETIRE.targetStatus());
        assertEquals(ElementStatus.ACTIVE, ControlEdgeUseCase.Action.REACTIVATE.targetStatus());
        when(edgeRepository.findById(edge.getId())).thenReturn(Mono.just(edge));
        when(edgeRepository.updateStatus(eq(edge.getId()), any(), any())).thenReturn(Mono.empty());
        when(nodeRepository.findById(source.getId())).thenReturn(Mono.just(source));
        when(nodeRepository.findById(target.getId())).thenReturn(Mono.just(target));
        ControlEdgeUseCase useCase = new ControlEdgeUseCase(edgeRepository, nodeRepository);

        StepVerifier.create(useCase.execute(edge.getId(), ControlEdgeUseCase.Action.RETIRE, EXECUTOR)).verifyComplete();
        StepVerifier.create(useCase.execute(edge.getId(), ControlEdgeUseCase.Action.REACTIVATE, EXECUTOR)).verifyComplete();
        verify(edgeRepository).updateStatus(eq(edge.getId()), eq(ElementStatus.RETIRED), any());
        verify(edgeRepository).updateStatus(eq(edge.getId()), eq(ElementStatus.ACTIVE), any());
    }

    @Test
    @DisplayName("Control edge: reactivation is refused while an endpoint is RETIRED or missing, and an unknown edge is 404")
    void controlEdgeGuards() {
        edge.retire(EXECUTOR);
        when(edgeRepository.findById(edge.getId())).thenReturn(Mono.just(edge));
        UUID unknown = UUID.randomUUID();
        when(edgeRepository.findById(unknown)).thenReturn(Mono.empty());
        when(nodeRepository.findById(source.getId())).thenReturn(Mono.just(source));
        when(nodeRepository.findById(target.getId())).thenReturn(Mono.just(target)).thenReturn(Mono.empty());
        target.retire(EXECUTOR);
        ControlEdgeUseCase useCase = new ControlEdgeUseCase(edgeRepository, nodeRepository);

        StepVerifier.create(useCase.execute(edge.getId(), ControlEdgeUseCase.Action.REACTIVATE, EXECUTOR))
                .expectError(InvalidNodeStatusException.class).verify();
        StepVerifier.create(useCase.execute(edge.getId(), ControlEdgeUseCase.Action.REACTIVATE, EXECUTOR))
                .expectError(NodeNotFoundException.class).verify();
        StepVerifier.create(useCase.execute(unknown, ControlEdgeUseCase.Action.RETIRE, EXECUTOR))
                .expectError(EdgeNotFoundException.class).verify();
        verify(edgeRepository, never()).updateStatus(any(), any(), any());
    }

    @Test
    @DisplayName("Edge audit log: lists the ledger, 404 when absent")
    void edgeAuditLog() {
        when(edgeRepository.findById(edge.getId())).thenReturn(Mono.just(edge));
        UUID unknown = UUID.randomUUID();
        when(edgeRepository.findById(unknown)).thenReturn(Mono.empty());
        RetrieveEdgeAuditLogUseCase useCase = new RetrieveEdgeAuditLogUseCase(edgeRepository);

        StepVerifier.create(useCase.execute(edge.getId())).assertNext(entries -> assertEquals(1, entries.size())).verifyComplete();
        StepVerifier.create(useCase.execute(unknown)).expectError(EdgeNotFoundException.class).verify();
    }

    // ---------------------------------------------------------------- blast radius

    private BlastRadiusUseCase blastRadius() {
        return new BlastRadiusUseCase(nodeRepository, graphTraversalPort);
    }

    private void stubReached(Node... reached) {
        when(nodeRepository.findActiveByOrganisationIdAndIds(eq(organisationId), anyCollection())).thenReturn(Flux.just(reached));
    }

    @Test
    @DisplayName("Blast radius: reduces hops to one entry per node with its minimum hops, ordered by hops then label")
    void blastRadiusDownstream() {
        Node a = node("alpha");
        Node b = node("beta");
        when(nodeRepository.findById(source.getId())).thenReturn(Mono.just(source));
        when(graphTraversalPort.traverse(organisationId, source.getId(), 3, TraversalDirection.DOWNSTREAM)).thenReturn(Mono.just(new TraversalResult(
                List.of(new EdgeHop(source.getId(), b.getId(), 1), new EdgeHop(source.getId(), a.getId(), 0), new EdgeHop(a.getId(), b.getId(), 1),
                        new EdgeHop(b.getId(), source.getId(), 2)),
                List.of())));
        stubReached(b, a);

        StepVerifier.create(blastRadius().execute(organisationId, source.getId(), null, TraversalDirection.DOWNSTREAM))
                .assertNext(response -> {
                    assertEquals(3, response.maxHops());
                    assertEquals("DOWNSTREAM", response.direction());
                    List<ImpactedNodeResponse> impacted = response.impactedNodes();
                    assertEquals(2, impacted.size());
                    assertEquals(a.getId(), impacted.get(0).nodeId());
                    assertEquals(1, impacted.get(0).hops());
                    assertEquals("DOWNSTREAM", impacted.get(0).direction());
                    assertEquals(b.getId(), impacted.get(1).nodeId());
                    assertEquals(2, impacted.get(1).hops());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Blast radius: BOTH classifies a node as DOWNSTREAM, UPSTREAM or BOTH by where its minimum hops are reached")
    void blastRadiusBoth() {
        Node same = node("both");
        Node downFirst = node("down-first");
        Node upFirst = node("up-first");
        Node upOnly = node("up-only");
        when(nodeRepository.findById(source.getId())).thenReturn(Mono.just(source));
        when(graphTraversalPort.traverse(organisationId, source.getId(), 4, TraversalDirection.BOTH)).thenReturn(Mono.just(new TraversalResult(
                List.of(new EdgeHop(source.getId(), same.getId(), 0), new EdgeHop(source.getId(), downFirst.getId(), 0),
                        new EdgeHop(source.getId(), upFirst.getId(), 2)),
                List.of(new EdgeHop(same.getId(), source.getId(), 0), new EdgeHop(downFirst.getId(), source.getId(), 2),
                        new EdgeHop(upFirst.getId(), source.getId(), 0), new EdgeHop(upOnly.getId(), source.getId(), 1)))));
        stubReached(same, downFirst, upFirst, upOnly);

        StepVerifier.create(blastRadius().execute(organisationId, source.getId(), 4, null))
                .assertNext(response -> {
                    assertEquals("BOTH", response.direction());
                    Map<String, String> directions = new java.util.HashMap<>();
                    response.impactedNodes().forEach(n -> directions.put(n.label(), n.direction() + ":" + n.hops()));
                    assertEquals("BOTH:1", directions.get("both"));
                    assertEquals("DOWNSTREAM:1", directions.get("down-first"));
                    assertEquals("UPSTREAM:1", directions.get("up-first"));
                    assertEquals("UPSTREAM:2", directions.get("up-only"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Blast radius: a node that does not exist or belongs to another tenant is 404 and never traversed")
    void blastRadiusNotFound() {
        Node foreign = Node.createNew(UUID.randomUUID(), UUID.randomUUID(), "ASSET", UUID.randomUUID(), "foreign", Map.of(), EXECUTOR);
        when(nodeRepository.findById(source.getId())).thenReturn(Mono.empty());
        when(nodeRepository.findById(foreign.getId())).thenReturn(Mono.just(foreign));

        StepVerifier.create(blastRadius().execute(organisationId, source.getId(), 2, TraversalDirection.BOTH))
                .expectError(NodeNotFoundException.class).verify();
        StepVerifier.create(blastRadius().execute(organisationId, foreign.getId(), 2, TraversalDirection.BOTH))
                .expectError(NodeNotFoundException.class).verify();
        verifyNoInteractions(graphTraversalPort);
    }

    @Test
    @DisplayName("Blast radius: maxHops outside 1..10 is rejected before touching any repository")
    void blastRadiusInvalidHops() {
        StepVerifier.create(blastRadius().execute(organisationId, source.getId(), 0, null)).expectError(IllegalArgumentException.class).verify();
        StepVerifier.create(blastRadius().execute(organisationId, source.getId(), 11, null)).expectError(IllegalArgumentException.class).verify();
        verifyNoInteractions(nodeRepository, graphTraversalPort);
    }
}
