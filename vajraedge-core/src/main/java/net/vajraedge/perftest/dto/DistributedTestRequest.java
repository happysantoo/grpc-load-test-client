package net.vajraedge.perftest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * DTO for distributed test configuration requests.
 * Simplified configuration focused on TPS-based distributed testing.
 */
public class DistributedTestRequest {
    
    @NotNull(message = "Task type cannot be null")
    private String taskType;
    
    @NotNull(message = "Target TPS cannot be null")
    @Min(value = 1, message = "Target TPS must be at least 1")
    private Integer targetTps;
    
    @NotNull(message = "Test duration cannot be null")
    @Min(value = 1, message = "Test duration must be at least 1 second")
    private Integer durationSeconds;
    
    @Min(value = 0, message = "Ramp-up duration cannot be negative")
    private Integer rampUpSeconds = 0;
    
    @Min(value = 1, message = "Max concurrency must be at least 1")
    private Integer maxConcurrency = 10000;
    
    private Map<String, String> taskParameters;
    
    private Integer minWorkers; // Minimum workers required
    
    // Getters and Setters
    
    public String getTaskType() {
        return taskType;
    }
    
    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }
    
    public Integer getTargetTps() {
        return targetTps;
    }
    
    public void setTargetTps(Integer targetTps) {
        this.targetTps = targetTps;
    }
    
    public Integer getDurationSeconds() {
        return durationSeconds;
    }
    
    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }
    
    public Integer getRampUpSeconds() {
        return rampUpSeconds;
    }
    
    public void setRampUpSeconds(Integer rampUpSeconds) {
        this.rampUpSeconds = rampUpSeconds;
    }
    
    public Integer getMaxConcurrency() {
        return maxConcurrency;
    }
    
    public void setMaxConcurrency(Integer maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }
    
    public Map<String, String> getTaskParameters() {
        return taskParameters;
    }
    
    public void setTaskParameters(Map<String, String> taskParameters) {
        this.taskParameters = taskParameters;
    }
    
    public Integer getMinWorkers() {
        return minWorkers;
    }
    
    public void setMinWorkers(Integer minWorkers) {
        this.minWorkers = minWorkers;
    }
}
