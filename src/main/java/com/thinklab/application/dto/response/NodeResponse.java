package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** DTO for Node output (DTO Isolation Pattern). */
@Serdeable
public record NodeResponse(UUID id, UUID organisationId, String nodeType, UUID externalId, String label,
                           Map<String, String> attributes, String status, Instant createdAt, Instant updatedAt) {}
