package com.thinklab.application.usecase;

import com.thinklab.domain.exception.EdgeNotFoundException;
import com.thinklab.domain.exception.InvalidNodeStatusException;
import com.thinklab.domain.exception.NodeNotFoundException;
import com.thinklab.domain.model.AuditEntry;
import com.thinklab.domain.model.Edge;
import com.thinklab.domain.model.ElementStatus;
import com.thinklab.domain.repository.EdgeRepository;
import com.thinklab.domain.repository.NodeRepository;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Edge lifecycle (BIAN Behavior Qualifier: {@code control}). Reactivating an Edge requires both of its
 * endpoint Nodes to be ACTIVE again, so a retired Node can never end up with a live relationship.
 */
@Singleton
public class ControlEdgeUseCase {

    private final EdgeRepository edgeRepository;
    private final NodeRepository nodeRepository;

    public ControlEdgeUseCase(EdgeRepository edgeRepository, NodeRepository nodeRepository) {
        this.edgeRepository = edgeRepository;
        this.nodeRepository = nodeRepository;
    }

    public Mono<Void> execute(UUID id, Action action, String executor) {
        return edgeRepository.findById(id)
                .switchIfEmpty(Mono.error(new EdgeNotFoundException(id)))
                .flatMap(edge -> guard(edge, action)
                        .then(Mono.defer(() -> {
                            AuditEntry entry = action.apply(edge, executor);
                            return edgeRepository.updateStatus(id, action.targetStatus(), entry);
                        })));
    }

    private Mono<Void> guard(Edge edge, Action action) {
        if (action != Action.REACTIVATE) {
            return Mono.empty();
        }
        return Mono.zip(activeEndpoint(edge.getSourceNodeId()), activeEndpoint(edge.getTargetNodeId())).then();
    }

    private Mono<Boolean> activeEndpoint(UUID nodeId) {
        return nodeRepository.findById(nodeId)
                .switchIfEmpty(Mono.error(new NodeNotFoundException(nodeId)))
                .flatMap(node -> node.getStatus() == ElementStatus.ACTIVE
                        ? Mono.just(true)
                        : Mono.error(new InvalidNodeStatusException(String.format(
                                "Compliance Violation: cannot reactivate an Edge while Node [%s] is RETIRED.", nodeId))));
    }

    public enum Action {
        RETIRE(ElementStatus.RETIRED) {
            @Override AuditEntry apply(Edge edge, String executor) { return edge.retire(executor); }
        },
        REACTIVATE(ElementStatus.ACTIVE) {
            @Override AuditEntry apply(Edge edge, String executor) { return edge.reactivate(executor); }
        };

        private final ElementStatus targetStatus;

        Action(ElementStatus targetStatus) {
            this.targetStatus = targetStatus;
        }

        public ElementStatus targetStatus() {
            return targetStatus;
        }

        abstract AuditEntry apply(Edge edge, String executor);
    }
}
