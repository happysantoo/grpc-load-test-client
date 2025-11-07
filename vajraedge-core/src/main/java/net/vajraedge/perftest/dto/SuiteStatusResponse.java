package net.vajraedge.perftest.dto;

import net.vajraedge.perftest.suite.ScenarioResult;
import net.vajraedge.perftest.suite.SuiteResult;
import net.vajraedge.perftest.metrics.MetricsSnapshot;
import net.vajraedge.perftest.service.MetricsService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for suite execution status.
 * 
 * @author Santhosh Kuppusamy
 * @since 1.0
 */
public class SuiteStatusResponse {
    private String suiteId;
    private String suiteName;
    private String status;
    private Instant startTime;
    private Instant endTime;
    private long durationMillis;
    private String executionMode;
    private int totalScenarios;
    private int completedScenarios;
    private int successfulScenarios;
    private int failedScenarios;
    private double progress;
    private List<ScenarioStatusDto> scenarios = new ArrayList<>();
    private Map<String, Object> suiteMetrics = new HashMap<>();
    private String errorMessage;
    
    public static SuiteStatusResponse fromResult(SuiteResult result) {
        SuiteStatusResponse response = new SuiteStatusResponse();
        response.setSuiteId(result.getSuiteId());
        response.setSuiteName(result.getSuiteName());
        response.setStatus(result.getStatus().name());
        response.setStartTime(result.getStartTime());
        response.setEndTime(result.getEndTime());
        response.setDurationMillis(result.getDurationMillis());
        response.setExecutionMode(result.getExecutionMode().name());
        response.setTotalScenarios(result.getTotalScenarios());
        response.setCompletedScenarios(result.getTotalScenarios());
        response.setSuccessfulScenarios(result.getSuccessfulScenarios());
        response.setFailedScenarios(result.getFailedScenarios());
        response.setProgress(100.0);
        response.setSuiteMetrics(result.getSuiteMetrics());
        response.setErrorMessage(result.getErrorMessage());
        
        // Convert scenario results
        for (ScenarioResult scenarioResult : result.getScenarioResults()) {
            response.getScenarios().add(ScenarioStatusDto.fromResult(scenarioResult));
        }
        
        return response;
    }
    
    // Getters and setters
    public String getSuiteId() {
        return suiteId;
    }
    
    public void setSuiteId(String suiteId) {
        this.suiteId = suiteId;
    }
    
    public String getSuiteName() {
        return suiteName;
    }
    
    public void setSuiteName(String suiteName) {
        this.suiteName = suiteName;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Instant getStartTime() {
        return startTime;
    }
    
    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }
    
    public Instant getEndTime() {
        return endTime;
    }
    
    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }
    
    public long getDurationMillis() {
        return durationMillis;
    }
    
    public void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }
    
    public String getExecutionMode() {
        return executionMode;
    }
    
    public void setExecutionMode(String executionMode) {
        this.executionMode = executionMode;
    }
    
    public int getTotalScenarios() {
        return totalScenarios;
    }
    
    public void setTotalScenarios(int totalScenarios) {
        this.totalScenarios = totalScenarios;
    }
    
    public int getCompletedScenarios() {
        return completedScenarios;
    }
    
    public void setCompletedScenarios(int completedScenarios) {
        this.completedScenarios = completedScenarios;
    }
    
    public int getSuccessfulScenarios() {
        return successfulScenarios;
    }
    
    public void setSuccessfulScenarios(int successfulScenarios) {
        this.successfulScenarios = successfulScenarios;
    }
    
    public int getFailedScenarios() {
        return failedScenarios;
    }
    
    public void setFailedScenarios(int failedScenarios) {
        this.failedScenarios = failedScenarios;
    }
    
    public double getProgress() {
        return progress;
    }
    
    public void setProgress(double progress) {
        this.progress = progress;
    }
    
    public List<ScenarioStatusDto> getScenarios() {
        return scenarios;
    }
    
    public void setScenarios(List<ScenarioStatusDto> scenarios) {
        this.scenarios = scenarios;
    }
    
    public Map<String, Object> getSuiteMetrics() {
        return suiteMetrics;
    }
    
    public void setSuiteMetrics(Map<String, Object> suiteMetrics) {
        this.suiteMetrics = suiteMetrics;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    /**
     * DTO for individual scenario status within a suite.
     */
    public static class ScenarioStatusDto {
        private String scenarioId;
        private String scenarioName;
        private String status;
        private Instant startTime;
        private Instant endTime;
        private long durationMillis;
        private MetricsResponse metrics;
        private Map<String, Long> taskMixDistribution = new HashMap<>();
        private String errorMessage;
        
        public static ScenarioStatusDto fromResult(ScenarioResult result) {
            return fromResult(result, null);
        }
        
        public static ScenarioStatusDto fromResult(ScenarioResult result, MetricsService metricsService) {
            ScenarioStatusDto dto = new ScenarioStatusDto();
            dto.setScenarioId(result.getScenarioId());
            dto.setScenarioName(result.getScenarioName());
            dto.setStatus(result.getStatus().name());
            dto.setStartTime(result.getStartTime());
            dto.setEndTime(result.getEndTime());
            dto.setDurationMillis(result.getDurationMillis());
            
            if (result.getMetrics() != null && metricsService != null) {
                dto.setMetrics(metricsService.convertToResponse(result.getScenarioId(), result.getMetrics()));
            }
            
            dto.setTaskMixDistribution(result.getTaskMixDistribution());
            dto.setErrorMessage(result.getErrorMessage());
            
            return dto;
        }
        
        // Getters and setters
        public String getScenarioId() {
            return scenarioId;
        }
        
        public void setScenarioId(String scenarioId) {
            this.scenarioId = scenarioId;
        }
        
        public String getScenarioName() {
            return scenarioName;
        }
        
        public void setScenarioName(String scenarioName) {
            this.scenarioName = scenarioName;
        }
        
        public String getStatus() {
            return status;
        }
        
        public void setStatus(String status) {
            this.status = status;
        }
        
        public Instant getStartTime() {
            return startTime;
        }
        
        public void setStartTime(Instant startTime) {
            this.startTime = startTime;
        }
        
        public Instant getEndTime() {
            return endTime;
        }
        
        public void setEndTime(Instant endTime) {
            this.endTime = endTime;
        }
        
        public long getDurationMillis() {
            return durationMillis;
        }
        
        public void setDurationMillis(long durationMillis) {
            this.durationMillis = durationMillis;
        }
        
        public MetricsResponse getMetrics() {
            return metrics;
        }
        
        public void setMetrics(MetricsResponse metrics) {
            this.metrics = metrics;
        }
        
        public Map<String, Long> getTaskMixDistribution() {
            return taskMixDistribution;
        }
        
        public void setTaskMixDistribution(Map<String, Long> taskMixDistribution) {
            this.taskMixDistribution = taskMixDistribution;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }
}
