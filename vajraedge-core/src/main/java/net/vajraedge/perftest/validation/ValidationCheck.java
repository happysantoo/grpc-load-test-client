package net.vajraedge.perftest.validation;

/**
 * Interface for validation checks.
 * Each check validates a specific aspect of the test configuration.
 */
public interface ValidationCheck {
    
    /**
     * Execute the validation check.
     *
     * @param context the validation context containing test configuration
     * @return the result of this validation check
     */
    CheckResult execute(ValidationContext context);
    
    /**
     * Get the name of this validation check.
     *
     * @return the check name
     */
    String getName();
}
