package net.vajraedge.perftest.dto;

import java.time.LocalDateTime;

/**
 * DTO for test status responses.
 */
public class TestStatusResponse {
    
    private String testId;
    private String status; // RUNNING, COMPLETED, FAILED, STOPPED
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long elapsedSeconds;
    private TestConfigRequest configuration;
    private CurrentMetrics currentMetrics;
    
    public TestStatusResponse() {
    }
    
    public TestStatusResponse(String testId, String status) {
        this.testId = testId;
        this.status = status;
    }
    
    // Getters and Setters
    
    public String getTestId() {
        return testId;
    }
    
    public void setTestId(String testId) {
        this.testId = testId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public LocalDateTime getStartTime() {
        return startTime;
    }
    
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }
    
    public LocalDateTime getEndTime() {
        return endTime;
    }
    
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
    
    public Long getElapsedSeconds() {
        return elapsedSeconds;
    }
    
    public void setElapsedSeconds(Long elapsedSeconds) {
        this.elapsedSeconds = elapsedSeconds;
    }
    
    public TestConfigRequest getConfiguration() {
        return configuration;
    }
    
    public void setConfiguration(TestConfigRequest configuration) {
        this.configuration = configuration;
    }
    
    public CurrentMetrics getCurrentMetrics() {
        return currentMetrics;
    }
    
    public void setCurrentMetrics(CurrentMetrics currentMetrics) {
        this.currentMetrics = currentMetrics;
    }
    
    public static class CurrentMetrics {
        private Long totalRequests;
        private Long successfulRequests;
        private Long failedRequests;
        private Integer activeTasks;
        private Double currentTps;
        private Double avgLatencyMs;
        
        // Getters and Setters
        
        public Long getTotalRequests() {
            return totalRequests;
        }
        
        public void setTotalRequests(Long totalRequests) {
            this.totalRequests = totalRequests;
        }
        
        public Long getSuccessfulRequests() {
            return successfulRequests;
        }
        
        public void setSuccessfulRequests(Long successfulRequests) {
            this.successfulRequests = successfulRequests;
        }
        
        public Long getFailedRequests() {
            return failedRequests;
        }
        
        public void setFailedRequests(Long failedRequests) {
            this.failedRequests = failedRequests;
        }
        
        public Integer getActiveTasks() {
            return activeTasks;
        }
        
        public void setActiveTasks(Integer activeTasks) {
            this.activeTasks = activeTasks;
        }
        
        public void setActiveTasks(Long activeTasks) {
            this.activeTasks = activeTasks != null ? activeTasks.intValue() : null;
        }
        
        public Double getCurrentTps() {
            return currentTps;
        }
        
        public void setCurrentTps(Double currentTps) {
            this.currentTps = currentTps;
        }
        
        public Double getAvgLatencyMs() {
            return avgLatencyMs;
        }
        
        public void setAvgLatencyMs(Double avgLatencyMs) {
            this.avgLatencyMs = avgLatencyMs;
        }
    }
}
