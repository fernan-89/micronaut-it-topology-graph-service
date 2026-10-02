package com.thinklab.domain.model;

import java.time.Instant;

/**
 * Immutable forensic ledger entry shared by {@link Node} and {@link Edge}.
 *
 * @param fromStatus status before the action ({@code null} for the initiating entry)
 * @param toStatus   status after the action (equal to {@code fromStatus} for non-transition actions)
 */
public record AuditEntry(Instant occurredAt, String action, String executor,
                         ElementStatus fromStatus, ElementStatus toStatus, String detail) {}
