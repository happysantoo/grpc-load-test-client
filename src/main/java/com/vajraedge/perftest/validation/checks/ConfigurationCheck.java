package com.vajraedge.perftest.validation.checks;

import com.vajraedge.perftest.validation.CheckResult;
import com.vajraedge.perftest.validation.ValidationCheck;
import com.vajraedge.perftest.validation.ValidationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates test configuration parameters for sanity and safety.
 */
@Component
public class ConfigurationCheck implements ValidationCheck {
    
    private static final int MAX_SAFE_TPS = 100_000;
    private static final int MAX_SAFE_CONCURRENCY = 50_000;
    private static final int MAX_SAFE_DURATION = 3600; // 1 hour
    private static final int WARN_HIGH_TPS = 10_000;
    private static final int WARN_HIGH_CONCURRENCY = 10_000;
    
    @Override
    public String getName() {
        return "Configuration Check";
    }
    
    @Override
    public CheckResult execute(ValidationContext context) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        // Validate TPS
        Integer targetTps = context.getTargetTps();
        if (targetTps != null) {
            if (targetTps > MAX_SAFE_TPS) {
                errors.add(String.format("Target TPS (%d) exceeds maximum safe limit (%d)", 
                    targetTps, MAX_SAFE_TPS));
            } else if (targetTps > WARN_HIGH_TPS) {
                warnings.add(String.format("Target TPS (%d) is very high - ensure target service can handle this load", 
                    targetTps));
            }
        }
        
        // Validate concurrency
        Integer maxConcurrency = context.getMaxConcurrency();
        if (maxConcurrency != null) {
            if (maxConcurrency > MAX_SAFE_CONCURRENCY) {
                errors.add(String.format("Max concurrency (%d) exceeds maximum safe limit (%d)", 
                    maxConcurrency, MAX_SAFE_CONCURRENCY));
            } else if (maxConcurrency > WARN_HIGH_CONCURRENCY) {
                warnings.add(String.format("Max concurrency (%d) is very high - verify system resources", 
                    maxConcurrency));
            }
        }
        
        // Validate duration
        Integer duration = context.getDuration();
        if (duration != null) {
            if (duration > MAX_SAFE_DURATION) {
                errors.add(String.format("Duration (%d seconds) exceeds maximum safe limit (%d seconds / 1 hour)", 
                    duration, MAX_SAFE_DURATION));
            } else if (duration < 10) {
                warnings.add(String.format("Duration (%d seconds) is very short - may not provide meaningful results", 
                    duration));
            }
        }
        
        // Validate ramp-up makes sense
        Integer startingConcurrency = context.getStartingConcurrency();
        if (startingConcurrency != null && maxConcurrency != null) {
            if (startingConcurrency > maxConcurrency) {
                errors.add(String.format("Starting concurrency (%d) cannot be greater than max concurrency (%d)", 
                    startingConcurrency, maxConcurrency));
            }
        }
        
        // Validate task type
        String taskType = context.getTaskType();
        if (taskType == null || taskType.isBlank()) {
            errors.add("Task type is required");
        }
        
        // Validate task-specific parameters
        if ("HTTP_GET".equals(taskType) || "HTTP_POST".equals(taskType)) {
            String url = (String) context.getParameters().get("url");
            if (url == null || url.isBlank()) {
                errors.add("URL parameter is required for HTTP tasks");
            } else if (!url.startsWith("http://") && !url.startsWith("https://")) {
                errors.add("URL must start with http:// or https://");
            }
        }
        
        // Return result
        if (!errors.isEmpty()) {
            return CheckResult.fail(getName(), "Configuration validation failed", errors);
        } else if (!warnings.isEmpty()) {
            return CheckResult.warn(getName(), "Configuration has warnings", warnings);
        } else {
            List<String> details = new ArrayList<>();
            details.add(String.format("✓ Target TPS: %s", targetTps != null ? targetTps : "N/A"));
            details.add(String.format("✓ Max concurrency: %s", maxConcurrency != null ? maxConcurrency : "N/A"));
            details.add(String.format("✓ Duration: %s seconds", duration != null ? duration : "N/A"));
            details.add(String.format("✓ Task type: %s", taskType));
            return CheckResult.pass(getName(), "Configuration is valid", details);
        }
    }
}
