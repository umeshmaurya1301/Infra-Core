package org.infra.commons.exception;

/**
 * Base exception class for all infrastructure-related exceptions across all infra modules.
 * 
 * <p>This class serves as the root exception for the entire infrastructure library ecosystem,
 * providing a consistent exception hierarchy and enabling unified exception handling strategies
 * across all infra modules (commons, security, cryptography, audit, validation, cloud, etc.).</p>
 * 
 * <p>All custom exceptions within the infra modules should extend this class either directly
 * or through its subclasses to maintain consistency and enable centralized exception handling.</p>
 * 
 * <p><strong>Design Principles:</strong></p>
 * <ul>
 *   <li>Extends {@link RuntimeException} to avoid forced exception handling in client code</li>
 *   <li>Provides all standard constructors for maximum flexibility</li>
 *   <li>Serves as a marker interface for infra-specific exceptions</li>
 *   <li>Enables consistent logging and monitoring across all infra modules</li>
 * </ul>
 * 
 * <p><strong>Usage Examples:</strong></p>
 * <pre>
 * // Direct usage
 * throw new InfraException("Infrastructure operation failed");
 * 
 * // As base class for module-specific exceptions
 * public class SecurityException extends InfraException {
 *     public SecurityException(String message) {
 *         super(message);
 *     }
 * }
 * </pre>
 * 
 * @author Infrastructure Team
 * @version 1.0.0
 * @since 1.0.0
 * @see RuntimeException
 */
public class InfraException extends RuntimeException {

    /**
     * Constructs a new InfraException with no detail message.
     * The cause is not initialized, and may subsequently be initialized by a call to {@link #initCause}.
     */
    public InfraException() {
        super();
    }

    /**
     * Constructs a new InfraException with the specified detail message.
     * The cause is not initialized, and may subsequently be initialized by a call to {@link #initCause}.
     * 
     * @param message the detail message. The detail message is saved for later retrieval 
     *               by the {@link #getMessage()} method
     */
    public InfraException(String message) {
        super(message);
    }

    /**
     * Constructs a new InfraException with the specified detail message and cause.
     * 
     * <p>Note that the detail message associated with {@code cause} is <i>not</i> 
     * automatically incorporated in this exception's detail message.</p>
     * 
     * @param message the detail message (which is saved for later retrieval by the 
     *               {@link #getMessage()} method)
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method).
     *             (A null value is permitted, and indicates that the cause is nonexistent or unknown.)
     */
    public InfraException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new InfraException with the specified cause and a detail message of 
     * {@code (cause==null ? null : cause.toString())} (which typically contains the class 
     * and detail message of {@code cause}).

     * This constructor is useful for exceptions that are little more than wrappers for other throwables.
     * 
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method).
     *             (A null value is permitted, and indicates that the cause is nonexistent or unknown.)
     */
    public InfraException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new InfraException with the specified detail message, cause, 
     * suppression enabled or disabled, and writable stack trace enabled or disabled.
     * 
     * @param message the detail message
     * @param cause the cause (A null value is permitted, and indicates that the cause is 
     *             nonexistent or unknown.)
     * @param enableSuppression whether or not suppression is enabled or disabled
     * @param writableStackTrace whether or not the stack trace should be writable
     * 
     * @since 1.0.0
     */
    protected InfraException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
