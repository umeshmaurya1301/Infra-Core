package org.infra.cloud.exception;

import lombok.Getter;
import lombok.Setter;
import org.infra.commons.exception.InfraException;

/**
 * Exception thrown when cloud storage operations fail.
 * This includes operations like upload, download, delete, list, and metadata retrieval.
 * 
 * <p>This exception extends {@link InfraException} to maintain consistency
 * with the infrastructure exception hierarchy.</p>
 * 
 * @author Infrastructure Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
@Setter
public class CloudOperationException extends InfraException {

    private final String operation;
    private final String bucketName;
    private final String fileName;

    /**
     * Constructs a new CloudOperationException with the specified detail message.
     * 
     * @param message the detail message
     */
    public CloudOperationException(String message) {
        super(message);
        this.operation = null;
        this.bucketName = null;
        this.fileName = null;
    }

    /**
     * Constructs a new CloudOperationException with the specified detail message and cause.
     * 
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public CloudOperationException(String message, Throwable cause) {
        super(message, cause);
        this.operation = null;
        this.bucketName = null;
        this.fileName = null;
    }

    /**
     * Constructs a new CloudOperationException with operation context.
     * 
     * @param message the detail message
     * @param operation the operation that failed (e.g., "upload", "download", "delete")
     * @param bucketName the bucket name involved in the operation
     * @param fileName the file name involved in the operation
     */
    public CloudOperationException(String message, String operation, String bucketName, String fileName) {
        super(message);
        this.operation = operation;
        this.bucketName = bucketName;
        this.fileName = fileName;
    }

    /**
     * Constructs a new CloudOperationException with operation context and cause.
     * 
     * @param message the detail message
     * @param cause the cause of the exception
     * @param operation the operation that failed
     * @param bucketName the bucket name involved in the operation
     * @param fileName the file name involved in the operation
     */
    public CloudOperationException(String message, Throwable cause, String operation, 
                                 String bucketName, String fileName) {
        super(message, cause);
        this.operation = operation;
        this.bucketName = bucketName;
        this.fileName = fileName;
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder(super.getMessage());
        
        if (operation != null || bucketName != null || fileName != null) {
            sb.append(" [");
            if (operation != null) {
                sb.append("operation=").append(operation);
            }
            if (bucketName != null) {
                if (operation != null) sb.append(", ");
                sb.append("bucket=").append(bucketName);
            }
            if (fileName != null) {
                if (operation != null || bucketName != null) sb.append(", ");
                sb.append("file=").append(fileName);
            }
            sb.append("]");
        }
        
        return sb.toString();
    }
}
