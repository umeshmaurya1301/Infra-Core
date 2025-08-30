package org.infra.commons.exception;

/**
 * Base exception class for all custom exceptions in the infrastructure.
 * Provides common functionality and structure for exception handling.
 */
public abstract class BaseException extends RuntimeException {

    private final String errorCode;
    private final String userMessage;

    protected BaseException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.userMessage = message;
    }

    protected BaseException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.userMessage = message;
    }

    protected BaseException(String errorCode, String message, String userMessage) {
        super(message);
        this.errorCode = errorCode;
        this.userMessage = userMessage;
    }

    protected BaseException(String errorCode, String message, String userMessage, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.userMessage = userMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
