package com.godsplan.payments.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest request) {
        return response(ex.getStatus(), ex.getCode(), ex.getMessage(), List.of(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        var fields = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return response(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "Request validation failed", fields, request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "Required header '" + ex.getHeaderName() + "' is missing",
                List.of(new ApiError.FieldError(ex.getHeaderName(), "is required")), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        var amountError = ex.getMessage() != null && ex.getMessage().contains("BigDecimal");
        return response(HttpStatus.BAD_REQUEST, amountError ? ErrorCode.INVALID_AMOUNT : ErrorCode.VALIDATION_FAILED,
                amountError ? "Amount must be a valid number" : "Request body is malformed", List.of(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "Invalid value for '" + ex.getName() + "'", List.of(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        var fields = ex.getConstraintViolations().stream()
                .map(violation -> new ApiError.FieldError(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return response(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "Request validation failed", fields, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        var traceId = traceId();
        log.error("Unhandled API error traceId={}", traceId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(Instant.now(),
                request.getRequestURI(), ErrorCode.PROCESSING_ERROR.name(),
                "The payment service could not complete the request", List.of(), traceId));
    }

    private ResponseEntity<ApiError> response(HttpStatus status, ErrorCode code, String message,
                                               List<ApiError.FieldError> fields, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), request.getRequestURI(),
                code.name(), message, fields, traceId()));
    }

    private String traceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
