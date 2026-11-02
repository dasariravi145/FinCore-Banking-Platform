package com.fincore.platform.common.error;

public final class ErrorConstants {

    private ErrorConstants() {}

    public static final String ERR_VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String ERR_AUTHENTICATION_FAILED = "AUTHENTICATION_FAILED";
    public static final String ERR_ACCESS_DENIED = "ACCESS_DENIED";
    public static final String ERR_USER_ALREADY_EXISTS = "USER_ALREADY_EXISTS";
    public static final String ERR_RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String ERR_TOKEN_EXPIRED_OR_INVALID = "TOKEN_EXPIRED_OR_INVALID";
    public static final String ERR_TOKEN_REPLAY_DETECTED = "TOKEN_REPLAY_DETECTED";
    public static final String ERR_RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED";
    public static final String ERR_DATA_INTEGRITY_VIOLATION = "DATA_INTEGRITY_VIOLATION";
    public static final String ERR_INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
}

