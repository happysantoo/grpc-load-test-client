package net.vajraedge.perftest.suite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a test suite containing multiple scenarios.
 * 
 * <p>Test suites allow complex testing patterns:
 * <ul>
 *   <li>Sequential scenarios (setup → test → teardown)</li>
 *   <li>Parallel scenarios (multiple user journeys simultaneously)</li>
 *   <li>Mixed task types with weighted distribution</li>
 *   <li>Data correlation between scenarios</li>
 * </ul>
 * 
 * @author Santhosh Kuppusamy
 * @since 1.0
 */
public class TestSuite {
    private final String suiteId;
    private final String name;
    private final String description;
    private final List<TestScenario> scenarios;
    private final ExecutionMode executionMode;
    private final boolean useCorrelation;
    private final Map<String, String> metadata;
    
    private TestSuite(Builder builder) {
        this.suiteId = builder.suiteId;
        this.name = builder.name;
        this.description = builder.description;
        this.scenarios = new ArrayList<>(builder.scenarios);
        this.executionMode = builder.executionMode;
        this.useCorrelation = builder.useCorrelation;
        this.metadata = new HashMap<>(builder.metadata);
    }
    
    public String getSuiteId() {
        return suiteId;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public List<TestScenario> getScenarios() {
        return new ArrayList<>(scenarios);
    }
    
    public ExecutionMode getExecutionMode() {
        return executionMode;
    }
    
    public boolean isUseCorrelation() {
        return useCorrelation;
    }
    
    public Map<String, String> getMetadata() {
        return new HashMap<>(metadata);
    }
    
    public int getScenarioCount() {
        return scenarios.size();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String suiteId;
        private String name;
        private String description;
        private List<TestScenario> scenarios = new ArrayList<>();
        private ExecutionMode executionMode = ExecutionMode.SEQUENTIAL;
        private boolean useCorrelation = false;
        private Map<String, String> metadata = new HashMap<>();
        
        public Builder suiteId(String suiteId) {
            this.suiteId = suiteId;
            return this;
        }
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder addScenario(TestScenario scenario) {
            this.scenarios.add(scenario);
            return this;
        }
        
        public Builder scenarios(List<TestScenario> scenarios) {
            this.scenarios.addAll(scenarios);
            return this;
        }
        
        public Builder executionMode(ExecutionMode executionMode) {
            this.executionMode = executionMode;
            return this;
        }
        
        public Builder useCorrelation(boolean useCorrelation) {
            this.useCorrelation = useCorrelation;
            return this;
        }
        
        public Builder metadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }
        
        public Builder metadata(Map<String, String> metadata) {
            this.metadata.putAll(metadata);
            return this;
        }
        
        public TestSuite build() {
            if (suiteId == null || suiteId.isBlank()) {
                throw new IllegalStateException("Suite ID is required");
            }
            if (name == null || name.isBlank()) {
                throw new IllegalStateException("Suite name is required");
            }
            if (scenarios.isEmpty()) {
                throw new IllegalStateException("At least one scenario is required");
            }
            return new TestSuite(this);
        }
    }
}
