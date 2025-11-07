package net.vajraedge.perftest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Request DTO for configuring a test scenario within a suite.
 * 
 * @author Santhosh Kuppusamy
 * @since 1.0
 */
public class ScenarioConfigRequest {
    
    @NotBlank(message = "Scenario ID is required")
    private String scenarioId;
    
    @NotBlank(message = "Scenario name is required")
    private String name;
    
    private String description;
    
    @Valid
    @NotNull(message = "Test configuration is required")
    private TestConfigRequest config;
    
    @Valid
    private TaskMixRequest taskMix;
    
    private Map<String, String> metadata = new HashMap<>();
    
    public String getScenarioId() {
        return scenarioId;
    }
    
    public void setScenarioId(String scenarioId) {
        this.scenarioId = scenarioId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public TestConfigRequest getConfig() {
        return config;
    }
    
    public void setConfig(TestConfigRequest config) {
        this.config = config;
    }
    
    public TaskMixRequest getTaskMix() {
        return taskMix;
    }
    
    public void setTaskMix(TaskMixRequest taskMix) {
        this.taskMix = taskMix;
    }
    
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
}
