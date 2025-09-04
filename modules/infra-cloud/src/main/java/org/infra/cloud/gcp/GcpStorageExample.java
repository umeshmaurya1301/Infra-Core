package org.infra.cloud.gcp;

import lombok.extern.slf4j.Slf4j;
import org.infra.cloud.dto.CloudFileMetadata;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Example usage of the GCP Storage Service implementation.
 * 
 * <p>This class demonstrates how to use the GcpStorageService for common operations
 * like uploading files, downloading files, managing buckets, and using GCP-specific features.</p>
 * 
 * <p><strong>Note:</strong> This is an example class for demonstration purposes.
 * In a real application, you would inject the GcpStorageService and use it in your business logic.</p>
 * 
 * @author Infrastructure Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Component
public class GcpStorageExample {

    /**
     * Demonstrates basic usage of the GCP Storage Service.
     * 
     * @param gcpStorageService the GCP storage service instance
     */
    public void demonstrateUsage(GcpStorageService gcpStorageService) {
        try {
            log.info("=== GCP Storage Service Usage Example ===");
            
            // Example configuration (in real usage, this would come from application properties)
            Map<String, Object> connectionConfig = new HashMap<>();
            connectionConfig.put("projectId", gcpStorageService.getProjectId());
            
            // Connect to GCP
            log.info("Connecting to GCP Cloud Storage...");
            boolean connected = gcpStorageService.connect(connectionConfig);
            log.info("Connection status: {}", connected);
            
            if (!connected) {
                log.error("Failed to connect to GCP Cloud Storage");
                return;
            }
            
            // Example bucket and file names
            String bucketName = "my-example-bucket";
            String fileName = "example-file.txt";
            String fileContent = "Hello, GCP Cloud Storage! This is an example file.";
            
            // 1. Create a bucket
            log.info("Creating bucket: {}", bucketName);
            boolean bucketCreated = gcpStorageService.createBucket(bucketName);
            log.info("Bucket created: {}", bucketCreated);
            
            // 2. Check if bucket exists
            boolean bucketExists = gcpStorageService.bucketExists(bucketName);
            log.info("Bucket exists: {}", bucketExists);
            
            // 3. Upload a file
            log.info("Uploading file: {}", fileName);
            Map<String, String> metadata = new HashMap<>();
            metadata.put("example-key", "example-value");
            metadata.put("uploaded-by", "GcpStorageExample");
            
            String uploadResult = gcpStorageService.uploadFile(
                bucketName,
                fileName,
                new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8)),
                fileContent.length(),
                metadata
            );
            log.info("File uploaded successfully: {}", uploadResult);
            
            // 4. Check if file exists
            boolean fileExists = gcpStorageService.fileExists(bucketName, fileName);
            log.info("File exists: {}", fileExists);
            
            // 5. Get file metadata
            Optional<CloudFileMetadata> fileMetadata = gcpStorageService.getFileMetadata(bucketName, fileName);
            if (fileMetadata.isPresent()) {
                CloudFileMetadata metadata1 = fileMetadata.get();
                log.info("File metadata: size={}, contentType={}, storageClass={}", 
                        metadata1.getSize(), metadata1.getContentType(), metadata1.getStorageClass());
            }
            
            // 6. List files in bucket
            List<String> files = gcpStorageService.listFiles(bucketName);
            log.info("Files in bucket: {}", files);
            
            // 7. Download file as bytes
            byte[] downloadedContent = gcpStorageService.downloadFileAsBytes(bucketName, fileName);
            String downloadedText = new String(downloadedContent, StandardCharsets.UTF_8);
            log.info("Downloaded content: {}", downloadedText);
            
            // 8. Download file to OutputStream
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            long bytesDownloaded = gcpStorageService.downloadFile(bucketName, fileName, outputStream);
            log.info("Bytes downloaded: {}", bytesDownloaded);
            
            // 9. Generate signed URL
            String signedUrl = gcpStorageService.generateSignedUrl(bucketName, fileName, 60, "GET");
            log.info("Generated signed URL (expires in 60 minutes): {}", signedUrl);
            
            // 10. Get object storage class
            Optional<String> storageClass = gcpStorageService.getObjectStorageClass(bucketName, fileName);
            log.info("Object storage class: {}", storageClass.orElse("UNKNOWN"));
            
            // 11. Copy object
            String copyFileName = "copied-" + fileName;
            boolean copied = gcpStorageService.copyObject(bucketName, fileName, bucketName, copyFileName);
            log.info("File copied successfully: {}", copied);
            
            // 12. List buckets
            List<String> buckets = gcpStorageService.listBuckets();
            log.info("Available buckets: {}", buckets);
            
            // 13. Get bucket info
            Optional<Map<String, Object>> bucketInfo = gcpStorageService.getBucketInfo(bucketName);
            if (bucketInfo.isPresent()) {
                log.info("Bucket info: {}", bucketInfo.get());
            }
            
            // 14. Set bucket versioning
            boolean versioningSet = gcpStorageService.setBucketVersioning(bucketName, true);
            log.info("Versioning enabled: {}", versioningSet);
            
            boolean versioningEnabled = gcpStorageService.isBucketVersioningEnabled(bucketName);
            log.info("Versioning status: {}", versioningEnabled);
            
            // 15. Set lifecycle rules
            Map<String, Object> lifecycleRules = new HashMap<>();
            lifecycleRules.put("deleteAfterDays", 30);
            boolean lifecycleSet = gcpStorageService.setBucketLifecycleRules(bucketName, lifecycleRules);
            log.info("Lifecycle rules set: {}", lifecycleSet);
            
            // Cleanup: Delete files
            log.info("Cleaning up files...");
            gcpStorageService.deleteFile(bucketName, fileName);
            gcpStorageService.deleteFile(bucketName, copyFileName);
            
            // Note: In a real scenario, you might not want to delete the bucket
            // gcpStorageService.deleteBucket(bucketName);
            
            // Disconnect
            gcpStorageService.disconnect();
            log.info("Disconnected from GCP Cloud Storage");
            
            log.info("=== Example completed successfully ===");
            
        } catch (Exception e) {
            log.error("Error during GCP Storage Service example", e);
        }
    }
    
    /**
     * Demonstrates error handling scenarios.
     * 
     * @param gcpStorageService the GCP storage service instance
     */
    public void demonstrateErrorHandling(GcpStorageService gcpStorageService) {
        try {
            log.info("=== Error Handling Examples ===");
            
            // Try to operate without connecting
            try {
                gcpStorageService.listBuckets();
            } catch (Exception e) {
                log.info("Expected error when not connected: {}", e.getMessage());
            }
            
            // Connect first
            gcpStorageService.connect(new HashMap<>());
            
            // Try to access non-existent file
            try {
                gcpStorageService.downloadFileAsBytes("non-existent-bucket", "non-existent-file.txt");
            } catch (Exception e) {
                log.info("Expected error for non-existent file: {}", e.getMessage());
            }
            
            // Try to create bucket with invalid name
            try {
                gcpStorageService.createBucket("INVALID_BUCKET_NAME_WITH_CAPS");
            } catch (Exception e) {
                log.info("Expected error for invalid bucket name: {}", e.getMessage());
            }
            
            gcpStorageService.disconnect();
            
        } catch (Exception e) {
            log.error("Error during error handling demonstration", e);
        }
    }
}
