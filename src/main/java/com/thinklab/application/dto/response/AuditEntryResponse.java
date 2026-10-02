package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

/** DTO projecting one immutable entry of a Node/Edge forensic audit ledger. */
@Serdeable
public record AuditEntryResponse(Instant occurredAt, String action, String executor,
                                 String fromStatus, String toStatus, String detail) {}
