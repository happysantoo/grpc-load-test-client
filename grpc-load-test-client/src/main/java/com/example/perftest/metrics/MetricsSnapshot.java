package com.example.perftest.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Immutable snapshot of performance metrics at a point in time.
 */
public class MetricsSnapshot {
    
    private final Instant startTime;
    private final Duration elapsed;
    private final long totalTasks;
    private final long successfulTasks;
    private final long failedTasks;
    private final double tps;
    private final double avgLatencyMs;
    private final double successRate;
    private final PercentileStats percentiles;
    private final Map<String, Long> errorCounts;
    
    public MetricsSnapshot(Instant startTime, Duration elapsed, long totalTasks,
                          long successfulTasks, long failedTasks, double tps,
                          double avgLatencyMs, double successRate,
                          PercentileStats percentiles, Map<String, Long> errorCounts) {
        this.startTime = startTime;
        this.elapsed = elapsed;
        this.totalTasks = totalTasks;
        this.successfulTasks = successfulTasks;
        this.failedTasks = failedTasks;
        this.tps = tps;
        this.avgLatencyMs = avgLatencyMs;
        this.successRate = successRate;
        this.percentiles = percentiles;
        this.errorCounts = errorCounts;
    }
    
    // Getters
    public Instant getStartTime() { return startTime; }
    public Duration getElapsed() { return elapsed; }
    public long getTotalTasks() { return totalTasks; }
    public long getSuccessfulTasks() { return successfulTasks; }
    public long getFailedTasks() { return failedTasks; }
    public double getTps() { return tps; }
    public double getAvgLatencyMs() { return avgLatencyMs; }
    public double getSuccessRate() { return successRate; }
    public PercentileStats getPercentiles() { return percentiles; }
    public Map<String, Long> getErrorCounts() { return errorCounts; }
    
    @Override
    public String toString() {
        return String.format(
                "Tasks: %d (%.1f%% success), TPS: %.1f, Avg Latency: %.2fms, P95: %.2fms, P99: %.2fms",
                totalTasks, successRate, tps, avgLatencyMs,
                percentiles.getPercentile(0.95), percentiles.getPercentile(0.99)
        );
    }
}
