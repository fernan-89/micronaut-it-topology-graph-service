package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

/**
 * One node inside a blast radius: {@code hops} is the minimum number of edges from the root and
 * {@code direction} is DOWNSTREAM, UPSTREAM, or BOTH when reached both ways at that same minimum.
 */
@Serdeable
public record ImpactedNodeResponse(UUID nodeId, String nodeType, UUID externalId, String label, int hops, String direction) {}
