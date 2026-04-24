package com.smartpark.api.parking.application.exception;

/** Thrown when all lock tiers fail; maps to HTTP 429 with Retry-After header. */
public class TooManyRequestException extends RuntimeException {
    public TooManyRequestException(String message) {
        super(message);
    }
}