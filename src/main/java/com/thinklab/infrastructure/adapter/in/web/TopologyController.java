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
import com.thinklab.domain.model.ElementStatus;
import com.thinklab.domain.model.TraversalDirection;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Inbound Web Adapter for the {@code it-topology-graph} Service Domain (BIAN, ADR-013). {@code Node} is
 * the Control Record and lives at the root; {@code Edge} is a second top-level aggregate of the same
 * Service Domain under the {@code /edge} prefix (same shape as {@code workflow-approval}'s policy/
 * prefix, ADR-032). {@code X-Tenant-Id} is mandatory on {@code initiate} and collection {@code retrieve};
 * {@code X-Executor} on every mutation. There is no {@code DELETE}.
 */
@Controller("/it-topology-graph/v1")
public class TopologyController {

    private static final Logger log = LoggerFactory.getLogger(TopologyController.class);
    static final String TENANT_HEADER = "X-Tenant-Id";
    static final String EXECUTOR_HEADER = "X-Executor";

    private final InitiateNodeUseCase initiateNodeUseCase;
    private final RetrieveNodeUseCase retrieveNodeUseCase;
    private final RetrieveNodesUseCase retrieveNodesUseCase;
    private final UpdateNodeUseCase updateNodeUseCase;
    private final ControlNodeUseCase controlNodeUseCase;
    private final RetrieveNodeAuditLogUseCase retrieveNodeAuditLogUseCase;
    private final BlastRadiusUseCase blastRadiusUseCase;
    private final InitiateEdgeUseCase initiateEdgeUseCase;
    private final RetrieveEdgeUseCase retrieveEdgeUseCase;
    private final RetrieveEdgesUseCase retrieveEdgesUseCase;
    private final ControlEdgeUseCase controlEdgeUseCase;
    private final RetrieveEdgeAuditLogUseCase retrieveEdgeAuditLogUseCase;

    public TopologyController(
            InitiateNodeUseCase initiateNodeUseCase,
            RetrieveNodeUseCase retrieveNodeUseCase,
            RetrieveNodesUseCase retrieveNodesUseCase,
            UpdateNodeUseCase updateNodeUseCase,
            ControlNodeUseCase controlNodeUseCase,
            RetrieveNodeAuditLogUseCase retrieveNodeAuditLogUseCase,
            BlastRadiusUseCase blastRadiusUseCase,
            InitiateEdgeUseCase initiateEdgeUseCase,
            RetrieveEdgeUseCase retrieveEdgeUseCase,
            RetrieveEdgesUseCase retrieveEdgesUseCase,
            ControlEdgeUseCase controlEdgeUseCase,
            RetrieveEdgeAuditLogUseCase retrieveEdgeAuditLogUseCase
    ) {
        this.initiateNodeUseCase = initiateNodeUseCase;
        this.retrieveNodeUseCase = retrieveNodeUseCase;
        this.retrieveNodesUseCase = retrieveNodesUseCase;
        this.updateNodeUseCase = updateNodeUseCase;
        this.controlNodeUseCase = controlNodeUseCase;
        this.retrieveNodeAuditLogUseCase = retrieveNodeAuditLogUseCase;
        this.blastRadiusUseCase = blastRadiusUseCase;
        this.initiateEdgeUseCase = initiateEdgeUseCase;
        this.retrieveEdgeUseCase = retrieveEdgeUseCase;
        this.retrieveEdgesUseCase = retrieveEdgesUseCase;
        this.controlEdgeUseCase = controlEdgeUseCase;
        this.retrieveEdgeAuditLogUseCase = retrieveEdgeAuditLogUseCase;
    }

    // ------------------------------------------------------------------ Node

    @Post("/initiate")
    public Mono<HttpResponse<NodeResponse>> initiate(
            @Header(TENANT_HEADER) @NotBlank String tenantId,
            @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Body @Valid InitiateNodeRequest request
    ) {
        log.info("[ACTION: INITIATE_NODE] [EXECUTOR: {}] organisation: {} type: {}", executor, tenantId, request.nodeType());
        return initiateNodeUseCase.execute(UUID.fromString(tenantId), request, executor).map(HttpResponse::created);
    }

    @Get("/{id}/retrieve")
    public Mono<HttpResponse<NodeResponse>> retrieveById(@PathVariable UUID id) {
        return retrieveNodeUseCase.execute(id).map(HttpResponse::ok);
    }

