package org.infra.commons.utils;

import lombok.experimental.UtilityClass;

import java.util.Objects;

/**
 * String utility methods shared across modules.
 */
@UtilityClass
public class StringUtils {
    
    /**
     * Checks if a string is null or empty.
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }
    
    /**
     * Checks if a string is not null and not empty.
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }
    
    /**
     * Checks if a string is null, empty, or contains only whitespace.
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    /**
     * Checks if a string is not null, not empty, and contains non-whitespace characters.
     */
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }
    
    /**
     * Safely converts an object to string, returning empty string for null.
     */
    public static String safeToString(Object obj) {
        return Objects.toString(obj, "");
    }
    
    /**
     * Masks a string for logging purposes, showing only first and last 2 characters.
     */
    public static String maskForLogging(String input) {
        if (isEmpty(input) || input.length() <= 4) {
            return "****";
        }
        return input.substring(0, 2) + "****" + input.substring(input.length() - 2);
    }
}
