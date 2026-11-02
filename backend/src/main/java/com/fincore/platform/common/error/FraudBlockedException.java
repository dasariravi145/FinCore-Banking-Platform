package com.fincore.platform.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class FraudBlockedException extends RuntimeException {
    public FraudBlockedException(String message) {
        super(message);
    }
}

