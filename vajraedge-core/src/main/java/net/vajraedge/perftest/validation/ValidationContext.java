package net.vajraedge.perftest.validation;

import net.vajraedge.perftest.dto.TestConfigRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Context containing test configuration and metadata for validation.
 */
public class ValidationContext {
    
    private final TestConfigRequest config;
    private final Map<String, Object> additionalContext;
    
    private ValidationContext(TestConfigRequest config, Map<String, Object> additionalContext) {
        this.config = config;
        this.additionalContext = new HashMap<>(additionalContext);
    }
    
    public TestConfigRequest getConfig() {
        return config;
    }
    
    public String getTaskType() {
        return config.getTaskType();
    }
    
    public Integer getMaxTpsLimit() {
        return config.getMaxTpsLimit();
    }
    
    public Long getTestDurationSeconds() {
        return config.getTestDurationSeconds();
    }
    
    public Integer getStartingConcurrency() {
        return config.getStartingConcurrency();
    }
    
    public Integer getMaxConcurrency() {
        return config.getMaxConcurrency();
    }
    
    public Object getTaskParameter() {
        return config.getTaskParameter();
    }
    
    public Object getAdditionalContext(String key) {
        return additionalContext.get(key);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private TestConfigRequest config;
        private final Map<String, Object> additionalContext = new HashMap<>();
        
        public Builder config(TestConfigRequest config) {
            this.config = config;
            return this;
        }
        
        public Builder addContext(String key, Object value) {
            this.additionalContext.put(key, value);
            return this;
        }
        
        public ValidationContext build() {
            if (config == null) {
                throw new IllegalStateException("TestConfigRequest is required");
            }
            return new ValidationContext(config, additionalContext);
        }
    }
}
