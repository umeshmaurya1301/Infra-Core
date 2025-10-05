package org.infra.messaging.exception;

/**
 * Base exception for messaging operations
 */
public class MessagingException extends RuntimeException {
    
    public MessagingException(String message) {
        super(message);
    }
    
    public MessagingException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public MessagingException(Throwable cause) {
        super(cause);
    }
}
