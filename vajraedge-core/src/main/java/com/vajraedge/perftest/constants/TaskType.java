package com.vajraedge.perftest.constants;

/**
 * Enumeration of supported task types in VajraEdge.
 * 
 * This enum provides type safety and prevents magic strings throughout the codebase.
 * Use this instead of hardcoded task type strings.
 */
public enum TaskType {
    
    /**
     * Sleep task - simulates I/O-bound operations with configurable delay
     */
    SLEEP("SLEEP"),
    
    /**
     * CPU-intensive task - simulates computational workload
     */
    CPU("CPU"),
    
    /**
     * HTTP GET request task
     */
    HTTP_GET("HTTP_GET"),
    
    /**
     * HTTP POST request task
     */
    HTTP_POST("HTTP_POST"),
    
    /**
     * Generic HTTP task (backward compatibility)
     */
    HTTP("HTTP");
    
    private final String typeName;
    
    TaskType(String typeName) {
        this.typeName = typeName;
    }
    
    /**
     * Get the string representation of the task type.
     * 
     * @return task type name
     */
    public String getTypeName() {
        return typeName;
    }
    
    /**
     * Parse a task type from string name (case-insensitive).
     * 
     * @param name task type name
     * @return TaskType enum value, or null if not found
     */
    public static TaskType fromString(String name) {
        if (name == null) {
            return null;
        }
        
        String normalizedName = name.toUpperCase().trim();
        
        for (TaskType type : values()) {
            if (type.typeName.equals(normalizedName)) {
                return type;
            }
        }
        
        return null;
    }
    
    /**
     * Check if a string represents a valid task type.
     * 
     * @param name task type name
     * @return true if valid, false otherwise
     */
    public static boolean isValid(String name) {
        return fromString(name) != null;
    }
    
    /**
     * Check if task type represents an HTTP-based task.
     * 
     * @return true if HTTP-based, false otherwise
     */
    public boolean isHttpTask() {
        return this == HTTP_GET || this == HTTP_POST || this == HTTP;
    }
    
    @Override
    public String toString() {
        return typeName;
    }
}
