package com.example.perftest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for test configuration requests.
 */
public class TestConfigRequest {
    
    @NotNull(message = "Target TPS cannot be null")
    @Min(value = 1, message = "Target TPS must be at least 1")
    @Max(value = 100000, message = "Target TPS cannot exceed 100,000")
    private Integer targetTps;
    
    @NotNull(message = "Max concurrency cannot be null")
    @Min(value = 1, message = "Max concurrency must be at least 1")
    @Max(value = 50000, message = "Max concurrency cannot exceed 50,000")
    private Integer maxConcurrency;
    
    @NotNull(message = "Test duration in seconds cannot be null")
    @Min(value = 1, message = "Test duration must be at least 1 second")
    private Integer testDurationSeconds;
    
    @NotNull(message = "Ramp-up duration in seconds cannot be null")
    @Min(value = 0, message = "Ramp-up duration cannot be negative")
    private Integer rampUpDurationSeconds;
    
    private String taskType = "SLEEP";
    private Integer taskParameter = 10;
    
    // Getters and Setters
    
    public Integer getTargetTps() {
        return targetTps;
    }
    
    public void setTargetTps(Integer targetTps) {
        this.targetTps = targetTps;
    }
    
    public Integer getMaxConcurrency() {
        return maxConcurrency;
    }
    
    public void setMaxConcurrency(Integer maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }
    
    public Integer getTestDurationSeconds() {
        return testDurationSeconds;
    }
    
    public void setTestDurationSeconds(Integer testDurationSeconds) {
        this.testDurationSeconds = testDurationSeconds;
    }
    
    public Integer getRampUpDurationSeconds() {
        return rampUpDurationSeconds;
    }
    
    public void setRampUpDurationSeconds(Integer rampUpDurationSeconds) {
        this.rampUpDurationSeconds = rampUpDurationSeconds;
    }
    
    public String getTaskType() {
        return taskType;
    }
    
    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }
    
    public Integer getTaskParameter() {
        return taskParameter;
    }
    
    public void setTaskParameter(Integer taskParameter) {
        this.taskParameter = taskParameter;
    }
}
