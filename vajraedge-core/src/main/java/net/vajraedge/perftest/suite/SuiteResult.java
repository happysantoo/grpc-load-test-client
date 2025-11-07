package net.vajraedge.perftest.suite;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregated result of executing an entire test suite.
 * 
 * <p>Contains status, scenario results, suite-level metrics,
 * and correlation statistics.
 * 
 * @author Santhosh Kuppusamy
 * @since 1.0
 */
public class SuiteResult {
    private final String suiteId;
    private final String suiteName;
    private final SuiteStatus status;
    private final Instant startTime;
    private final Instant endTime;
    private final List<ScenarioResult> scenarioResults;
    private final ExecutionMode executionMode;
    private final Map<String, Object> suiteMetrics;
    private final String errorMessage;
    
    private SuiteResult(Builder builder) {
        this.suiteId = builder.suiteId;
        this.suiteName = builder.suiteName;
        this.status = builder.status;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.scenarioResults = new ArrayList<>(builder.scenarioResults);
        this.executionMode = builder.executionMode;
        this.suiteMetrics = new HashMap<>(builder.suiteMetrics);
        this.errorMessage = builder.errorMessage;
    }
    
    public String getSuiteId() {
        return suiteId;
    }
    
    public String getSuiteName() {
        return suiteName;
    }
    
    public SuiteStatus getStatus() {
        return status;
    }
    
    public Instant getStartTime() {
        return startTime;
    }
    
    public Instant getEndTime() {
        return endTime;
    }
    
    public List<ScenarioResult> getScenarioResults() {
        return new ArrayList<>(scenarioResults);
    }
    
    public ExecutionMode getExecutionMode() {
        return executionMode;
    }
    
    public Map<String, Object> getSuiteMetrics() {
        return new HashMap<>(suiteMetrics);
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public long getDurationMillis() {
        if (startTime == null || endTime == null) {
            return 0;
        }
        return endTime.toEpochMilli() - startTime.toEpochMilli();
    }
    
    public int getTotalScenarios() {
        return scenarioResults.size();
    }
    
    public int getSuccessfulScenarios() {
        return (int) scenarioResults.stream()
            .filter(ScenarioResult::isSuccessful)
            .count();
    }
    
    public int getFailedScenarios() {
        return (int) scenarioResults.stream()
            .filter(r -> r.getStatus() == ScenarioResult.ScenarioStatus.FAILED)
            .count();
    }
    
    public boolean isSuccessful() {
        return status == SuiteStatus.COMPLETED && getFailedScenarios() == 0;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String suiteId;
        private String suiteName;
        private SuiteStatus status;
        private Instant startTime;
        private Instant endTime;
        private List<ScenarioResult> scenarioResults = new ArrayList<>();
        private ExecutionMode executionMode;
        private Map<String, Object> suiteMetrics = new HashMap<>();
        private String errorMessage;
        
        public Builder suiteId(String suiteId) {
            this.suiteId = suiteId;
            return this;
        }
        
        public Builder suiteName(String suiteName) {
            this.suiteName = suiteName;
            return this;
        }
        
        public Builder status(SuiteStatus status) {
            this.status = status;
            return this;
        }
        
        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }
        
        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }
        
        public Builder addScenarioResult(ScenarioResult result) {
            this.scenarioResults.add(result);
            return this;
        }
        
        public Builder scenarioResults(List<ScenarioResult> results) {
            this.scenarioResults.addAll(results);
            return this;
        }
        
        public Builder executionMode(ExecutionMode executionMode) {
            this.executionMode = executionMode;
            return this;
        }
        
        public Builder suiteMetric(String key, Object value) {
            this.suiteMetrics.put(key, value);
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        
        public SuiteResult build() {
            return new SuiteResult(this);
        }
    }
    
    public enum SuiteStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        STOPPED
    }
}
