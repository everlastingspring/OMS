package com.oms.common.exception;

import com.oms.common.dto.ApiResponse;
import com.oms.common.dto.ValidationError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One place where every exception becomes an ApiResponse with a correct status code.
 * Registered in each service through scanBasePackages on the application class.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex, HttpServletRequest request) {
        log.warn("Business rule rejected {} {} -> {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getErrorCode(), ex.getMessage());
        return build(ex.getStatus(), ApiResponse.error(ex.getMessage(), ex.getErrorCode()), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException ex,
                                                                 HttpServletRequest request) {
        List<ValidationError> errors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.add(new ValidationError(fieldError.getField(),
                    fieldError.getRejectedValue(), fieldError.getDefaultMessage()));
        }
        log.warn("Validation failed for {} {}: {} field(s)",
                request.getMethod(), request.getRequestURI(), errors.size());
        return build(HttpStatus.BAD_REQUEST,
                ApiResponse.error("Validation failed", "VALIDATION_ERROR", errors), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleParamValidation(ConstraintViolationException ex,
                                                                   HttpServletRequest request) {
        List<ValidationError> errors = ex.getConstraintViolations().stream()
                .map(this::toValidationError)
                .collect(Collectors.toList());
        return build(HttpStatus.BAD_REQUEST,
                ApiResponse.error("Validation failed", "VALIDATION_ERROR", errors), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                                  HttpServletRequest request) {
        log.warn("Malformed request body on {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST,
                ApiResponse.error("Request body is missing or malformed", "MALFORMED_REQUEST"), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex,
                                                                HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                ApiResponse.error("Required parameter '" + ex.getParameterName() + "' is missing",
                        "MISSING_PARAMETER"), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                                HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                ApiResponse.error("Parameter '" + ex.getName() + "' has an invalid value: '"
                        + ex.getValue() + "'", "TYPE_MISMATCH"), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex,
                                                                 HttpServletRequest request) {
        log.error("Data integrity violation on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.CONFLICT,
                ApiResponse.error("The request conflicts with existing data", "DATA_INTEGRITY_VIOLATION"), request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                                  HttpServletRequest request) {
        log.warn("Concurrent update rejected on {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT,
                ApiResponse.error("The record was modified by another request. Retry with fresh data.",
                        "CONCURRENT_MODIFICATION"), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex,
                                                                  HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED,
                ApiResponse.error("Authentication failed", "UNAUTHORIZED"), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex,
                                                                HttpServletRequest request) {
        log.warn("Access denied for {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.FORBIDDEN,
                ApiResponse.error("You do not have permission to perform this action", "FORBIDDEN"), request);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandler(NoHandlerFoundException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND,
                ApiResponse.error("No endpoint for " + ex.getHttpMethod() + " " + ex.getRequestURL(),
                        "ENDPOINT_NOT_FOUND"), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                ApiResponse.error("An unexpected error occurred. Contact support with the correlation id.",
                        "INTERNAL_ERROR"), request);
    }

    private ValidationError toValidationError(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath() == null ? "" : violation.getPropertyPath().toString();
        String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
        return new ValidationError(field, violation.getInvalidValue(), violation.getMessage());
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, ApiResponse<Void> body,
                                                    HttpServletRequest request) {
        body.setPath(request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
