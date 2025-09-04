package org.infra.cloud.exception;

import org.infra.commons.exception.InfraException;

/**
 * Exception thrown when there are issues establishing or maintaining
 * a connection to a cloud storage service.
 * 
 * <p>This exception extends {@link InfraException} to maintain consistency
 * with the infrastructure exception hierarchy.</p>
 * 
 * @author Infrastructure Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class CloudConnectionException extends InfraException {

    /**
     * Constructs a new CloudConnectionException with the specified detail message.
     * 
     * @param message the detail message
     */
    public CloudConnectionException(String message) {
        super(message);
    }

    /**
     * Constructs a new CloudConnectionException with the specified detail message and cause.
     * 
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public CloudConnectionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new CloudConnectionException with the specified cause.
     * 
     * @param cause the cause of the exception
     */
    public CloudConnectionException(Throwable cause) {
        super(cause);
    }
}
