package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.UpdateNodeRequest;
import com.thinklab.domain.exception.NodeNotFoundException;
import com.thinklab.domain.model.AuditEntry;
import com.thinklab.domain.repository.NodeRepository;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Updates a Node's descriptive information (BIAN Behavior Qualifier: {@code update}). */
@Singleton
public class UpdateNodeUseCase {

    private final NodeRepository nodeRepository;

    public UpdateNodeUseCase(NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    public Mono<Void> execute(UUID id, UpdateNodeRequest request, String executor) {
        return nodeRepository.findById(id)
                .switchIfEmpty(Mono.error(new NodeNotFoundException(id)))
                .flatMap(node -> {
                    AuditEntry entry = node.update(request.label(), request.attributes(), executor);
                    return nodeRepository.updateInfo(id, node.getLabel(), node.getAttributes(), entry);
                });
    }
}
