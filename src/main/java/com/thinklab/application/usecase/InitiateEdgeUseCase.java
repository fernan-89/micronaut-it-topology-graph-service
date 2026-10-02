package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateEdgeRequest;
import com.thinklab.application.dto.response.EdgeResponse;
import com.thinklab.application.mapper.TopologyMapper;
import com.thinklab.domain.exception.InvalidNodeStatusException;
import com.thinklab.domain.exception.NodeNotFoundException;
import com.thinklab.domain.model.ElementStatus;
import com.thinklab.domain.model.Node;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.EdgeRepository;
import com.thinklab.domain.repository.NodeRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Registers an Edge. Never a blind write (ADR-032): both endpoint Nodes are loaded first and must exist,
 * belong to the same tenant (a foreign tenant's node is reported as not found, never leaked) and be
 * ACTIVE; only then is a Sovereign ID spent.
 */
@Singleton
public class InitiateEdgeUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateEdgeUseCase.class);

    private final HashServicePort hashServicePort;
    private final NodeRepository nodeRepository;
    private final EdgeRepository edgeRepository;

    public InitiateEdgeUseCase(HashServicePort hashServicePort, NodeRepository nodeRepository, EdgeRepository edgeRepository) {
        this.hashServicePort = hashServicePort;
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
    }

    public Mono<EdgeResponse> execute(UUID organisationId, InitiateEdgeRequest request, String executor) {
        log.info("[USE CASE] Initiating edge for organisation: {} {} -[{}]-> {}",
                organisationId, request.sourceNodeId(), request.relationshipType(), request.targetNodeId());

        return Mono.zip(activeEndpoint(organisationId, request.sourceNodeId()), activeEndpoint(organisationId, request.targetNodeId()))
                .then(Mono.defer(() -> hashServicePort.generateSovereignId("edge-creation")))
                .map(sovereignId -> TopologyMapper.toDomain(request, sovereignId, organisationId, executor))
                .flatMap(edgeRepository::create)
                .map(TopologyMapper::toResponse);
    }

    private Mono<Node> activeEndpoint(UUID organisationId, UUID nodeId) {
        return nodeRepository.findById(nodeId)
                .filter(node -> node.getOrganisationId().equals(organisationId))
                .switchIfEmpty(Mono.error(new NodeNotFoundException(nodeId)))
                .flatMap(node -> node.getStatus() == ElementStatus.ACTIVE
                        ? Mono.just(node)
                        : Mono.error(new InvalidNodeStatusException(String.format(
                                "Compliance Violation: Node [%s] is RETIRED and cannot be an Edge endpoint.", nodeId))));
    }
}
