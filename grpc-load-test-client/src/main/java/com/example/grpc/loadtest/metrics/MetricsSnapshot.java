package com.example.grpc.loadtest.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Immutable snapshot of metrics at a point in time
 */
public class MetricsSnapshot {
    
    private final Instant startTime;
    private final Duration elapsed;
    private final long totalRequests;
    private final long successfulRequests;
    private final long failedRequests;
    private final double tps;
    private final double avgLatencyMs;
    private final double successRate;
    private final long avgResponseSize;
    private final MetricsCollector.PercentileStats percentiles;
    private final Map<Integer, Long> responseCodeCounts;
    private final Map<String, Long> errorCounts;
    
    public MetricsSnapshot(Instant startTime, Duration elapsed, long totalRequests, 
                          long successfulRequests, long failedRequests, double tps, 
                          double avgLatencyMs, double successRate, long avgResponseSize,
                          MetricsCollector.PercentileStats percentiles, 
                          Map<Integer, Long> responseCodeCounts, 
                          Map<String, Long> errorCounts) {
        this.startTime = startTime;
        this.elapsed = elapsed;
        this.totalRequests = totalRequests;
        this.successfulRequests = successfulRequests;
        this.failedRequests = failedRequests;
        this.tps = tps;
        this.avgLatencyMs = avgLatencyMs;
        this.successRate = successRate;
        this.avgResponseSize = avgResponseSize;
        this.percentiles = percentiles;
        this.responseCodeCounts = responseCodeCounts;
        this.errorCounts = errorCounts;
    }
    
    // Getters
    public Instant getStartTime() { return startTime; }
    public Duration getElapsed() { return elapsed; }
    public long getTotalRequests() { return totalRequests; }
    public long getSuccessfulRequests() { return successfulRequests; }
    public long getFailedRequests() { return failedRequests; }
    public double getTps() { return tps; }
    public double getAvgLatencyMs() { return avgLatencyMs; }
    public double getSuccessRate() { return successRate; }
    public long getAvgResponseSize() { return avgResponseSize; }
    public MetricsCollector.PercentileStats getPercentiles() { return percentiles; }
    public Map<Integer, Long> getResponseCodeCounts() { return responseCodeCounts; }
    public Map<String, Long> getErrorCounts() { return errorCounts; }
    
    @Override
    public String toString() {
        return String.format(
            "MetricsSnapshot{total=%d, success=%d (%.1f%%), tps=%.1f, avgLatency=%.2fms, p95=%.2fms, p99=%.2fms}",
            totalRequests, successfulRequests, successRate, tps, avgLatencyMs,
            percentiles.getPercentile(0.95), percentiles.getPercentile(0.99)
        );
    }
}