package com.smartpark.api.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;

@Builder
public record ApiError(
        String title,
        int status,
        String error,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        String detail,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        Object allowedMethods,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        Object fieldErrors,

        String instance,
        Instant timestamp
) {}