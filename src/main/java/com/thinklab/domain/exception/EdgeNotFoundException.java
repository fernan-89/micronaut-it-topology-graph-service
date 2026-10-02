package com.thinklab.domain.exception;

import java.util.Objects;
import java.util.UUID;

/**
 * Domain Exception: a requested {@link com.thinklab.domain.model.Edge} could not be resolved.
 *
 * <p>RFC 7807 mapping: HTTP 404 Not Found.
 */
public class EdgeNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "ERR-TPG-00404";

    public EdgeNotFoundException(UUID id) {
        super(ERROR_CODE, String.format("Edge with sovereign ID [%s] could not be found in the system of record.",
                Objects.requireNonNull(id, "Domain Exception constraint violated: UUID cannot be null.")));
    }
}
