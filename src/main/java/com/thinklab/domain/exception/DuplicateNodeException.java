package com.thinklab.domain.exception;

/**
 * Domain Exception: Thrown when a Node is initiated with an {@code (organisationId, nodeType,
 * externalId)} that already exists — the caller should {@code retrieve} the existing Node instead of
 * initiating a duplicate (ADR-032).
 *
 * <p>RFC 7807 mapping: HTTP 409 Conflict.
 */
public class DuplicateNodeException extends BusinessException {

    private static final String ERROR_CODE = "ERR-TPG-00409";

    public DuplicateNodeException(String message) {
        super(ERROR_CODE, message);
    }
}
