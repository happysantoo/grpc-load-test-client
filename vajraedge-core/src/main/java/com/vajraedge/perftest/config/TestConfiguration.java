package com.vajraedge.perftest.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Duration;

/**
 * Configuration class for performance test parameters.
 * Includes validation to ensure sensible values.
 */
public class TestConfiguration {
    
    @NotNull(message = "Target TPS cannot be null")
    @Min(value = 1, message = "Target TPS must be at least 1")
    @Max(value = 100000, message = "Target TPS cannot exceed 100,000")
    private Integer targetTps;
    
    @NotNull(message = "Max concurrency cannot be null")
    @Min(value = 1, message = "Max concurrency must be at least 1")
    @Max(value = 50000, message = "Max concurrency cannot exceed 50,000")
    private Integer maxConcurrency;
    
    @NotNull(message = "Test duration cannot be null")
    private Duration testDuration;
    
    @NotNull(message = "Ramp-up duration cannot be null")
    private Duration rampUpDuration;
    
    private String taskType = "SLEEP"; // Default task type
    
    private Integer taskParameter = 10; // Default sleep time in ms
    
    public TestConfiguration() {
        // Default constructor for JSON deserialization
    }
    
    public TestConfiguration(Integer targetTps, Integer maxConcurrency, 
                            Duration testDuration, Duration rampUpDuration) {
        this.targetTps = targetTps;
        this.maxConcurrency = maxConcurrency;
        this.testDuration = testDuration;
        this.rampUpDuration = rampUpDuration;
    }
    
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
    
    public Duration getTestDuration() {
        return testDuration;
    }
    
    public void setTestDuration(Duration testDuration) {
        this.testDuration = testDuration;
    }
    
    public Duration getRampUpDuration() {
        return rampUpDuration;
    }
    
    public void setRampUpDuration(Duration rampUpDuration) {
        this.rampUpDuration = rampUpDuration;
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
    
    @Override
    public String toString() {
        return String.format("TestConfiguration{targetTps=%d, maxConcurrency=%d, " +
                           "testDuration=%s, rampUpDuration=%s, taskType='%s', taskParameter=%d}",
                targetTps, maxConcurrency, testDuration, rampUpDuration, taskType, taskParameter);
    }
}
