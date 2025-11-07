package net.vajraedge.perftest.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of pre-flight validation checks.
 * Contains overall status and individual check results.
 */
public class ValidationResult {
    
    private final Status status;
    private final List<CheckResult> checkResults;
    private final String summary;
    
    private ValidationResult(Status status, List<CheckResult> checkResults, String summary) {
        this.status = status;
        this.checkResults = new ArrayList<>(checkResults);
        this.summary = summary;
    }
    
    public enum Status {
        PASS,    // All checks passed - safe to proceed
        WARN,    // Some warnings - user should review but can proceed
        FAIL     // Critical failures - test should be blocked
    }
    
    public Status getStatus() {
        return status;
    }
    
    public List<CheckResult> getCheckResults() {
        return new ArrayList<>(checkResults);
    }
    
    public String getSummary() {
        return summary;
    }
    
    public boolean canProceed() {
        return status != Status.FAIL;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final List<CheckResult> checkResults = new ArrayList<>();
        
        public Builder addCheckResult(CheckResult result) {
            this.checkResults.add(result);
            return this;
        }
        
        public ValidationResult build() {
            // Determine overall status
            Status overallStatus = Status.PASS;
            int failCount = 0;
            int warnCount = 0;
            
            for (CheckResult result : checkResults) {
                if (result.getStatus() == CheckResult.Status.FAIL) {
                    failCount++;
                    overallStatus = Status.FAIL;
                } else if (result.getStatus() == CheckResult.Status.WARN) {
                    warnCount++;
                    if (overallStatus == Status.PASS) {
                        overallStatus = Status.WARN;
                    }
                }
            }
            
            // Build summary
            String summary = String.format(
                "Validation complete: %d checks, %d passed, %d warnings, %d failures",
                checkResults.size(),
                checkResults.size() - failCount - warnCount,
                warnCount,
                failCount
            );
            
            return new ValidationResult(overallStatus, checkResults, summary);
        }
    }
}
