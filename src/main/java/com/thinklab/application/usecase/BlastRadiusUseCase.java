package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.BlastRadiusResponse;
import com.thinklab.application.dto.response.ImpactedNodeResponse;
import com.thinklab.domain.exception.NodeNotFoundException;
import com.thinklab.domain.model.TraversalDirection;
import com.thinklab.domain.port.GraphTraversalPort;
import com.thinklab.domain.port.GraphTraversalPort.EdgeHop;
import com.thinklab.domain.repository.NodeRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Blast radius of a Node (BIAN Behavior Qualifier: {@code blast-radius/retrieve}, ADR-031): the
 * transitive set of ACTIVE Nodes reachable DOWNSTREAM (what the node depends on), UPSTREAM (what
 * depends on it) or both, within {@code maxHops} edges (default 3, hard cap 10).
 *
 * <p>The port returns raw edge hops; this use case reduces them to one entry per Node with its minimum
 * hop count. A Node reached both ways at the same minimum is reported as {@code BOTH}.
 */
@Singleton
public class BlastRadiusUseCase {

    public static final int DEFAULT_MAX_HOPS = 3;
    public static final int MAX_HOPS_CAP = 10;

    private static final Logger log = LoggerFactory.getLogger(BlastRadiusUseCase.class);

    private final NodeRepository nodeRepository;
    private final GraphTraversalPort graphTraversalPort;

    public BlastRadiusUseCase(NodeRepository nodeRepository, GraphTraversalPort graphTraversalPort) {
        this.nodeRepository = nodeRepository;
        this.graphTraversalPort = graphTraversalPort;
    }

    public Mono<BlastRadiusResponse> execute(UUID organisationId, UUID nodeId, Integer maxHopsParam, TraversalDirection directionParam) {
        int maxHops = maxHopsParam != null ? maxHopsParam : DEFAULT_MAX_HOPS;
        TraversalDirection direction = directionParam != null ? directionParam : TraversalDirection.BOTH;
        if (maxHops < 1 || maxHops > MAX_HOPS_CAP) {
            return Mono.error(new IllegalArgumentException("maxHops must be between 1 and " + MAX_HOPS_CAP + "."));
        }
        log.info("[USE CASE] Blast radius for node: {} organisation: {} maxHops: {} direction: {}", nodeId, organisationId, maxHops, direction);

        return nodeRepository.findById(nodeId)
                .filter(node -> node.getOrganisationId().equals(organisationId))
                .switchIfEmpty(Mono.error(new NodeNotFoundException(nodeId)))
                .then(Mono.defer(() -> graphTraversalPort.traverse(organisationId, nodeId, maxHops, direction)))
                .flatMap(result -> {
                    Map<UUID, Integer> downstream = minHops(result.downstream(), true, nodeId);
                    Map<UUID, Integer> upstream = minHops(result.upstream(), false, nodeId);
                    Set<UUID> reached = new HashSet<>(downstream.keySet());
                    reached.addAll(upstream.keySet());
                    return nodeRepository.findActiveByOrganisationIdAndIds(organisationId, reached)
                            .map(node -> impacted(node.getId(), node.getNodeType(), node.getExternalId(), node.getLabel(),
                                    downstream.get(node.getId()), upstream.get(node.getId())))
                            .collectList();
                })
                .map(impacted -> {
                    List<ImpactedNodeResponse> sorted = new ArrayList<>(impacted);
                    sorted.sort(Comparator.comparingInt(ImpactedNodeResponse::hops).thenComparing(ImpactedNodeResponse::label));
                    return new BlastRadiusResponse(nodeId, maxHops, direction.name(), sorted);
                });
    }

    private static Map<UUID, Integer> minHops(List<EdgeHop> hops, boolean downstream, UUID root) {
        Map<UUID, Integer> result = new HashMap<>();
        for (EdgeHop hop : hops) {
            UUID reached = downstream ? hop.targetNodeId() : hop.sourceNodeId();
            if (!reached.equals(root)) {
                result.merge(reached, hop.depth() + 1, Math::min);
            }
        }
        return result;
    }

    private static ImpactedNodeResponse impacted(UUID id, String nodeType, UUID externalId, String label, Integer down, Integer up) {
        if (down != null && up != null) {
            String direction = down.equals(up) ? "BOTH" : (down < up ? "DOWNSTREAM" : "UPSTREAM");
            return new ImpactedNodeResponse(id, nodeType, externalId, label, Math.min(down, up), direction);
        }
        return down != null
                ? new ImpactedNodeResponse(id, nodeType, externalId, label, down, "DOWNSTREAM")
                : new ImpactedNodeResponse(id, nodeType, externalId, label, up, "UPSTREAM");
    }
}
