package com.example.perftest.runner;

import com.example.perftest.metrics.MetricsSnapshot;

import java.time.Duration;

/**
 * Result of a performance test execution.
 */
public class TestResult {
    
    private final MetricsSnapshot metrics;
    private final Duration actualDuration;
    
    public TestResult(MetricsSnapshot metrics, Duration actualDuration) {
        this.metrics = metrics;
        this.actualDuration = actualDuration;
    }
    
    public MetricsSnapshot getMetrics() {
        return metrics;
    }
    
    public Duration getActualDuration() {
        return actualDuration;
    }
    
    @Override
    public String toString() {
        return String.format("TestResult{duration=%s, metrics=%s}", 
                actualDuration, metrics);
    }
}
