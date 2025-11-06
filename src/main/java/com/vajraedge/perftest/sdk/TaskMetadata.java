package com.vajraedge.perftest.sdk;

import java.util.List;
import java.util.Map;

/**
 * Metadata describing a task plugin.
 * Provides information for UI rendering, validation, and documentation.
 *
 * @param name Unique task identifier (e.g., "HTTP_GET", "DATABASE_QUERY")
 * @param displayName Human-readable name for UI display
 * @param description Detailed description of what the task does
 * @param category Task category for grouping (e.g., "HTTP", "DATABASE", "MESSAGING")
 * @param parameters List of parameter definitions
 * @param metadata Additional key-value metadata
 *
 * @since 1.1.0
 */
public record TaskMetadata(
    String name,
    String displayName,
    String description,
    String category,
    List<ParameterDef> parameters,
    Map<String, String> metadata
) {
    
    /**
     * Parameter definition for task configuration.
     *
     * @param name Parameter name (e.g., "url", "method", "timeout")
     * @param type Parameter type ("STRING", "INTEGER", "BOOLEAN", "DOUBLE")
     * @param required Whether this parameter is required
     * @param defaultValue Default value if not provided (can be null)
     * @param description Human-readable parameter description
     * @param validationPattern Optional regex pattern for validation (can be null)
     * @param minValue Optional minimum value for numeric types (can be null)
     * @param maxValue Optional maximum value for numeric types (can be null)
     */
    public record ParameterDef(
        String name,
        String type,
        boolean required,
        Object defaultValue,
        String description,
        String validationPattern,
        Object minValue,
        Object maxValue
    ) {
        /**
         * Create a required string parameter.
         */
        public static ParameterDef requiredString(String name, String description) {
            return new ParameterDef(name, "STRING", true, null, description, null, null, null);
        }
        
        /**
         * Create an optional string parameter with default value.
         */
        public static ParameterDef optionalString(String name, String defaultValue, String description) {
            return new ParameterDef(name, "STRING", false, defaultValue, description, null, null, null);
        }
        
        /**
         * Create a required integer parameter.
         */
        public static ParameterDef requiredInteger(String name, String description) {
            return new ParameterDef(name, "INTEGER", true, null, description, null, null, null);
        }
        
        /**
         * Create an optional integer parameter with default value and range.
         */
        public static ParameterDef optionalInteger(String name, Integer defaultValue, 
                                                   Integer min, Integer max, String description) {
            return new ParameterDef(name, "INTEGER", false, defaultValue, description, null, min, max);
        }
        
        /**
         * Create a required boolean parameter.
         */
        public static ParameterDef requiredBoolean(String name, String description) {
            return new ParameterDef(name, "BOOLEAN", true, null, description, null, null, null);
        }
        
        /**
         * Create an optional boolean parameter with default value.
         */
        public static ParameterDef optionalBoolean(String name, Boolean defaultValue, String description) {
            return new ParameterDef(name, "BOOLEAN", false, defaultValue, description, null, null, null);
        }
    }
    
    /**
     * Builder for TaskMetadata.
     */
    public static class Builder {
        private String name;
        private String displayName;
        private String description;
        private String category = "OTHER";
        private List<ParameterDef> parameters = List.of();
        private Map<String, String> metadata = Map.of();
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder category(String category) {
            this.category = category;
            return this;
        }
        
        public Builder parameters(List<ParameterDef> parameters) {
            this.parameters = parameters;
            return this;
        }
        
        public Builder parameters(ParameterDef... parameters) {
            this.parameters = List.of(parameters);
            return this;
        }
        
        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public TaskMetadata build() {
            if (name == null || name.isBlank()) {
                throw new IllegalStateException("Task name is required");
            }
            if (displayName == null || displayName.isBlank()) {
                throw new IllegalStateException("Task displayName is required");
            }
            return new TaskMetadata(name, displayName, description, category, parameters, metadata);
        }
    }
    
    /**
     * Create a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }
}
