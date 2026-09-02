package com.oms.common.exception;

import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends BusinessException {

    public ServiceUnavailableException(String service) {
        super(service + " is currently unavailable. Please try again later.",
                HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
    }
}
