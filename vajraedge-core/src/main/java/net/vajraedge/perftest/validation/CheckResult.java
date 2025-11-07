package net.vajraedge.perftest.validation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of an individual validation check.
 */
public class CheckResult {
    
    private final String checkName;
    private final Status status;
    private final String message;
    private final Duration duration;
    private final List<String> details;
    
    private CheckResult(String checkName, Status status, String message, Duration duration, List<String> details) {
        this.checkName = checkName;
        this.status = status;
        this.message = message;
        this.duration = duration;
        this.details = new ArrayList<>(details);
    }
    
    public enum Status {
        PASS,    // Check passed
        WARN,    // Check passed with warnings
        FAIL,    // Check failed
        SKIP     // Check was skipped (not applicable)
    }
    
    public String getCheckName() {
        return checkName;
    }
    
    public Status getStatus() {
        return status;
    }
    
    public String getMessage() {
        return message;
    }
    
    public Duration getDuration() {
        return duration;
    }
    
    public List<String> getDetails() {
        return new ArrayList<>(details);
    }
    
    public static CheckResult pass(String checkName, String message) {
        return new CheckResult(checkName, Status.PASS, message, Duration.ZERO, List.of());
    }
    
    public static CheckResult pass(String checkName, String message, Duration duration) {
        return new CheckResult(checkName, Status.PASS, message, duration, List.of());
    }
    
    public static CheckResult pass(String checkName, String message, List<String> details) {
        return new CheckResult(checkName, Status.PASS, message, Duration.ZERO, details);
    }
    
    public static CheckResult warn(String checkName, String message, List<String> details) {
        return new CheckResult(checkName, Status.WARN, message, Duration.ZERO, details);
    }
    
    public static CheckResult fail(String checkName, String message, List<String> details) {
        return new CheckResult(checkName, Status.FAIL, message, Duration.ZERO, details);
    }
    
    public static CheckResult skip(String checkName, String message) {
        return new CheckResult(checkName, Status.SKIP, message, Duration.ZERO, List.of());
    }
}
