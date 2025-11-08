package net.vajraedge.sdk;

import java.net.URI;
import java.util.Map;

/**
 * Utility class for common parameter validation patterns.
 * Reduces boilerplate code in plugin parameter validation.
 * 
 * @since 1.1.0
 */
public final class ParameterValidator {
    
    private ParameterValidator() {
        // Utility class
    }
    
    /**
     * Validate that a required string parameter exists and is not blank.
     * 
     * @param parameters Parameter map
     * @param paramName Parameter name
     * @throws IllegalArgumentException if parameter is missing or blank
     */
    public static void requireString(Map<String, Object> parameters, String paramName) {
        if (!parameters.containsKey(paramName)) {
            throw new IllegalArgumentException(paramName + " parameter is required");
        }
        
        String value = parameters.get(paramName).toString();
        if (value.isBlank()) {
            throw new IllegalArgumentException(paramName + " cannot be empty");
        }
    }
    
    /**
     * Validate that a required parameter exists.
     * 
     * @param parameters Parameter map
     * @param paramName Parameter name
     * @throws IllegalArgumentException if parameter is missing
     */
    public static void requireParameter(Map<String, Object> parameters, String paramName) {
        if (!parameters.containsKey(paramName)) {
            throw new IllegalArgumentException(paramName + " parameter is required");
        }
    }
    
    /**
     * Validate that a string parameter is a valid URL.
     * 
     * @param parameters Parameter map
     * @param paramName Parameter name
     * @throws IllegalArgumentException if URL is invalid
     */
    public static void requireValidUrl(Map<String, Object> parameters, String paramName) {
        requireString(parameters, paramName);
        
        String url = parameters.get(paramName).toString();
        try {
            URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid URL format for " + paramName + ": " + url);
        }
    }
    
    /**
     * Validate that an integer parameter is within a specified range.
     * 
     * @param parameters Parameter map
     * @param paramName Parameter name
     * @param minValue Minimum allowed value (inclusive)
     * @param maxValue Maximum allowed value (inclusive)
     * @throws IllegalArgumentException if value is out of range or cannot be parsed
     */
    public static void requireIntegerInRange(Map<String, Object> parameters, String paramName, 
                                             int minValue, int maxValue) {
        if (!parameters.containsKey(paramName)) {
            return; // Optional parameter
        }
        
        Object valueObj = parameters.get(paramName);
        int value;
        
        if (valueObj instanceof Integer) {
            value = (Integer) valueObj;
        } else {
            try {
                value = Integer.parseInt(valueObj.toString());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    String.format("Invalid integer value for parameter '%s': %s", paramName, valueObj), e);
            }
        }
        
        if (value < minValue || value > maxValue) {
            throw new IllegalArgumentException(
                String.format("%s must be between %d and %d", paramName, minValue, maxValue));
        }
    }
    
    /**
     * Get an integer parameter with a default value if not present.
     * 
     * @param parameters Parameter map
     * @param paramName Parameter name
     * @param defaultValue Default value if parameter not present
     * @return Parameter value or default
     * @throws IllegalArgumentException if parameter value cannot be parsed as an integer
     */
    public static int getIntegerOrDefault(Map<String, Object> parameters, String paramName, int defaultValue) {
        if (!parameters.containsKey(paramName)) {
            return defaultValue;
        }
        
        Object valueObj = parameters.get(paramName);
        if (valueObj instanceof Integer) {
            return (Integer) valueObj;
        }
        
        try {
            if (valueObj instanceof String) {
                return Integer.parseInt((String) valueObj);
            } else {
                return Integer.parseInt(valueObj.toString());
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                String.format("Invalid integer value for parameter '%s': %s", paramName, valueObj), e);
        }
    }
    
    /**
     * Get a string parameter with a default value if not present.
     * 
     * @param parameters Parameter map
     * @param paramName Parameter name
     * @param defaultValue Default value if parameter not present
     * @return Parameter value or default
     */
    public static String getStringOrDefault(Map<String, Object> parameters, String paramName, String defaultValue) {
        if (!parameters.containsKey(paramName)) {
            return defaultValue;
        }
        return parameters.get(paramName).toString();
    }
    
    /**
     * Get a boolean parameter with a default value if not present.
     * 
     * @param parameters Parameter map
     * @param paramName Parameter name
     * @param defaultValue Default value if parameter not present
     * @return Parameter value or default
     */
    public static boolean getBooleanOrDefault(Map<String, Object> parameters, String paramName, boolean defaultValue) {
        if (!parameters.containsKey(paramName)) {
            return defaultValue;
        }
        return Boolean.parseBoolean(parameters.get(paramName).toString());
    }
    
    /**
     * Validate timeout parameter (common pattern).
     * Checks that timeout is within 100ms to 60000ms range.
     * 
     * @param parameters Parameter map
     * @param paramName Parameter name (usually "timeout")
     * @throws IllegalArgumentException if timeout is invalid
     */
    public static void validateTimeout(Map<String, Object> parameters, String paramName) {
        requireIntegerInRange(parameters, paramName, 100, 60000);
    }
}
