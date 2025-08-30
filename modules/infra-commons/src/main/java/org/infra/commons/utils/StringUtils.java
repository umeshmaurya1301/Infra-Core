package org.infra.commons.utils;

import java.util.Collection;
import java.util.regex.Pattern;

/**
 * Utility class for string operations.
 */
public final class StringUtils {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    private StringUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Check if string is null or empty
     */
    public static boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }

    /**
     * Check if string is not null and not empty
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /**
     * Check if string is null, empty, or contains only whitespace
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().length() == 0;
    }

    /**
     * Check if string is not null, not empty, and contains non-whitespace characters
     */
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    /**
     * Trim string and return null if result is empty
     */
    public static String trimToNull(String str) {
        String trimmed = trim(str);
        return isEmpty(trimmed) ? null : trimmed;
    }

    /**
     * Trim string and return empty string if null
     */
    public static String trimToEmpty(String str) {
        return str == null ? "" : str.trim();
    }

    /**
     * Safe trim - returns null if input is null
     */
    public static String trim(String str) {
        return str == null ? null : str.trim();
    }

    /**
     * Capitalize first letter of string
     */
    public static String capitalize(String str) {
        if (isEmpty(str)) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Convert string to camelCase
     */
    public static String toCamelCase(String str) {
        if (isBlank(str)) {
            return str;
        }
        
        String[] words = str.toLowerCase().split("[\\s_-]+");
        StringBuilder result = new StringBuilder(words[0]);
        
        for (int i = 1; i < words.length; i++) {
            result.append(capitalize(words[i]));
        }
        
        return result.toString();
    }

    /**
     * Convert string to snake_case
     */
    public static String toSnakeCase(String str) {
        if (isBlank(str)) {
            return str;
        }
        
        return str.replaceAll("([a-z])([A-Z])", "$1_$2")
                  .replaceAll("[\\s-]+", "_")
                  .toLowerCase();
    }

    /**
     * Join collection of strings with delimiter
     */
    public static String join(Collection<String> collection, String delimiter) {
        if (collection == null || collection.isEmpty()) {
            return "";
        }
        
        return String.join(delimiter, collection);
    }

    /**
     * Join array of strings with delimiter
     */
    public static String join(String[] array, String delimiter) {
        if (array == null || array.length == 0) {
            return "";
        }
        
        return String.join(delimiter, array);
    }

    /**
     * Check if string contains only digits
     */
    public static boolean isNumeric(String str) {
        if (isBlank(str)) {
            return false;
        }
        
        return str.matches("\\d+");
    }

    /**
     * Check if string is a valid email format
     */
    public static boolean isValidEmail(String email) {
        if (isBlank(email)) {
            return false;
        }
        
        return EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * Mask string for security (e.g., passwords, sensitive data)
     */
    public static String mask(String str) {
        return mask(str, '*');
    }

    /**
     * Mask string with specified character
     */
    public static String mask(String str, char maskChar) {
        if (isEmpty(str)) {
            return str;
        }
        
        if (str.length() <= 2) {
            return String.valueOf(maskChar).repeat(str.length());
        }
        
        return str.charAt(0) + String.valueOf(maskChar).repeat(str.length() - 2) + str.charAt(str.length() - 1);
    }

    /**
     * Truncate string to specified length
     */
    public static String truncate(String str, int maxLength) {
        if (isEmpty(str) || str.length() <= maxLength) {
            return str;
        }
        
        return str.substring(0, maxLength) + "...";
    }
}