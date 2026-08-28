package com.oms.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base type for every exception the OMS services raise deliberately.
 * Carries the HTTP status and a stable machine-readable error code so the
 * GlobalExceptionHandler does not need a chain of instanceof checks.
 */
@Getter
public abstract class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    protected BusinessException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
}
