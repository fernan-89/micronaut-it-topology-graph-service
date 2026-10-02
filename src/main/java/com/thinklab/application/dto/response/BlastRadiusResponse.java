package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.UUID;

/** The transitive impact set of a root node, for the requested direction and hop limit. */
@Serdeable
public record BlastRadiusResponse(UUID nodeId, int maxHops, String direction, List<ImpactedNodeResponse> impactedNodes) {}
