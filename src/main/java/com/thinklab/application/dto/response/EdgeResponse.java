package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

/** DTO for Edge output (DTO Isolation Pattern). */
@Serdeable
public record EdgeResponse(UUID id, UUID organisationId, String relationshipType, UUID sourceNodeId, UUID targetNodeId,
                           String status, Instant createdAt, Instant updatedAt) {}
