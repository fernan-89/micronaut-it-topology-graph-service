package com.thinklab.domain.exception;

/**
 * Domain Exception: Indicates an illegal lifecycle transition on a {@link com.thinklab.domain.model.Node}
 * or {@link com.thinklab.domain.model.Edge} (for example, retiring an already-RETIRED node, or
 * reactivating an edge whose endpoint is not ACTIVE).
 *
 * <p>RFC 7807 mapping: HTTP 409 Conflict (ADR-019). The request is well formed but collides with
 * the aggregate's current state, the same contract used for every other state conflict on the platform.
 */
public class InvalidNodeStatusException extends BusinessException {

    private static final String ERROR_CODE = "ERR-TPG-00409";

    public InvalidNodeStatusException(String message) {
        super(ERROR_CODE, message);
    }
}
