package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/** DTO for Node creation (BIAN Behavior Qualifier: {@code initiate}). organisationId travels via {@code X-Tenant-Id}. */
@Serdeable
public record InitiateNodeRequest(

        @NotBlank(message = "Node type is required")
        @Size(max = 80, message = "Node type must not exceed 80 characters")
        String nodeType,

        @NotNull(message = "External id is required")
        UUID externalId,

        @NotBlank(message = "Label is required")
        @Size(max = 160, message = "Label must not exceed 160 characters")
        String label,

        Map<String, String> attributes
) {}
