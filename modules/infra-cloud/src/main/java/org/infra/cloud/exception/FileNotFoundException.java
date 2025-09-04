package org.infra.cloud.exception;

import lombok.Getter;
import lombok.Setter;
import org.infra.commons.exception.InfraException;

/**
 * Exception thrown when a requested file is not found in cloud storage.
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
public class FileNotFoundException extends InfraException {

    private final String bucketName;
    private final String fileName;

    /**
     * Constructs a new FileNotFoundException with the specified detail message.
     * 
     * @param message the detail message
     */
    public FileNotFoundException(String message) {
        super(message);
        this.bucketName = null;
        this.fileName = null;
    }

    /**
     * Constructs a new FileNotFoundException with the specified detail message and cause.
     * 
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public FileNotFoundException(String message, Throwable cause) {
        super(message, cause);
        this.bucketName = null;
        this.fileName = null;
    }

    /**
     * Constructs a new FileNotFoundException with file context.
     * 
     * @param message the detail message
     * @param bucketName the bucket name where the file was expected
     * @param fileName the name of the file that was not found
     */
    public FileNotFoundException(String message, String bucketName, String fileName) {
        super(message);
        this.bucketName = bucketName;
        this.fileName = fileName;
    }

    /**
     * Constructs a new FileNotFoundException with file context and cause.
     * 
     * @param message the detail message
     * @param cause the cause of the exception
     * @param bucketName the bucket name where the file was expected
     * @param fileName the name of the file that was not found
     */
    public FileNotFoundException(String message, Throwable cause, String bucketName, String fileName) {
        super(message, cause);
        this.bucketName = bucketName;
        this.fileName = fileName;
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder(super.getMessage());
        
        if (bucketName != null || fileName != null) {
            sb.append(" [");
            if (bucketName != null) {
                sb.append("bucket=").append(bucketName);
            }
            if (fileName != null) {
                if (bucketName != null) sb.append(", ");
                sb.append("file=").append(fileName);
            }
            sb.append("]");
        }
        
        return sb.toString();
    }
}
