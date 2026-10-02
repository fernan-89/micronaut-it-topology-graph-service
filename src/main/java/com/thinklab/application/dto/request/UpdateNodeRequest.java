package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** DTO for updating a Node's descriptive information (BIAN Behavior Qualifier: {@code update}). */
@Serdeable
public record UpdateNodeRequest(

        @NotBlank(message = "Label is required")
        @Size(max = 160, message = "Label must not exceed 160 characters")
        String label,

        Map<String, String> attributes
) {}
