package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.EdgeResponse;
import com.thinklab.application.mapper.TopologyMapper;
import com.thinklab.domain.model.ElementStatus;
import com.thinklab.domain.repository.EdgeRepository;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

import java.util.UUID;

/** Tenant-scoped Edge listing; {@code nodeId} matches an Edge on either endpoint. */
@Singleton
public class RetrieveEdgesUseCase {

    private final EdgeRepository edgeRepository;

    public RetrieveEdgesUseCase(EdgeRepository edgeRepository) {
        this.edgeRepository = edgeRepository;
    }

    public Flux<EdgeResponse> execute(UUID organisationId, String relationshipType, UUID nodeId, ElementStatus status) {
        return edgeRepository.findAllByOrganisationId(organisationId, relationshipType, nodeId, status).map(TopologyMapper::toResponse);
    }
}
