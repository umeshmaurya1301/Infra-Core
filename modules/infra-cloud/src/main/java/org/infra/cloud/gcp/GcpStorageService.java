package org.infra.cloud.gcp;

import org.infra.cloud.dto.CloudFileMetadata;
import org.infra.cloud.exception.CloudConnectionException;
import org.infra.cloud.exception.CloudOperationException;
import org.infra.cloud.exception.FileNotFoundException;
import org.infra.cloud.service.CloudService;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * GCP-specific cloud storage service interface extending the base CloudService.
 * This interface provides GCP Cloud Storage specific operations and configurations
 * that extend the common cloud service functionality.
 * 
 * <p>This interface is designed to work with GCP Cloud Storage and provides
 * additional GCP-specific operations like bucket management, lifecycle policies,
 * versioning, and signed URL generation.</p>
 * 
 * <p>Implementations should use GCP Cloud Storage client libraries and handle
 * GCP-specific authentication, project management, and error responses.</p>
 * 
 * @author Infrastructure Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface GcpStorageService extends CloudService {

    /**
     * Initializes the GCP Storage service with the provided configuration.
     * This method should be called before any other operations.
     * 
     * @param gcpProperties the GCP configuration properties
     * @throws CloudConnectionException if initialization fails
     */
    void initialize(GcpProperties gcpProperties) throws CloudConnectionException;

    /**
     * Gets the current GCP project ID being used for operations.
     * 
     * @return the GCP project ID
     */
    String getProjectId();

    /**
     * Gets the current GCP location/region being used for operations.
     * 
     * @return the GCP location (e.g., "us-central1", "europe-west1")
     */
    String getLocation();

    /**
     * Creates a new Cloud Storage bucket with the specified name and configuration.
     * 
     * @param bucketName the name of the bucket to create
     * @param location the GCP location where the bucket should be created (null for default)
     * @param storageClass the storage class for the bucket (e.g., STANDARD, NEARLINE, COLDLINE, ARCHIVE)
     * @return true if the bucket was created successfully, false if it already exists
     * @throws CloudOperationException if the bucket creation fails
     */
    boolean createBucket(String bucketName, String location, String storageClass) throws CloudOperationException;

    /**
     * Creates a bucket with default settings from configuration.
     * 
     * @param bucketName the name of the bucket to create
     * @return true if the bucket was created successfully, false if it already exists
     * @throws CloudOperationException if the bucket creation fails
     */
    boolean createBucket(String bucketName) throws CloudOperationException;

    /**
     * Deletes a Cloud Storage bucket. The bucket must be empty before it can be deleted.
     * 
     * @param bucketName the name of the bucket to delete
     * @return true if the bucket was deleted successfully, false if it didn't exist
     * @throws CloudOperationException if the bucket deletion fails (e.g., bucket not empty)
     */
    boolean deleteBucket(String bucketName) throws CloudOperationException;

    /**
     * Lists all Cloud Storage buckets in the current project.
     * 
     * @return list of bucket names
     * @throws CloudOperationException if the bucket listing fails
     */
    List<String> listBuckets() throws CloudOperationException;

    /**
     * Checks if a Cloud Storage bucket exists and is accessible.
     * 
     * @param bucketName the name of the bucket to check
     * @return true if the bucket exists and is accessible, false otherwise
     * @throws CloudOperationException if the existence check fails
     */
    boolean bucketExists(String bucketName) throws CloudOperationException;

    /**
     * Gets the storage class for a specific object.
     * 
     * @param bucketName the name of the bucket
     * @param fileName the name of the file/object
     * @return Optional containing the storage class if available (e.g., STANDARD, NEARLINE, COLDLINE, ARCHIVE)
     * @throws CloudOperationException if the operation fails
     */
    Optional<String> getObjectStorageClass(String bucketName, String fileName) throws CloudOperationException;

    /**
     * Changes the storage class of an existing object.
     * 
     * @param bucketName the name of the bucket
     * @param fileName the name of the file/object
     * @param storageClass the new storage class (e.g., STANDARD, NEARLINE, COLDLINE, ARCHIVE)
     * @return true if the storage class was changed successfully
     * @throws CloudOperationException if the operation fails
     */
    boolean changeObjectStorageClass(String bucketName, String fileName, String storageClass) 
            throws CloudOperationException;

    /**
     * Generates a signed URL for temporary access to an object.
     * 
     * @param bucketName the name of the bucket
     * @param fileName the name of the file/object
     * @param expirationMinutes the number of minutes until the URL expires
     * @param httpMethod the HTTP method ("GET", "PUT", "DELETE")
     * @return the signed URL as a string
     * @throws CloudOperationException if URL generation fails
     */
    String generateSignedUrl(String bucketName, String fileName, int expirationMinutes, String httpMethod) 
            throws CloudOperationException;

    /**
     * Sets lifecycle management rules for a bucket.
     * 
     * @param bucketName the name of the bucket
     * @param lifecycleRules the lifecycle rules configuration
     * @return true if the lifecycle rules were set successfully
     * @throws CloudOperationException if the operation fails
     */
    boolean setBucketLifecycleRules(String bucketName, Map<String, Object> lifecycleRules) throws CloudOperationException;

    /**
     * Enables or disables versioning for a bucket.
     * 
     * @param bucketName the name of the bucket
     * @param enabled true to enable versioning, false to disable
     * @return true if versioning was configured successfully
     * @throws CloudOperationException if the operation fails
     */
    boolean setBucketVersioning(String bucketName, boolean enabled) throws CloudOperationException;

    /**
     * Gets the versioning status of a bucket.
     * 
     * @param bucketName the name of the bucket
     * @return true if versioning is enabled, false otherwise
     * @throws CloudOperationException if the operation fails
     */
    boolean isBucketVersioningEnabled(String bucketName) throws CloudOperationException;

    /**
     * Lists all versions of an object.
     * 
     * @param bucketName the name of the bucket
     * @param fileName the name of the file/object
     * @return list of version identifiers
     * @throws CloudOperationException if the operation fails
     */
    List<String> listObjectVersions(String bucketName, String fileName) throws CloudOperationException;

    /**
     * Copies an object from one location to another within GCP Cloud Storage.
     * 
     * @param sourceBucket the source bucket name
     * @param sourceFileName the source file name
     * @param destinationBucket the destination bucket name
     * @param destinationFileName the destination file name
     * @return true if the copy was successful
     * @throws CloudOperationException if the copy operation fails
     * @throws FileNotFoundException if the source file doesn't exist
     */
    boolean copyObject(String sourceBucket, String sourceFileName, 
                      String destinationBucket, String destinationFileName) 
            throws CloudOperationException, FileNotFoundException;

    /**
     * Moves an object from one location to another within GCP Cloud Storage.
     * This is essentially a copy followed by a delete operation.
     * 
     * @param sourceBucket the source bucket name
     * @param sourceFileName the source file name
     * @param destinationBucket the destination bucket name
     * @param destinationFileName the destination file name
     * @return true if the move was successful
     * @throws CloudOperationException if the move operation fails
     * @throws FileNotFoundException if the source file doesn't exist
     */
    boolean moveObject(String sourceBucket, String sourceFileName, 
                      String destinationBucket, String destinationFileName) 
            throws CloudOperationException, FileNotFoundException;

    /**
     * Gets comprehensive bucket information including metadata, policies, and settings.
     * 
     * @param bucketName the name of the bucket
     * @return Optional containing bucket information if the bucket exists
     * @throws CloudOperationException if the operation fails
     */
    Optional<Map<String, Object>> getBucketInfo(String bucketName) throws CloudOperationException;
}
