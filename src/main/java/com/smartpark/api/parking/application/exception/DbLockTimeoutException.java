package com.smartpark.api.parking.application.exception;

/** Thrown when the DB pessimistic lock wait exceeds the configured timeout (~500 ms). */
public class DbLockTimeoutException extends RuntimeException {
    public DbLockTimeoutException(String message) {
        super(message);
    }

    public DbLockTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
