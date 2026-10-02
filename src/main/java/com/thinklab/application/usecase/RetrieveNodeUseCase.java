package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.NodeResponse;
import com.thinklab.application.mapper.TopologyMapper;
import com.thinklab.domain.exception.NodeNotFoundException;
import com.thinklab.domain.repository.NodeRepository;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Single-Node retrieval (BIAN Behavior Qualifier: {@code retrieve}). */
@Singleton
public class RetrieveNodeUseCase {

    private final NodeRepository nodeRepository;

    public RetrieveNodeUseCase(NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    public Mono<NodeResponse> execute(UUID id) {
        return nodeRepository.findById(id)
                .switchIfEmpty(Mono.error(new NodeNotFoundException(id)))
                .map(TopologyMapper::toResponse);
    }
}
