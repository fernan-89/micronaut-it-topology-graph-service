package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.mapper.TopologyMapper;
import com.thinklab.domain.exception.NodeNotFoundException;
import com.thinklab.domain.repository.NodeRepository;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/** Node forensic ledger ({@code audit-log/retrieve}); {@code Mono<List>}, never a bare {@code Flux}. */
@Singleton
public class RetrieveNodeAuditLogUseCase {

    private final NodeRepository nodeRepository;

    public RetrieveNodeAuditLogUseCase(NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    public Mono<List<AuditEntryResponse>> execute(UUID id) {
        return nodeRepository.findById(id)
                .switchIfEmpty(Mono.error(new NodeNotFoundException(id)))
                .flatMapMany(node -> Flux.fromIterable(node.getAuditTrail()))
                .map(TopologyMapper::toResponse)
                .collectList();
    }
}
