package org.infra.cloud.service;

import org.infra.cloud.dto.CloudFileMetadata;
import org.infra.cloud.exception.CloudConnectionException;
import org.infra.cloud.exception.CloudOperationException;
import org.infra.cloud.exception.FileNotFoundException;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Common interface for cloud storage operations across different cloud providers.
 * This interface provides a unified abstraction layer for cloud storage services,
 * allowing applications to work with different cloud providers (AWS S3, GCP Cloud Storage, 
 * Azure Blob Storage) through a consistent API.
 * 
 * <p>Implementations of this interface should handle provider-specific authentication,
 * error handling, and protocol differences while maintaining the contract defined here.</p>
 * 
 * @author Infrastructure Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface CloudService {

    /**
     * Establishes a connection to the cloud storage service.
     * This method should handle authentication and initialize any necessary
     * client configurations required for subsequent operations.
     * 
     * @param connectionConfig configuration parameters for establishing connection
     *                        (e.g., credentials, region, endpoint URLs)
     * @return true if connection was established successfully, false otherwise
     * @throws CloudConnectionException if connection cannot be established
     */
    boolean connect(Map<String, Object> connectionConfig) throws CloudConnectionException;

    /**
     * Uploads a file to the specified bucket or container in the cloud storage.
     * The file content is provided as an InputStream for efficient streaming.
     * 
     * @param bucketName the name of the bucket or container where the file will be stored
     * @param fileName the name/key of the file in the cloud storage
     * @param fileContent the content of the file as an InputStream
     * @param contentLength the size of the file content in bytes
     * @param metadata optional metadata to associate with the file (can be null or empty)
     * @return unique identifier or URL of the uploaded file
     * @throws CloudOperationException if the upload operation fails
     */
    String uploadFile(String bucketName, String fileName, InputStream fileContent, 
                     long contentLength, Map<String, String> metadata) throws CloudOperationException;

    /**
     * Downloads a file from the cloud storage and writes it to the provided OutputStream.
     * 
     * @param bucketName the name of the bucket or container containing the file
     * @param fileName the name/key of the file to download
     * @param outputStream the OutputStream where the file content will be written
     * @return the number of bytes downloaded
     * @throws CloudOperationException if the download operation fails
     * @throws FileNotFoundException if the specified file does not exist
     */
    long downloadFile(String bucketName, String fileName, OutputStream outputStream) 
            throws CloudOperationException, FileNotFoundException;

    /**
     * Downloads a file from the cloud storage and returns it as a byte array.
     * This method is convenient for smaller files but should be used carefully
     * with large files to avoid memory issues.
     * 
     * @param bucketName the name of the bucket or container containing the file
     * @param fileName the name/key of the file to download
     * @return the file content as a byte array
     * @throws CloudOperationException if the download operation fails
     * @throws FileNotFoundException if the specified file does not exist
     */
    byte[] downloadFileAsBytes(String bucketName, String fileName) 
            throws CloudOperationException, FileNotFoundException;

    /**
     * Deletes a file from the cloud storage.
     * 
     * @param bucketName the name of the bucket or container containing the file
     * @param fileName the name/key of the file to delete
     * @return true if the file was successfully deleted, false if the file did not exist
     * @throws CloudOperationException if the delete operation fails
     */
    boolean deleteFile(String bucketName, String fileName) throws CloudOperationException;

    /**
     * Lists all files in the specified bucket or directory.
     * 
     * @param bucketName the name of the bucket or container to list files from
     * @param prefix optional prefix to filter files (can be null for all files)
     * @param maxResults maximum number of files to return (0 or negative for no limit)
     * @return list of file names/keys in the bucket
     * @throws CloudOperationException if the list operation fails
     */
    List<String> listFiles(String bucketName, String prefix, int maxResults) 
            throws CloudOperationException;

    /**
     * Lists all files in the specified bucket.
     * This is a convenience method equivalent to calling listFiles(bucketName, null, 0).
     * 
     * @param bucketName the name of the bucket or container to list files from
     * @return list of all file names/keys in the bucket
     * @throws CloudOperationException if the list operation fails
     */
    default List<String> listFiles(String bucketName) throws CloudOperationException {
        return listFiles(bucketName, null, 0);
    }

    /**
     * Retrieves metadata information for a specific file.
     * 
     * @param bucketName the name of the bucket or container containing the file
     * @param fileName the name/key of the file
     * @return Optional containing file metadata if the file exists, empty otherwise
     * @throws CloudOperationException if the metadata retrieval operation fails
     */
    Optional<CloudFileMetadata> getFileMetadata(String bucketName, String fileName)
            throws CloudOperationException;

    /**
     * Checks if a file exists in the cloud storage.
     * 
     * @param bucketName the name of the bucket or container
     * @param fileName the name/key of the file to check
     * @return true if the file exists, false otherwise
     * @throws CloudOperationException if the existence check fails
     */
    boolean fileExists(String bucketName, String fileName) throws CloudOperationException;

    /**
     * Closes the connection to the cloud storage service and releases any resources.
     * After calling this method, other operations on this instance may fail.
     * 
     * @throws CloudConnectionException if there's an error during disconnection
     */
    void disconnect() throws CloudConnectionException;

    /**
     * Checks if the connection to the cloud service is currently active.
     * 
     * @return true if connected, false otherwise
     */
    boolean isConnected();

    /**
     * Gets the name of the cloud provider (e.g., "AWS", "GCP", "Azure").
     * 
     * @return the name of the cloud provider
     */
    String getProviderName();
}
