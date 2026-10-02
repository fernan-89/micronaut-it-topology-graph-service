package com.thinklab.domain.exception;

/**
 * Domain Exception: an Edge with the same {@code (organisationId, relationshipType, sourceNodeId,
 * targetNodeId)} already exists (ADR-032).
 *
 * <p>RFC 7807 mapping: HTTP 409 Conflict.
 */
public class DuplicateEdgeException extends BusinessException {

    private static final String ERROR_CODE = "ERR-TPG-00409";

    public DuplicateEdgeException(String message) {
        super(ERROR_CODE, message);
    }
}
