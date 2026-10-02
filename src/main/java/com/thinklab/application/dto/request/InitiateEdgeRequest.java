package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** DTO for Edge creation: a directed relationship {@code sourceNodeId -> targetNodeId}. */
@Serdeable
public record InitiateEdgeRequest(

        @NotBlank(message = "Relationship type is required")
        @Size(max = 80, message = "Relationship type must not exceed 80 characters")
        String relationshipType,

        @NotNull(message = "Source node id is required")
        UUID sourceNodeId,

        @NotNull(message = "Target node id is required")
        UUID targetNodeId
) {}
