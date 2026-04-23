package com.smartpark.api.shared.dto;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;

@Component
public class ApiErrorFactory {
    public ApiError build(
            Object request,
            HttpStatus status,
            String error,
            String detail
    ) {
        String instance = ApiErrorFactory.extractUri(request);

        return ApiError.builder()
                .title(status.getReasonPhrase())
                .status(status.value())
                .error(error)
                .detail(detail)
                .instance(instance)
                .timestamp(Instant.now())
                .build();
    }

    public static String extractUri(Object request) {
        return switch (request) {
            case HttpServletRequest req ->
                    req.getRequestURI();
            case ServletWebRequest req ->
                    req.getRequest().getRequestURI();
            case WebRequest req ->
                // fallback if not ServletWebRequest
                    req.getDescription(false).replace("uri=", "");
            case null, default ->
                    "N/A";
        };
    }
}