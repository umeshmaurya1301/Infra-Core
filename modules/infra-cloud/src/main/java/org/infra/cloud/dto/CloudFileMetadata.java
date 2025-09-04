package org.infra.cloud.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.Map;

/**
 * Represents metadata information for a file stored in cloud storage.
 * This class encapsulates common file attributes that are typically
 * available across different cloud storage providers.
 * 
 * @author Infrastructure Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CloudFileMetadata {
    
    /**
     * The name/key of the file.
     */
    String fileName;
    
    /**
     * The bucket or container name.
     */
    String bucketName;
    
    /**
     * The size of the file in bytes.
     */
    long size;
    
    /**
     * The MIME type of the file.
     */
    String contentType;
    
    /**
     * The entity tag (checksum) of the file.
     */
    String etag;
    
    /**
     * The timestamp when the file was last modified.
     */
    Instant lastModified;
    
    /**
     * The timestamp when the file was created.
     */
    Instant createdDate;
    
    /**
     * Custom metadata associated with the file.
     */
    Map<String, String> userMetadata;
    
    /**
     * The storage class or tier (e.g., STANDARD, COLD, ARCHIVE).
     */
    String storageClass;
    
    /**
     * The encryption information (e.g., AES256, KMS).
     */
    String encryption;
}
