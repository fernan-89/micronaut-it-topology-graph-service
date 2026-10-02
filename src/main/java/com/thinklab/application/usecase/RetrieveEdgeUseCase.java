package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.EdgeResponse;
import com.thinklab.application.mapper.TopologyMapper;
import com.thinklab.domain.exception.EdgeNotFoundException;
import com.thinklab.domain.repository.EdgeRepository;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Single-Edge retrieval (BIAN Behavior Qualifier: {@code retrieve}). */
@Singleton
public class RetrieveEdgeUseCase {

    private final EdgeRepository edgeRepository;

    public RetrieveEdgeUseCase(EdgeRepository edgeRepository) {
        this.edgeRepository = edgeRepository;
    }

    public Mono<EdgeResponse> execute(UUID id) {
        return edgeRepository.findById(id)
                .switchIfEmpty(Mono.error(new EdgeNotFoundException(id)))
                .map(TopologyMapper::toResponse);
    }
}
