package net.vajraedge.perftest.dto;

import java.util.Map;

/**
 * DTO for metrics responses.
 */
public class MetricsResponse {
    
    private String testId;
    private Long timestamp;
    private Long totalRequests;
    private Long successfulRequests;
    private Long failedRequests;
    private Double successRate;
    private Integer activeTasks;
    private Integer pendingTasks; // Tasks submitted but not yet started (queued/waiting)
    private Double currentTps;
    private Map<String, Double> latencyPercentiles; // p50, p75, p90, p95, p99, p99.9
    private Double avgLatencyMs;
    private Double minLatencyMs;
    private Double maxLatencyMs;
    
    // Getters and Setters
    
    public String getTestId() {
        return testId;
    }
    
    public void setTestId(String testId) {
        this.testId = testId;
    }
    
    public Long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
    
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
    
    public Double getSuccessRate() {
        return successRate;
    }
    
    public void setSuccessRate(Double successRate) {
        this.successRate = successRate;
    }
    
    public Integer getActiveTasks() {
        return activeTasks;
    }
    
    public void setActiveTasks(Integer activeTasks) {
        this.activeTasks = activeTasks;
    }
    
    public Integer getPendingTasks() {
        return pendingTasks;
    }
    
    public void setPendingTasks(Integer pendingTasks) {
        this.pendingTasks = pendingTasks;
    }
    
    public Double getCurrentTps() {
        return currentTps;
    }
    
    public void setCurrentTps(Double currentTps) {
        this.currentTps = currentTps;
    }
    
    public Map<String, Double> getLatencyPercentiles() {
        return latencyPercentiles;
    }
    
    public void setLatencyPercentiles(Map<String, Double> latencyPercentiles) {
        this.latencyPercentiles = latencyPercentiles;
    }
    
    public Double getAvgLatencyMs() {
        return avgLatencyMs;
    }
    
    public void setAvgLatencyMs(Double avgLatencyMs) {
        this.avgLatencyMs = avgLatencyMs;
    }
    
    public Double getMinLatencyMs() {
        return minLatencyMs;
    }
    
    public void setMinLatencyMs(Double minLatencyMs) {
        this.minLatencyMs = minLatencyMs;
    }
    
    public Double getMaxLatencyMs() {
        return maxLatencyMs;
    }
    
    public void setMaxLatencyMs(Double maxLatencyMs) {
        this.maxLatencyMs = maxLatencyMs;
    }
}
