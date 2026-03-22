package com.infra.core.crypto.exception;

public class KeyParsingException extends CryptographyException {
    public KeyParsingException(String message) {
        super(message);
    }

    public KeyParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