    @Get("/retrieve")
    public Mono<List<NodeResponse>> retrieveAll(
            @Header(TENANT_HEADER) @NotBlank String tenantId,
            @QueryValue @Nullable String nodeType,
            @QueryValue @Nullable ElementStatus status
    ) {
        return Mono.defer(() -> retrieveNodesUseCase.execute(UUID.fromString(tenantId), nodeType, status).collectList());
    }

    @Put("/{id}/update")
    public Mono<HttpResponse<Void>> update(
            @PathVariable UUID id,
            @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Body @Valid UpdateNodeRequest request
    ) {
        log.info("[ACTION: UPDATE_NODE] [EXECUTOR: {}] node ID: {}", executor, id);
        return updateNodeUseCase.execute(id, request, executor).thenReturn(HttpResponse.noContent());
    }

    @Put("/{id}/control/retire")
    public Mono<HttpResponse<Void>> controlRetire(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        return controlNodeUseCase.execute(id, ControlNodeUseCase.Action.RETIRE, executor).thenReturn(HttpResponse.noContent());
    }

    @Put("/{id}/control/reactivate")
    public Mono<HttpResponse<Void>> controlReactivate(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        return controlNodeUseCase.execute(id, ControlNodeUseCase.Action.REACTIVATE, executor).thenReturn(HttpResponse.noContent());
    }

    @Get("/{id}/audit-log/retrieve")
    public Mono<List<AuditEntryResponse>> retrieveAuditLog(@PathVariable UUID id) {
        return retrieveNodeAuditLogUseCase.execute(id);
    }

    /** Behavior Qualifier: {@code blast-radius/retrieve}. Transitive impact set, tenant-isolated (ADR-031). */
    @Get("/{id}/blast-radius/retrieve")
    public Mono<BlastRadiusResponse> blastRadius(
            @PathVariable UUID id,
            @Header(TENANT_HEADER) @NotBlank String tenantId,
            @QueryValue @Nullable Integer maxHops,
            @QueryValue @Nullable TraversalDirection direction
    ) {
        log.info("[ACTION: BLAST_RADIUS] node ID: {} organisation: {} maxHops: {} direction: {}", id, tenantId, maxHops, direction);
        return Mono.defer(() -> blastRadiusUseCase.execute(UUID.fromString(tenantId), id, maxHops, direction));
    }

    // ------------------------------------------------------------------ Edge

    @Post("/edge/initiate")
    public Mono<HttpResponse<EdgeResponse>> initiateEdge(
            @Header(TENANT_HEADER) @NotBlank String tenantId,
            @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Body @Valid InitiateEdgeRequest request
    ) {
        log.info("[ACTION: INITIATE_EDGE] [EXECUTOR: {}] organisation: {} type: {}", executor, tenantId, request.relationshipType());
        return initiateEdgeUseCase.execute(UUID.fromString(tenantId), request, executor).map(HttpResponse::created);
    }

    @Get("/edge/{id}/retrieve")
    public Mono<HttpResponse<EdgeResponse>> retrieveEdgeById(@PathVariable UUID id) {
        return retrieveEdgeUseCase.execute(id).map(HttpResponse::ok);
    }

    @Get("/edge/retrieve")
    public Mono<List<EdgeResponse>> retrieveAllEdges(
            @Header(TENANT_HEADER) @NotBlank String tenantId,
            @QueryValue @Nullable String relationshipType,
            @QueryValue @Nullable UUID nodeId,
            @QueryValue @Nullable ElementStatus status
    ) {
        return Mono.defer(() -> retrieveEdgesUseCase.execute(UUID.fromString(tenantId), relationshipType, nodeId, status).collectList());
    }

    @Put("/edge/{id}/control/retire")
    public Mono<HttpResponse<Void>> controlEdgeRetire(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        return controlEdgeUseCase.execute(id, ControlEdgeUseCase.Action.RETIRE, executor).thenReturn(HttpResponse.noContent());
    }

    @Put("/edge/{id}/control/reactivate")
    public Mono<HttpResponse<Void>> controlEdgeReactivate(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        return controlEdgeUseCase.execute(id, ControlEdgeUseCase.Action.REACTIVATE, executor).thenReturn(HttpResponse.noContent());
    }

    @Get("/edge/{id}/audit-log/retrieve")
    public Mono<List<AuditEntryResponse>> retrieveEdgeAuditLog(@PathVariable UUID id) {
        return retrieveEdgeAuditLogUseCase.execute(id);
    }
}
