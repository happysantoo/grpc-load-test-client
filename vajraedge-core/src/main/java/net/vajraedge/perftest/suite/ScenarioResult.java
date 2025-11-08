package net.vajraedge.perftest.suite;

import net.vajraedge.perftest.metrics.MetricsSnapshot;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Result of executing a single scenario within a test suite.
 * 
 * <p>Contains metrics, status, timing, and task mix distribution.
 * 
 * @author Santhosh Kuppusamy
 * @since 1.0
 */
public class ScenarioResult {
    private final String scenarioId;
    private final String scenarioName;
    private final ScenarioStatus status;
    private final Instant startTime;
    private final Instant endTime;
    private final MetricsSnapshot metrics;
    private final Map<String, Long> taskMixDistribution;
    private final String errorMessage;
    private final Map<String, Object> metadata;
    
    private ScenarioResult(Builder builder) {
        this.scenarioId = builder.scenarioId;
        this.scenarioName = builder.scenarioName;
        this.status = builder.status;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.metrics = builder.metrics;
        this.taskMixDistribution = builder.taskMixDistribution;
        this.errorMessage = builder.errorMessage;
        this.metadata = builder.metadata;
    }
    
    public String getScenarioId() {
        return scenarioId;
    }
    
    public String getScenarioName() {
        return scenarioName;
    }
    
    public ScenarioStatus getStatus() {
        return status;
    }
    
    public Instant getStartTime() {
        return startTime;
    }
    
    public Instant getEndTime() {
        return endTime;
    }
    
    public MetricsSnapshot getMetrics() {
        return metrics;
    }
    
    public Map<String, Long> getTaskMixDistribution() {
        return taskMixDistribution != null ? new HashMap<>(taskMixDistribution) : Map.of();
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata != null ? new HashMap<>(metadata) : Map.of();
    }
    
    public long getDurationMillis() {
        if (startTime == null || endTime == null) {
            return 0;
        }
        return endTime.toEpochMilli() - startTime.toEpochMilli();
    }
    
    public boolean isSuccessful() {
        return status == ScenarioStatus.COMPLETED;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String scenarioId;
        private String scenarioName;
        private ScenarioStatus status;
        private Instant startTime;
        private Instant endTime;
        private MetricsSnapshot metrics;
        private Map<String, Long> taskMixDistribution = new HashMap<>();
        private String errorMessage;
        private Map<String, Object> metadata = new HashMap<>();
        
        public Builder scenarioId(String scenarioId) {
            this.scenarioId = scenarioId;
            return this;
        }
        
        public Builder scenarioName(String scenarioName) {
            this.scenarioName = scenarioName;
            return this;
        }
        
        public Builder status(ScenarioStatus status) {
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
        
        public Builder metrics(MetricsSnapshot metrics) {
            this.metrics = metrics;
            return this;
        }
        
        public Builder taskMixDistribution(Map<String, Long> distribution) {
            this.taskMixDistribution.putAll(distribution);
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        
        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }
        
        public ScenarioResult build() {
            return new ScenarioResult(this);
        }
    }
    
    public enum ScenarioStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        STOPPED
    }
}
