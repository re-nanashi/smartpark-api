package com.smartpark.api.shared.exception;

import com.smartpark.api.parking.application.exception.TooManyRequestException;
import com.smartpark.api.shared.dto.ApiError;
import com.smartpark.api.shared.dto.ApiErrorFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {
    private final ApiErrorFactory errorFactory;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusinessException(BusinessException ex, WebRequest request) {
        ApiError error = errorFactory.build(
                request,
                HttpStatus.BAD_REQUEST,
                ex.getErrorCode().getValue(),
                ex.getMessage()
        );

        log.warn("{} message={}", ex.getErrorCode(), ex.getMessage());

        return ResponseEntity.status(error.status()).body(error);
    }

    @ExceptionHandler({NotFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiError> handleNotFoundException(Exception ex, WebRequest request) {
        ErrorCode code = ex instanceof ConflictException conflict
                ? conflict.getErrorCode()
                : ErrorCode.RESOURCE_NOT_FOUND;

        ApiError error = errorFactory.build(
                request,
                HttpStatus.NOT_FOUND,
                code.getValue(),
                ex.getMessage()
        );

        log.warn("{} message={}", code, ex.getMessage());

        return ResponseEntity.status(error.status()).body(error);
    }

    @ExceptionHandler({ConflictException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ApiError> handleConflictException(Exception ex, WebRequest request) {
        ErrorCode code = ex instanceof ConflictException conflict
                ? conflict.getErrorCode()
                : ErrorCode.RESOURCE_CONFLICT;

        ApiError error = errorFactory.build(
                request,
                HttpStatus.CONFLICT,
                code.getValue(),
                ex.getMessage()
        );

        log.warn("{} message={}", code, ex.getMessage());

        return ResponseEntity.status(error.status()).body(error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex,
            WebRequest request
    ) {
        ApiError error = errorFactory.build(
                request,
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_INPUT.getValue(),
                "Malformed JSON request"
        );

        log.warn("{} message={}", ErrorCode.INVALID_INPUT, ex.getMessage());

        return ResponseEntity.status(error.status()).body(error);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiError> handleNoHandlerFoundException(
            NoHandlerFoundException ex,
            WebRequest request
    ) {
        String instancePath = ApiErrorFactory.extractUri(request);
        String errorCode = HttpStatus.NOT_FOUND.name();

        ApiError error = errorFactory.build(
                request,
                HttpStatus.NOT_FOUND,
                errorCode,
                "Endpoint '" + instancePath + "' was not found"
        );

        log.warn("{} message={}", errorCode, ex.getMessage());

        return ResponseEntity.status(error.status()).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex,
            WebRequest request
    ) {
        List<Map<String, String>> fieldErrors = new ArrayList<>();

        ex.getBindingResult()
                .getAllErrors()
                .forEach(error -> {
                    String fieldName = error instanceof FieldError fieldError ? fieldError.getField() : "error";
                    String errorMessage = error.getDefaultMessage();
                    assert errorMessage != null;
                    fieldErrors.add(Map.of("field", fieldName, "message", errorMessage));
                });

        ApiError error = ApiError.builder()
                .title(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(ErrorCode.INVALID_INPUT.getValue())
                .fieldErrors(fieldErrors)
                .instance(ApiErrorFactory.extractUri(request))
                .timestamp(Instant.now())
                .build();

        log.warn("{} message={}", ErrorCode.VALIDATION_FAILED, ex.getMessage());

        return ResponseEntity.status(error.status()).body(error);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException ex,
            WebRequest request
    ) {
        ApiError error = ApiError.builder()
                .title(HttpStatus.METHOD_NOT_ALLOWED.getReasonPhrase())
                .status(HttpStatus.METHOD_NOT_ALLOWED.value())
                .error(ErrorCode.METHOD_NOT_ALLOWED.getValue())
                .detail(ex.getMessage())
                .allowedMethods(ex.getSupportedMethods())
                .instance(ApiErrorFactory.extractUri(request))
                .timestamp(Instant.now())
                .build();

        log.warn("{} message={}", ErrorCode.METHOD_NOT_ALLOWED, ex.getMessage());

        return ResponseEntity.status(error.status()).body(error);
    }

    /**
     * Terminal tier of the fallback chain: both Redis lock and DB pessimistic lock failed.
     * Returns {@code Retry-After: 2} so clients know when to retry.
     */
    @ExceptionHandler(TooManyRequestException.class)
    public ResponseEntity<ApiError> handleTooManyRequests(TooManyRequestException ex, WebRequest request) {
        ApiError error = errorFactory.build(
                request,
                HttpStatus.TOO_MANY_REQUESTS,
                ErrorCode.TOO_MANY_REQUESTS.getValue(),
                "Too many requests. Please try again after some time."
        );

        log.warn("Returning 429 – both Redis and DB locks exhausted: {}", ex.getMessage());

        return ResponseEntity.status(error.status()).body(error);
    }

    // Fallback for uncaught exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAllExceptions(
            Exception ex,
            WebRequest request
    ) {
        ApiError error = errorFactory.build(
                request,
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_SERVER_ERROR.getValue(),
                "An unexpected error occurred on our end. We have been notified and are looking into it."
        );

        log.error("Unexpected error occurred on [{}]", ApiErrorFactory.extractUri(request), ex);

        return ResponseEntity.status(error.status()).body(error);
    }
}