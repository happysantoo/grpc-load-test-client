package com.example.grpc.loadtest.payload;

import java.util.Map;
import java.util.function.Function;

/**
 * Interface for transforming request payloads before sending to the gRPC service.
 * Allows dynamic modification of request attributes based on configuration.
 */
public interface PayloadTransformer {
    
    /**
     * Transform the payload based on the configured rules
     * 
     * @param originalPayload the original payload data
     * @param transformationRules the transformation rules to apply
     * @return the transformed payload
     */
    Map<String, Object> transform(Map<String, Object> originalPayload, 
                                 Map<String, TransformationRule> transformationRules);
    
    /**
     * Represents a single transformation rule
     */
    class TransformationRule {
        private final TransformationType type;
        private final Function<Object, Object> transformer;
        private final Map<String, Object> parameters;
        
        public TransformationRule(TransformationType type, 
                                Function<Object, Object> transformer,
                                Map<String, Object> parameters) {
            this.type = type;
            this.transformer = transformer;
            this.parameters = parameters;
        }
        
        public TransformationType getType() { return type; }
        public Function<Object, Object> getTransformer() { return transformer; }
        public Map<String, Object> getParameters() { return parameters; }
    }
    
    /**
     * Types of transformations available
     */
    enum TransformationType {
        RANDOM_STRING,      // Generate random string
        RANDOM_NUMBER,      // Generate random number
        INCREMENT,          // Increment numeric value
        PREFIX,             // Add prefix to string
        SUFFIX,             // Add suffix to string
        REPLACE,            // Replace value with another
        TEMPLATE,           // Use template with variables
        CUSTOM              // Custom function
    }
}