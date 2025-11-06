package com.vajraedge.perftest.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Main orchestrator for pre-flight validation.
 * Runs all validation checks and aggregates results.
 */
@Service
public class PreFlightValidator {
    
    private static final Logger log = LoggerFactory.getLogger(PreFlightValidator.class);
    
    private final List<ValidationCheck> checks;
    
    public PreFlightValidator(List<ValidationCheck> checks) {
        this.checks = checks;
        log.info("PreFlightValidator initialized with {} checks", checks.size());
    }
    
    /**
     * Validate the test configuration before execution.
     *
     * @param context the validation context containing test configuration
     * @return the validation result with overall status
     */
    public ValidationResult validate(ValidationContext context) {
        log.info("Starting pre-flight validation for test: {}", context.getTaskType());
        
        ValidationResult.Builder builder = ValidationResult.builder();
        
        for (ValidationCheck check : checks) {
            try {
                log.debug("Running check: {}", check.getName());
                CheckResult result = check.execute(context);
                builder.addCheckResult(result);
                
                log.debug("Check '{}' completed with status: {}", 
                    check.getName(), result.getStatus());
                
            } catch (Exception e) {
                log.error("Check '{}' failed with exception", check.getName(), e);
                CheckResult errorResult = CheckResult.fail(
                    check.getName(), 
                    "Check failed with exception: " + e.getMessage(),
                    List.of("Internal error during validation", 
                           "Contact support if this persists")
                );
                builder.addCheckResult(errorResult);
            }
        }
        
        ValidationResult result = builder.build();
        log.info("Pre-flight validation completed: {} - {}", 
            result.getStatus(), result.getSummary());
        
        return result;
    }
}
