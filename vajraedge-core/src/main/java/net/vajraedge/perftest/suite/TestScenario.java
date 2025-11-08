package net.vajraedge.perftest.suite;

import net.vajraedge.perftest.dto.TestConfigRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a single scenario within a test suite.
 * 
 * <p>A scenario is a complete test configuration that executes as part of a suite.
 * Scenarios can use task mixes for varied load patterns and correlation contexts
 * for data sharing with other scenarios.
 * 
 * @author Santhosh Kuppusamy
 * @since 1.0
 */
public class TestScenario {
    private final String scenarioId;
    private final String name;
    private final String description;
    private final TestConfigRequest config;
    private final TaskMix taskMix;
    private final Map<String, String> metadata;
    
    private TestScenario(Builder builder) {
        this.scenarioId = builder.scenarioId;
        this.name = builder.name;
        this.description = builder.description;
        this.config = builder.config;
        this.taskMix = builder.taskMix;
        this.metadata = builder.metadata;
    }
    
    public String getScenarioId() {
        return scenarioId;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public TestConfigRequest getConfig() {
        return config;
    }
    
    public TaskMix getTaskMix() {
        return taskMix;
    }
    
    public Map<String, String> getMetadata() {
        return new HashMap<>(metadata);
    }
    
    public boolean hasTaskMix() {
        return taskMix != null && !taskMix.isEmpty();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String scenarioId;
        private String name;
        private String description;
        private TestConfigRequest config;
        private TaskMix taskMix;
        private Map<String, String> metadata = new HashMap<>();
        
        public Builder scenarioId(String scenarioId) {
            this.scenarioId = scenarioId;
            return this;
        }
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder config(TestConfigRequest config) {
            this.config = config;
            return this;
        }
        
        public Builder taskMix(TaskMix taskMix) {
            this.taskMix = taskMix;
            return this;
        }
        
        public Builder metadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }
        
        public Builder metadata(Map<String, String> metadata) {
            this.metadata.putAll(metadata);
            return this;
        }
        
        public TestScenario build() {
            if (scenarioId == null || scenarioId.isBlank()) {
                throw new IllegalStateException("Scenario ID is required");
            }
            if (name == null || name.isBlank()) {
                throw new IllegalStateException("Scenario name is required");
            }
            if (config == null) {
                throw new IllegalStateException("Test configuration is required");
            }
            return new TestScenario(this);
        }
    }
}
