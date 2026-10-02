package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateNodeRequest;
import com.thinklab.application.dto.response.NodeResponse;
import com.thinklab.application.mapper.TopologyMapper;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.NodeRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Registers a Node (BIAN Behavior Qualifier: {@code initiate}). Not an upsert: a repeated
 * {@code (organisationId, nodeType, externalId)} is rejected by the unique index with a 409 (ADR-032).
 */
@Singleton
public class InitiateNodeUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateNodeUseCase.class);

    private final HashServicePort hashServicePort;
    private final NodeRepository nodeRepository;

    public InitiateNodeUseCase(HashServicePort hashServicePort, NodeRepository nodeRepository) {
        this.hashServicePort = hashServicePort;
        this.nodeRepository = nodeRepository;
    }

    public Mono<NodeResponse> execute(UUID organisationId, InitiateNodeRequest request, String executor) {
        log.info("[USE CASE] Initiating node for organisation: {} type: {} externalId: {}",
                organisationId, request.nodeType(), request.externalId());

        return hashServicePort.generateSovereignId("node-creation")
                .map(sovereignId -> TopologyMapper.toDomain(request, sovereignId, organisationId, executor))
                .flatMap(nodeRepository::create)
                .map(TopologyMapper::toResponse);
    }
}
