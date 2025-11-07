package net.vajraedge.perftest.dto;

import net.vajraedge.perftest.concurrency.LoadTestMode;
import net.vajraedge.perftest.concurrency.RampStrategyType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for test configuration requests.
 * Supports both concurrency-based and rate-limited load testing modes.
 */
public class TestConfigRequest {
    
    // Load test mode
    @NotNull(message = "Load test mode cannot be null")
    private LoadTestMode mode = LoadTestMode.CONCURRENCY_BASED;
    
    // Concurrency-based parameters
    @NotNull(message = "Starting concurrency cannot be null")
    @Min(value = 1, message = "Starting concurrency must be at least 1")
    @Max(value = 10000, message = "Starting concurrency cannot exceed 10,000")
    private Integer startingConcurrency = 10;
    
    @NotNull(message = "Max concurrency cannot be null")
    @Min(value = 1, message = "Max concurrency must be at least 1")
    @Max(value = 50000, message = "Max concurrency cannot exceed 50,000")
    private Integer maxConcurrency = 100;
    
    @NotNull(message = "Ramp strategy type cannot be null")
    private RampStrategyType rampStrategyType = RampStrategyType.STEP;
    
    // Step ramp parameters
    @Min(value = 1, message = "Ramp step must be at least 1")
    private Integer rampStep = 10;
    
    @Min(value = 1, message = "Ramp interval must be at least 1 second")
    private Long rampIntervalSeconds = 30L;
    
    // Linear ramp parameters
    @Min(value = 1, message = "Ramp duration must be at least 1 second")
    private Long rampDurationSeconds;
    
    // Sustain phase parameters
    @Min(value = 0, message = "Sustain duration cannot be negative")
    private Long sustainDurationSeconds = 0L;
    
    // Common parameters
    @NotNull(message = "Test duration in seconds cannot be null")
    @Min(value = 1, message = "Test duration must be at least 1 second")
    @Max(value = 86400, message = "Test duration cannot exceed 24 hours")
    private Long testDurationSeconds = 300L;
    
    // Hybrid mode: optional max TPS limit
    @Min(value = 1, message = "Max TPS limit must be at least 1")
    private Integer maxTpsLimit;
    
    // Task configuration
    @NotNull(message = "Task type cannot be null")
    private String taskType = "HTTP";
    
    private Object taskParameter = "http://localhost:8081/api/products";
    
    // Validation
    @AssertTrue(message = "Invalid configuration for selected ramp strategy")
    public boolean isValidRampConfiguration() {
        if (rampStrategyType == RampStrategyType.STEP) {
            return rampStep != null && rampIntervalSeconds != null;
        } else if (rampStrategyType == RampStrategyType.LINEAR) {
            return rampDurationSeconds != null;
        }
        return true;
    }
    
    @AssertTrue(message = "Max concurrency must be >= starting concurrency")
    public boolean isValidConcurrencyRange() {
        if (startingConcurrency == null || maxConcurrency == null) {
            return true; // Let @NotNull handle this
        }
        return maxConcurrency >= startingConcurrency;
    }
    
    @AssertTrue(message = "Max TPS limit only applies to RATE_LIMITED mode")
    public boolean isValidMaxTpsLimit() {
        if (maxTpsLimit != null) {
            return mode == LoadTestMode.RATE_LIMITED;
        }
        return true;
    }
    
    // Getters and Setters
    
    public LoadTestMode getMode() {
        return mode;
    }
    
    public void setMode(LoadTestMode mode) {
        this.mode = mode;
    }
    
    public Integer getStartingConcurrency() {
        return startingConcurrency;
    }
    
    public void setStartingConcurrency(Integer startingConcurrency) {
        this.startingConcurrency = startingConcurrency;
    }
    
    public Integer getMaxConcurrency() {
        return maxConcurrency;
    }
    
    public void setMaxConcurrency(Integer maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }
    
    public RampStrategyType getRampStrategyType() {
        return rampStrategyType;
    }
    
    public void setRampStrategyType(RampStrategyType rampStrategyType) {
        this.rampStrategyType = rampStrategyType;
    }
    
    public Integer getRampStep() {
        return rampStep;
    }
    
    public void setRampStep(Integer rampStep) {
        this.rampStep = rampStep;
    }
    
    public Long getRampIntervalSeconds() {
        return rampIntervalSeconds;
    }
    
    public void setRampIntervalSeconds(Long rampIntervalSeconds) {
        this.rampIntervalSeconds = rampIntervalSeconds;
    }
    
    public Long getRampDurationSeconds() {
        return rampDurationSeconds;
    }
    
    public void setRampDurationSeconds(Long rampDurationSeconds) {
        this.rampDurationSeconds = rampDurationSeconds;
    }
    
    public Long getSustainDurationSeconds() {
        return sustainDurationSeconds;
    }
    
    public void setSustainDurationSeconds(Long sustainDurationSeconds) {
        this.sustainDurationSeconds = sustainDurationSeconds;
    }
    
    public Long getTestDurationSeconds() {
        return testDurationSeconds;
    }
    
    public void setTestDurationSeconds(Long testDurationSeconds) {
        this.testDurationSeconds = testDurationSeconds;
    }
    
    public Integer getMaxTpsLimit() {
        return maxTpsLimit;
    }
    
    public void setMaxTpsLimit(Integer maxTpsLimit) {
        this.maxTpsLimit = maxTpsLimit;
    }
    
    public String getTaskType() {
        return taskType;
    }
    
    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }
    
    public Object getTaskParameter() {
        return taskParameter;
    }
    
    public void setTaskParameter(Object taskParameter) {
        this.taskParameter = taskParameter;
    }
}
