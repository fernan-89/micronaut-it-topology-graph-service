package com.thinklab.application.usecase;

import com.thinklab.domain.exception.NodeNotFoundException;
import com.thinklab.domain.model.AuditEntry;
import com.thinklab.domain.model.ElementStatus;
import com.thinklab.domain.model.Node;
import com.thinklab.domain.repository.NodeRepository;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Node lifecycle (BIAN Behavior Qualifier: {@code control}). The domain validates the transition
 * before any granular write. Retiring a Node does not cascade to its Edges: blast-radius lookups only
 * report ACTIVE Nodes, so a retired Node simply disappears from results (ADR-032).
 */
@Singleton
public class ControlNodeUseCase {

    private final NodeRepository nodeRepository;

    public ControlNodeUseCase(NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    public Mono<Void> execute(UUID id, Action action, String executor) {
        return nodeRepository.findById(id)
                .switchIfEmpty(Mono.error(new NodeNotFoundException(id)))
                .flatMap(node -> {
                    AuditEntry entry = action.apply(node, executor);
                    return nodeRepository.updateStatus(id, action.targetStatus(), entry);
                });
    }

    public enum Action {
        RETIRE(ElementStatus.RETIRED) {
            @Override AuditEntry apply(Node node, String executor) { return node.retire(executor); }
        },
        REACTIVATE(ElementStatus.ACTIVE) {
            @Override AuditEntry apply(Node node, String executor) { return node.reactivate(executor); }
        };

        private final ElementStatus targetStatus;

        Action(ElementStatus targetStatus) {
            this.targetStatus = targetStatus;
        }

        public ElementStatus targetStatus() {
            return targetStatus;
        }

        abstract AuditEntry apply(Node node, String executor);
    }
}
