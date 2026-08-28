package com.oms.common.exception;

import org.springframework.http.HttpStatus;

/** The request was well-formed but the domain refuses it in the current state. */
public class InvalidOperationException extends BusinessException {

    public InvalidOperationException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "INVALID_OPERATION");
    }

    public InvalidOperationException(String message, String errorCode) {
        super(message, HttpStatus.BAD_REQUEST, errorCode);
    }
}
