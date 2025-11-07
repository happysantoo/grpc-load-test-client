package net.vajraedge.perftest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Request DTO for configuring a test suite.
 * 
 * @author Santhosh Kuppusamy
 * @since 1.0
 */
public class SuiteConfigRequest {
    
    @NotBlank(message = "Suite ID is required")
    private String suiteId;
    
    @NotBlank(message = "Suite name is required")
    private String name;
    
    private String description;
    
    @Valid
    @NotEmpty(message = "At least one scenario is required")
    private List<ScenarioConfigRequest> scenarios = new ArrayList<>();
    
    @NotNull(message = "Execution mode is required")
    private String executionMode = "SEQUENTIAL";
    
    private boolean useCorrelation = false;
    
    private Map<String, String> metadata = new HashMap<>();
    
    public String getSuiteId() {
        return suiteId;
    }
    
    public void setSuiteId(String suiteId) {
        this.suiteId = suiteId;
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
    
    public List<ScenarioConfigRequest> getScenarios() {
        return scenarios;
    }
    
    public void setScenarios(List<ScenarioConfigRequest> scenarios) {
        this.scenarios = scenarios;
    }
    
    public String getExecutionMode() {
        return executionMode;
    }
    
    public void setExecutionMode(String executionMode) {
        this.executionMode = executionMode;
    }
    
    public boolean isUseCorrelation() {
        return useCorrelation;
    }
    
    public void setUseCorrelation(boolean useCorrelation) {
        this.useCorrelation = useCorrelation;
    }
    
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
}
