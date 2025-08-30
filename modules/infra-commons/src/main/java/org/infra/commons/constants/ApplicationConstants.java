package org.infra.commons.constants;

/**
 * Application-wide constants shared across all modules.
 */
public final class ApplicationConstants {
    
    // API Constants
    public static final String API_V1_PREFIX = "/api/v1";
    public static final String API_V2_PREFIX = "/api/v2";
    
    // Header Constants
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String CONTENT_TYPE_HEADER = "Content-Type";
    public static final String BEARER_PREFIX = "Bearer ";
    
    // Date/Time Constants
    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    public static final String DEFAULT_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    public static final String DEFAULT_TIMEZONE = "UTC";
    
    // Security Constants
    public static final String DEFAULT_ALGORITHM = "AES";
    public static final int DEFAULT_KEY_LENGTH = 256;
    
    private ApplicationConstants() {
        // Utility class - prevent instantiation
    }
}
