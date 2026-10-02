package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.InitiateEdgeRequest;
import com.thinklab.application.dto.request.InitiateNodeRequest;
import com.thinklab.application.dto.request.UpdateNodeRequest;
import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.dto.response.BlastRadiusResponse;
import com.thinklab.application.dto.response.EdgeResponse;
import com.thinklab.application.dto.response.NodeResponse;
import com.thinklab.application.usecase.BlastRadiusUseCase;
import com.thinklab.application.usecase.ControlEdgeUseCase;
import com.thinklab.application.usecase.ControlNodeUseCase;
import com.thinklab.application.usecase.InitiateEdgeUseCase;
import com.thinklab.application.usecase.InitiateNodeUseCase;
import com.thinklab.application.usecase.RetrieveEdgeAuditLogUseCase;
import com.thinklab.application.usecase.RetrieveEdgeUseCase;
import com.thinklab.application.usecase.RetrieveEdgesUseCase;
import com.thinklab.application.usecase.RetrieveNodeAuditLogUseCase;
import com.thinklab.application.usecase.RetrieveNodeUseCase;
import com.thinklab.application.usecase.RetrieveNodesUseCase;
import com.thinklab.application.usecase.UpdateNodeUseCase;
import com.thinklab.domain.exception.EdgeNotFoundException;
import com.thinklab.domain.exception.NodeNotFoundException;
import com.thinklab.domain.model.ElementStatus;
import com.thinklab.domain.model.TraversalDirection;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopologyControllerTest {

    private static final String EXECUTOR = "ops-admin";

    @Mock private InitiateNodeUseCase initiateNodeUseCase;
    @Mock private RetrieveNodeUseCase retrieveNodeUseCase;
    @Mock private RetrieveNodesUseCase retrieveNodesUseCase;
    @Mock private UpdateNodeUseCase updateNodeUseCase;
    @Mock private ControlNodeUseCase controlNodeUseCase;
    @Mock private RetrieveNodeAuditLogUseCase retrieveNodeAuditLogUseCase;
    @Mock private BlastRadiusUseCase blastRadiusUseCase;
    @Mock private InitiateEdgeUseCase initiateEdgeUseCase;
    @Mock private RetrieveEdgeUseCase retrieveEdgeUseCase;
    @Mock private RetrieveEdgesUseCase retrieveEdgesUseCase;
    @Mock private ControlEdgeUseCase controlEdgeUseCase;
    @Mock private RetrieveEdgeAuditLogUseCase retrieveEdgeAuditLogUseCase;

    @InjectMocks
    private TopologyController controller;

    private UUID organisationId;
    private UUID id;
    private NodeResponse node;
    private EdgeResponse edge;

    @BeforeEach
    void setUp() {
        organisationId = UUID.randomUUID();
        id = UUID.randomUUID();
        node = new NodeResponse(id, organisationId, "ASSET", UUID.randomUUID(), "sw", Map.of(), "ACTIVE", Instant.now(), Instant.now());
        edge = new EdgeResponse(id, organisationId, "DEPENDS_ON", UUID.randomUUID(), UUID.randomUUID(), "ACTIVE", Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("initiate returns 201 Created and rejects a malformed tenant header")
    void initiate() {
        InitiateNodeRequest request = new InitiateNodeRequest("ASSET", UUID.randomUUID(), "sw", null);
        when(initiateNodeUseCase.execute(organisationId, request, EXECUTOR)).thenReturn(Mono.just(node));

        StepVerifier.create(controller.initiate(organisationId.toString(), EXECUTOR, request))
                .assertNext(r -> {
                    assertEquals(HttpStatus.CREATED, r.getStatus());
                    assertEquals(id, r.body().id());
                })
                .verifyComplete();
        assertThrows(IllegalArgumentException.class, () -> controller.initiate("not-a-uuid", EXECUTOR, request));
    }

    @Test
    @DisplayName("retrieveById returns 200 and surfaces not-found")
    void retrieveById() {
        when(retrieveNodeUseCase.execute(id)).thenReturn(Mono.just(node)).thenReturn(Mono.error(new NodeNotFoundException(id)));

        StepVerifier.create(controller.retrieveById(id)).assertNext(r -> assertEquals(HttpStatus.OK, r.getStatus())).verifyComplete();
        StepVerifier.create(controller.retrieveById(id)).expectError(NodeNotFoundException.class).verify();
    }

    @Test
    @DisplayName("retrieveAll scopes by tenant and forwards filters")
    void retrieveAll() {
        when(retrieveNodesUseCase.execute(organisationId, "ASSET", ElementStatus.ACTIVE)).thenReturn(Flux.just(node));
        when(retrieveNodesUseCase.execute(organisationId, null, null)).thenReturn(Flux.just(node, node));

        StepVerifier.create(controller.retrieveAll(organisationId.toString(), "ASSET", ElementStatus.ACTIVE)).expectNext(List.of(node)).verifyComplete();
        StepVerifier.create(controller.retrieveAll(organisationId.toString(), null, null)).assertNext(l -> assertEquals(2, l.size())).verifyComplete();
    }

    @Test
    @DisplayName("update, retire and reactivate return 204 and dispatch the right action")
    void nodeMutations() {
        UpdateNodeRequest request = new UpdateNodeRequest("sw2", Map.of());
        when(updateNodeUseCase.execute(id, request, EXECUTOR)).thenReturn(Mono.empty());
        when(controlNodeUseCase.execute(eq(id), any(ControlNodeUseCase.Action.class), eq(EXECUTOR))).thenReturn(Mono.empty());

        StepVerifier.create(controller.update(id, EXECUTOR, request)).assertNext(r -> assertEquals(HttpStatus.NO_CONTENT, r.getStatus())).verifyComplete();
        StepVerifier.create(controller.controlRetire(id, EXECUTOR)).assertNext(r -> assertEquals(HttpStatus.NO_CONTENT, r.getStatus())).verifyComplete();
        StepVerifier.create(controller.controlReactivate(id, EXECUTOR)).assertNext(r -> assertEquals(HttpStatus.NO_CONTENT, r.getStatus())).verifyComplete();
        verify(controlNodeUseCase).execute(id, ControlNodeUseCase.Action.RETIRE, EXECUTOR);
        verify(controlNodeUseCase).execute(id, ControlNodeUseCase.Action.REACTIVATE, EXECUTOR);
    }

    @Test
    @DisplayName("retrieveAuditLog lists the node ledger")
    void nodeAuditLog() {
        AuditEntryResponse entry = new AuditEntryResponse(Instant.now(), "INITIATED", EXECUTOR, null, "ACTIVE", "d");
        when(retrieveNodeAuditLogUseCase.execute(id)).thenReturn(Mono.just(List.of(entry)));

        StepVerifier.create(controller.retrieveAuditLog(id)).expectNext(List.of(entry)).verifyComplete();
    }

    @Test
    @DisplayName("blastRadius forwards tenant, maxHops and direction, and rejects a malformed tenant header")
    void blastRadius() {
        BlastRadiusResponse response = new BlastRadiusResponse(id, 3, "BOTH", List.of());
        when(blastRadiusUseCase.execute(organisationId, id, 2, TraversalDirection.UPSTREAM)).thenReturn(Mono.just(response));

        StepVerifier.create(controller.blastRadius(id, organisationId.toString(), 2, TraversalDirection.UPSTREAM)).expectNext(response).verifyComplete();
        StepVerifier.create(controller.blastRadius(id, "not-a-uuid", null, null)).expectError(IllegalArgumentException.class).verify();
    }

    @Test
    @DisplayName("initiateEdge returns 201 Created and rejects a malformed tenant header")
    void initiateEdge() {
        InitiateEdgeRequest request = new InitiateEdgeRequest("DEPENDS_ON", UUID.randomUUID(), UUID.randomUUID());
        when(initiateEdgeUseCase.execute(organisationId, request, EXECUTOR)).thenReturn(Mono.just(edge));

        StepVerifier.create(controller.initiateEdge(organisationId.toString(), EXECUTOR, request))
                .assertNext(r -> assertEquals(HttpStatus.CREATED, r.getStatus())).verifyComplete();
        assertThrows(IllegalArgumentException.class, () -> controller.initiateEdge("not-a-uuid", EXECUTOR, request));
    }

    @Test
    @DisplayName("retrieveEdgeById returns 200 and surfaces not-found")
    void retrieveEdgeById() {
        when(retrieveEdgeUseCase.execute(id)).thenReturn(Mono.just(edge)).thenReturn(Mono.error(new EdgeNotFoundException(id)));

        StepVerifier.create(controller.retrieveEdgeById(id)).assertNext(r -> assertEquals(HttpStatus.OK, r.getStatus())).verifyComplete();
        StepVerifier.create(controller.retrieveEdgeById(id)).expectError(EdgeNotFoundException.class).verify();
    }

    @Test
    @DisplayName("retrieveAllEdges scopes by tenant and forwards filters")
    void retrieveAllEdges() {
        UUID nodeId = UUID.randomUUID();
        when(retrieveEdgesUseCase.execute(organisationId, "DEPENDS_ON", nodeId, ElementStatus.ACTIVE)).thenReturn(Flux.just(edge));
        when(retrieveEdgesUseCase.execute(organisationId, null, null, null)).thenReturn(Flux.empty());

        StepVerifier.create(controller.retrieveAllEdges(organisationId.toString(), "DEPENDS_ON", nodeId, ElementStatus.ACTIVE))
                .expectNext(List.of(edge)).verifyComplete();
        StepVerifier.create(controller.retrieveAllEdges(organisationId.toString(), null, null, null))
                .assertNext(l -> assertEquals(0, l.size())).verifyComplete();
    }

    @Test
    @DisplayName("edge retire and reactivate return 204 and dispatch the right action")
    void edgeMutations() {
        when(controlEdgeUseCase.execute(eq(id), any(ControlEdgeUseCase.Action.class), eq(EXECUTOR))).thenReturn(Mono.empty());

        StepVerifier.create(controller.controlEdgeRetire(id, EXECUTOR)).assertNext(r -> assertEquals(HttpStatus.NO_CONTENT, r.getStatus())).verifyComplete();
        StepVerifier.create(controller.controlEdgeReactivate(id, EXECUTOR)).assertNext(r -> assertEquals(HttpStatus.NO_CONTENT, r.getStatus())).verifyComplete();
        verify(controlEdgeUseCase).execute(id, ControlEdgeUseCase.Action.RETIRE, EXECUTOR);
        verify(controlEdgeUseCase).execute(id, ControlEdgeUseCase.Action.REACTIVATE, EXECUTOR);
    }

    @Test
    @DisplayName("retrieveEdgeAuditLog lists the edge ledger")
    void edgeAuditLog() {
        AuditEntryResponse entry = new AuditEntryResponse(Instant.now(), "INITIATED", EXECUTOR, null, "ACTIVE", "d");
        when(retrieveEdgeAuditLogUseCase.execute(id)).thenReturn(Mono.just(List.of(entry)));

        StepVerifier.create(controller.retrieveEdgeAuditLog(id)).expectNext(List.of(entry)).verifyComplete();
    }
}
