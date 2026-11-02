package com.fincore.platform.common.error;

public class AccountClosedException extends RuntimeException {
    public AccountClosedException(String message) {
        super(message);
    }
}

