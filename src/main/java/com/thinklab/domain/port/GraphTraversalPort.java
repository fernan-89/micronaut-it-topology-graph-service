package com.thinklab.domain.port;

import com.thinklab.domain.model.TraversalDirection;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Outbound Port: transitive edge traversal from a starting node, tenant-isolated and ACTIVE-only at
 * every hop (ADR-031). Returns raw edge hops; turning them into an impact set is domain/use-case logic.
 */
public interface GraphTraversalPort {

    /**
     * @param depth zero-based hop index of the edge from the start node (0 = directly connected)
     */
    record EdgeHop(UUID sourceNodeId, UUID targetNodeId, int depth) {}

    record TraversalResult(List<EdgeHop> downstream, List<EdgeHop> upstream) {}

    /** Only the passes required by {@code direction} are executed; the other list is empty. */
    Mono<TraversalResult> traverse(UUID organisationId, UUID startNodeId, int maxHops, TraversalDirection direction);
}
