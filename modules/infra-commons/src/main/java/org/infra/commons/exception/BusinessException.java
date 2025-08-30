package org.infra.commons.exception;

/**
 * Exception for business logic violations.
 * Used when business rules are not satisfied.
 */
public class BusinessException extends BaseException {

    public BusinessException(String errorCode, String message) {
        super(errorCode, message);
    }

    public BusinessException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public BusinessException(String errorCode, String message, String userMessage) {
        super(errorCode, message, userMessage);
    }

    public BusinessException(String errorCode, String message, String userMessage, Throwable cause) {
        super(errorCode, message, userMessage, cause);
    }
}
