package com.infra.core.crypto.exception;

public class EncryptionFailureException extends CryptographyException {
    public EncryptionFailureException(String message) {
        super(message);
    }

    public EncryptionFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
