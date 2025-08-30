package org.infra.commons.exception;

/**
 * Exception for technical/system errors.
 * Used when technical issues occur like database connectivity, external service failures, etc.
 */
public class TechnicalException extends BaseException {

    public TechnicalException(String errorCode, String message) {
        super(errorCode, message);
    }

    public TechnicalException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public TechnicalException(String errorCode, String message, String userMessage) {
        super(errorCode, message, userMessage);
    }

    public TechnicalException(String errorCode, String message, String userMessage, Throwable cause) {
        super(errorCode, message, userMessage, cause);
    }
}
