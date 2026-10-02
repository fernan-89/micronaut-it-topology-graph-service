package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.NodeResponse;
import com.thinklab.application.mapper.TopologyMapper;
import com.thinklab.domain.model.ElementStatus;
import com.thinklab.domain.repository.NodeRepository;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

import java.util.UUID;

/** Tenant-scoped Node listing (BIAN Behavior Qualifier: {@code retrieve} - collection). */
@Singleton
public class RetrieveNodesUseCase {

    private final NodeRepository nodeRepository;

    public RetrieveNodesUseCase(NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    public Flux<NodeResponse> execute(UUID organisationId, String nodeType, ElementStatus status) {
        return nodeRepository.findAllByOrganisationId(organisationId, nodeType, status).map(TopologyMapper::toResponse);
    }
}
