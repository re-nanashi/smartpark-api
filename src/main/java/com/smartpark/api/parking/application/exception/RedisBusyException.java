package com.smartpark.api.parking.application.exception;

/** Thrown when the Redis distributed lock cannot be acquired (lock is busy). */
public class RedisBusyException extends RuntimeException {
    public RedisBusyException(String message) {
        super(message);
    }
}