package com.thinklab.domain.exception;

import java.util.Objects;
import java.util.UUID;

/**
 * Domain Exception: Indicates that a requested {@link com.thinklab.domain.model.Node} could not
 * be resolved from the repository.
 *
 * <p>RFC 7807 mapping: HTTP 404 Not Found.
 */
public class NodeNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "ERR-TPG-00404";

    public NodeNotFoundException(UUID id) {
        super(
                ERROR_CODE,
                String.format("Node with sovereign ID [%s] could not be found in the system of record.",
                        Objects.requireNonNull(id, "Domain Exception constraint violated: UUID cannot be null."))
        );
    }

    public NodeNotFoundException(String message) {
        super(ERROR_CODE, message);
    }
}
