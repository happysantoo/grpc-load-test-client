package com.example.perftest.metrics;

import com.example.perftest.core.TaskResult;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Collects and aggregates performance metrics.
 * 
 * Single Responsibility: Metrics collection and aggregation
 */
public class MetricsCollector implements AutoCloseable {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsCollector.class);
    private static final int DEFAULT_MAX_LATENCY_HISTORY = 10000;
    
    private final Instant startTime;
    private final AtomicLong totalTasks = new AtomicLong(0);
    private final AtomicLong successfulTasks = new AtomicLong(0);
    private final AtomicLong failedTasks = new AtomicLong(0);
    private final LongAdder totalLatencyNanos = new LongAdder();
    private final ConcurrentLinkedQueue<Double> latencyHistory = new ConcurrentLinkedQueue<>();
    private final int maxLatencyHistory;
    private final ConcurrentHashMap<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
    
    public MetricsCollector() {
        this(DEFAULT_MAX_LATENCY_HISTORY);
    }
    
    public MetricsCollector(int maxLatencyHistory) {
        this.startTime = Instant.now();
        this.maxLatencyHistory = maxLatencyHistory;
        logger.info("Created MetricsCollector with maxLatencyHistory={}", maxLatencyHistory);
    }
    
    public void recordResult(TaskResult result) {
        totalTasks.incrementAndGet();
        totalLatencyNanos.add(result.getLatencyNanos());
        
        if (result.isSuccess()) {
            successfulTasks.incrementAndGet();
        } else {
            failedTasks.incrementAndGet();
            recordError(result.getErrorMessage());
        }
        
        recordLatency(result.getLatencyMs());
    }
    
    private void recordError(String errorMessage) {
        if (errorMessage != null && !errorMessage.isEmpty()) {
            String truncated = errorMessage.length() > 100 ? 
                    errorMessage.substring(0, 100) + "..." : errorMessage;
            errorCounts.computeIfAbsent(truncated, k -> new AtomicLong(0)).incrementAndGet();
        }
    }
    
    private void recordLatency(double latencyMs) {
        latencyHistory.offer(latencyMs);
        while (latencyHistory.size() > maxLatencyHistory) {
            latencyHistory.poll();
        }
    }
    
    public MetricsSnapshot getSnapshot() {
        long total = totalTasks.get();
        long successful = successfulTasks.get();
        long failed = failedTasks.get();
        
        Duration elapsed = Duration.between(startTime, Instant.now());
        double tps = elapsed.toMillis() > 0 ? (total * 1000.0) / elapsed.toMillis() : 0.0;
        
        double avgLatencyMs = total > 0 ? (totalLatencyNanos.doubleValue() / 1_000_000.0) / total : 0.0;
        double successRate = total > 0 ? (successful * 100.0) / total : 0.0;
        
        PercentileStats percentiles = calculatePercentiles(new double[]{0.5, 0.75, 0.9, 0.95, 0.99});
        
        Map<String, Long> errorSnapshot = new HashMap<>();
        errorCounts.forEach((error, count) -> errorSnapshot.put(error, count.get()));
        
        return new MetricsSnapshot(
                startTime,
                elapsed,
                total,
                successful,
                failed,
                tps,
                avgLatencyMs,
                successRate,
                percentiles,
                errorSnapshot
        );
    }
    
    private PercentileStats calculatePercentiles(double[] percentiles) {
        List<Double> latencies = new ArrayList<>(latencyHistory);
        
        if (latencies.isEmpty()) {
            return new PercentileStats(percentiles, new double[percentiles.length]);
        }
        
        double[] values = latencies.stream().mapToDouble(Double::doubleValue).toArray();
        Percentile percentileCalculator = new Percentile();
        percentileCalculator.setData(values);
        
        double[] results = new double[percentiles.length];
        for (int i = 0; i < percentiles.length; i++) {
            results[i] = percentileCalculator.evaluate(percentiles[i] * 100);
        }
        
        return new PercentileStats(percentiles, results);
    }
    
    public void reset() {
        totalTasks.set(0);
        successfulTasks.set(0);
        failedTasks.set(0);
        totalLatencyNanos.reset();
        latencyHistory.clear();
        errorCounts.clear();
        logger.info("MetricsCollector reset");
    }
    
    @Override
    public void close() {
        latencyHistory.clear();
        errorCounts.clear();
        logger.info("MetricsCollector closed");
    }
}
