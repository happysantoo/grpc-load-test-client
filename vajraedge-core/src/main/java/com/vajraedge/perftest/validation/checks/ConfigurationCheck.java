package com.vajraedge.perftest.validation.checks;

import com.vajraedge.perftest.constants.PerformanceTestConstants;
import com.vajraedge.perftest.constants.TaskType;
import com.vajraedge.perftest.validation.CheckResult;
import com.vajraedge.perftest.validation.ValidationCheck;
import com.vajraedge.perftest.validation.ValidationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates test configuration parameters for sanity, safety, and correctness.
 * 
 * <p>This check ensures that test configurations are within safe operational limits
 * and will not cause system resource exhaustion or other operational issues.</p>
 * 
 * <p><b>Validation Rules:</b></p>
 * <ul>
 *   <li>TPS limits must not exceed {@link PerformanceTestConstants#MAX_TPS}</li>
 *   <li>Concurrency must not exceed {@link PerformanceTestConstants#MAX_CONCURRENCY}</li>
 *   <li>Test duration must be reasonable (1 second to 1 hour)</li>
 *   <li>Starting concurrency must be <= max concurrency</li>
 *   <li>HTTP tasks must have valid URLs</li>
 * </ul>
 * 
 * @see ValidationCheck
 * @see PerformanceTestConstants
 */
@Component
public class ConfigurationCheck implements ValidationCheck {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationCheck.class);
    
    private static final int MAX_SAFE_DURATION_SECONDS = 3600; // 1 hour
    private static final int MIN_MEANINGFUL_DURATION_SECONDS = 10;
    private static final int WARN_HIGH_TPS_THRESHOLD = 10_000;
    private static final int WARN_HIGH_CONCURRENCY_THRESHOLD = 10_000;
    
    @Override
    public String getName() {
        return "Configuration Check";
    }
    
    @Override
    public CheckResult execute(ValidationContext context) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        // Validate TPS
        Integer maxTpsLimit = context.getMaxTpsLimit();
        if (maxTpsLimit != null) {
            if (maxTpsLimit > PerformanceTestConstants.MAX_TPS) {
                errors.add(String.format("Max TPS limit (%d) exceeds maximum safe limit (%d)", 
                    maxTpsLimit, PerformanceTestConstants.MAX_TPS));
                logger.warn("Configuration rejected: TPS {} exceeds max {}", maxTpsLimit, PerformanceTestConstants.MAX_TPS);
            } else if (maxTpsLimit > WARN_HIGH_TPS_THRESHOLD) {
                warnings.add(String.format("Max TPS limit (%d) is very high - ensure target service can handle this load", 
                    maxTpsLimit));
                logger.debug("High TPS configuration detected: {}", maxTpsLimit);
            }
        }
        
        // Validate concurrency
        Integer maxConcurrency = context.getMaxConcurrency();
        if (maxConcurrency != null) {
            if (maxConcurrency > PerformanceTestConstants.MAX_CONCURRENCY) {
                errors.add(String.format("Max concurrency (%d) exceeds maximum safe limit (%d)", 
                    maxConcurrency, PerformanceTestConstants.MAX_CONCURRENCY));
                logger.warn("Configuration rejected: concurrency {} exceeds max {}", maxConcurrency, PerformanceTestConstants.MAX_CONCURRENCY);
            } else if (maxConcurrency > WARN_HIGH_CONCURRENCY_THRESHOLD) {
                warnings.add(String.format("Max concurrency (%d) is very high - verify system resources", 
                    maxConcurrency));
                logger.debug("High concurrency configuration detected: {}", maxConcurrency);
            }
        }
        
        // Validate duration
        Long testDuration = context.getTestDurationSeconds();
        if (testDuration != null) {
            if (testDuration > MAX_SAFE_DURATION_SECONDS) {
                errors.add(String.format("Test duration (%d seconds) exceeds maximum safe limit (%d seconds / 1 hour)", 
                    testDuration, MAX_SAFE_DURATION_SECONDS));
                logger.warn("Configuration rejected: test duration {} exceeds max {} seconds", testDuration, MAX_SAFE_DURATION_SECONDS);
            } else if (testDuration < MIN_MEANINGFUL_DURATION_SECONDS) {
                warnings.add(String.format("Test duration (%d seconds) is very short - may not provide meaningful results", 
                    testDuration));
                logger.debug("Short duration test configured: {} seconds", testDuration);
            }
        }
        
        // Validate ramp-up makes sense
        Integer startingConcurrency = context.getStartingConcurrency();
        if (startingConcurrency != null && maxConcurrency != null) {
            if (startingConcurrency > maxConcurrency) {
                errors.add(String.format("Starting concurrency (%d) cannot be greater than max concurrency (%d)", 
                    startingConcurrency, maxConcurrency));
                logger.warn("Configuration rejected: starting concurrency {} > max concurrency {}", startingConcurrency, maxConcurrency);
            }
        }
        
        // Validate task type
        String taskTypeStr = context.getTaskType();
        if (taskTypeStr == null || taskTypeStr.isBlank()) {
            errors.add("Task type is required");
            logger.warn("Configuration rejected: task type is missing");
        } else {
            // Parse task type to validate it
            TaskType taskType = TaskType.fromString(taskTypeStr);
            logger.debug("Validating task type: {} (parsed as: {})", taskTypeStr, taskType);
            
            // Validate task-specific parameters
            if (taskType != null && taskType.isHttpTask()) {
                validateHttpTaskParameters(context, errors);
            }
        }
        
        // Return result
        if (!errors.isEmpty()) {
            logger.warn("Configuration validation failed with {} errors", errors.size());
            return CheckResult.fail(getName(), "Configuration validation failed", errors);
        } else if (!warnings.isEmpty()) {
            logger.info("Configuration validation passed with {} warnings", warnings.size());
            return CheckResult.warn(getName(), "Configuration has warnings", warnings);
        } else {
            logger.info("Configuration validation passed");
            List<String> details = new ArrayList<>();
            details.add(String.format("✓ Max TPS limit: %s", maxTpsLimit != null ? maxTpsLimit : "N/A"));
            details.add(String.format("✓ Max concurrency: %s", maxConcurrency != null ? maxConcurrency : "N/A"));
            details.add(String.format("✓ Test duration: %s seconds", testDuration != null ? testDuration : "N/A"));
            details.add(String.format("✓ Task type: %s", taskTypeStr));
            return CheckResult.pass(getName(), "Configuration is valid", details);
        }
    }
    
    /**
     * Validates HTTP task-specific parameters (URL format).
     * 
     * @param context validation context
     * @param errors list to accumulate errors
     */
    private void validateHttpTaskParameters(ValidationContext context, List<String> errors) {
        Object taskParam = context.getTaskParameter();
        String url = taskParam instanceof String ? (String) taskParam : null;
        
        if (url == null || url.isBlank()) {
            errors.add("URL parameter is required for HTTP tasks");
            logger.warn("HTTP task validation failed: URL is missing");
        } else if (!url.startsWith("http://") && !url.startsWith("https://")) {
            errors.add("URL must start with http:// or https://");
            logger.warn("HTTP task validation failed: invalid URL scheme: {}", url);
        } else {
            logger.debug("HTTP task URL validated: {}", url);
        }
    }
}
