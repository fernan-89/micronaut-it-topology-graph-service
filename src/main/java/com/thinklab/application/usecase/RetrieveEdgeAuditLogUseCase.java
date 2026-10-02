package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.mapper.TopologyMapper;
import com.thinklab.domain.exception.EdgeNotFoundException;
import com.thinklab.domain.repository.EdgeRepository;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/** Edge forensic ledger ({@code audit-log/retrieve}); {@code Mono<List>}, never a bare {@code Flux}. */
@Singleton
public class RetrieveEdgeAuditLogUseCase {

    private final EdgeRepository edgeRepository;

    public RetrieveEdgeAuditLogUseCase(EdgeRepository edgeRepository) {
        this.edgeRepository = edgeRepository;
    }

    public Mono<List<AuditEntryResponse>> execute(UUID id) {
        return edgeRepository.findById(id)
                .switchIfEmpty(Mono.error(new EdgeNotFoundException(id)))
                .flatMapMany(edge -> Flux.fromIterable(edge.getAuditTrail()))
                .map(TopologyMapper::toResponse)
                .collectList();
    }
}
