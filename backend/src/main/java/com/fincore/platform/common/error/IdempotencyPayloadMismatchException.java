package com.fincore.platform.common.error;

public class IdempotencyPayloadMismatchException extends RuntimeException {
    public IdempotencyPayloadMismatchException(String message) {
        super(message);
    }
}

