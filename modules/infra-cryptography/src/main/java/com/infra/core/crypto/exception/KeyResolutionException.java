package com.infra.core.crypto.exception;

public class KeyResolutionException extends CryptographyException {
    public KeyResolutionException(String message) {
        super(message);
    }

    public KeyResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
