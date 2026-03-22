package com.infra.core.crypto.exception;

public class DecryptionFailureException extends CryptographyException {
    public DecryptionFailureException(String message) {
        super(message);
    }

    public DecryptionFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
