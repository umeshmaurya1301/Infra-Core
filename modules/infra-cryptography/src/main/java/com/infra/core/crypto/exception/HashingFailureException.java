package com.infra.core.crypto.exception;

public class HashingFailureException extends CryptographyException {
    public HashingFailureException(String message) {
        super(message);
    }

    public HashingFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
