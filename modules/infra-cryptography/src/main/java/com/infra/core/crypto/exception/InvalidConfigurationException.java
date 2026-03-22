package com.infra.core.crypto.exception;

public class InvalidConfigurationException extends CryptographyException {
    public InvalidConfigurationException(String message) {
        super(message);
    }

    public InvalidConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
