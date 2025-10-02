package com.example.grpc.loadtest.metrics;

import com.example.grpc.loadtest.client.GrpcLoadTestClient.CallResult;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe metrics collector for gRPC load testing
 * Tracks latencies, response codes, and provides percentile calculations
 */
public class MetricsCollector implements AutoCloseable {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsCollector.class);
    
    // Constants for configuration defaults
    private static final int DEFAULT_MAX_LATENCY_HISTORY_SIZE = 10000;
    private static final long DEFAULT_WINDOW_SIZE_MS = 1000;
    private static final long CLEANUP_INTERVAL_MS = 60000; // 60 seconds
    private static final long WINDOW_RETENTION_MS = 10 * 60 * 1000; // 10 minutes
    
    private final Instant startTime;
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    private final LongAdder totalLatencyNanos = new LongAdder();
    private final LongAdder totalResponseSize = new LongAdder();
    
    // Response code tracking
    private final ConcurrentHashMap<Integer, AtomicLong> responseCodeCounts = new ConcurrentHashMap<>();
    
    // Latency tracking - using a bounded queue to prevent memory issues
    private final ConcurrentLinkedQueue<Double> latencyHistory = new ConcurrentLinkedQueue<>();
    private final int maxLatencyHistorySize;
    private final AtomicLong latencyHistoryCount = new AtomicLong(0);
    
    // Time-based metrics for real-time statistics
    private final ConcurrentHashMap<Long, WindowMetrics> timeWindows = new ConcurrentHashMap<>();
    private final long windowSizeMs;
    private final ReadWriteLock windowLock = new ReentrantReadWriteLock();
    
    // Error tracking
    private final ConcurrentHashMap<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
    
    public MetricsCollector() {
        this(DEFAULT_MAX_LATENCY_HISTORY_SIZE, DEFAULT_WINDOW_SIZE_MS); // Default: 10k latency history, 1s time windows
    }
    
    public MetricsCollector(int maxLatencyHistorySize, long windowSizeMs) {
        this.startTime = Instant.now();
        this.maxLatencyHistorySize = maxLatencyHistorySize;
        this.windowSizeMs = windowSizeMs;
        
        logger.info("Created MetricsCollector with maxLatencyHistory={}, windowSize={}ms", 
                   maxLatencyHistorySize, windowSizeMs);
    }
    
    /**
     * Record a call result
     */
    public void recordResult(CallResult result) {
        totalRequests.incrementAndGet();
        
        double latencyMs = result.getLatencyMs();
        totalLatencyNanos.add(result.getLatencyNanos());
        
        if (result.isSuccess()) {
            successfulRequests.incrementAndGet();
            totalResponseSize.add(result.getResponseSize());
            recordResponseCode(0); // Success code
        } else {
            failedRequests.incrementAndGet();
            recordResponseCode(result.getStatusCode());
            recordError(result.getErrorMessage());
        }
        
        // Record latency (with bounded history)
        recordLatency(latencyMs);
        
        // Record in time window
        recordInTimeWindow(result);
    }
    
    private void recordResponseCode(int code) {
        responseCodeCounts.computeIfAbsent(code, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    private void recordError(String errorMessage) {
        if (errorMessage != null && !errorMessage.isEmpty()) {
            // Truncate very long error messages
            String truncatedError = errorMessage.length() > 100 ? 
                    errorMessage.substring(0, 100) + "..." : errorMessage;
            errorCounts.computeIfAbsent(truncatedError, k -> new AtomicLong(0)).incrementAndGet();
        }
    }
    
    private void recordLatency(double latencyMs) {
        // Maintain bounded latency history
        latencyHistory.offer(latencyMs);
        latencyHistoryCount.incrementAndGet();
        
        // Remove old entries if we exceed the limit
        while (latencyHistory.size() > maxLatencyHistorySize) {
            latencyHistory.poll();
        }
    }
    
    private void recordInTimeWindow(CallResult result) {
        long currentTimeMs = System.currentTimeMillis();
        long windowStart = (currentTimeMs / windowSizeMs) * windowSizeMs;
        
        windowLock.readLock().lock();
        try {
            WindowMetrics window = timeWindows.computeIfAbsent(windowStart, k -> new WindowMetrics());
            window.record(result);
        } finally {
            windowLock.readLock().unlock();
        }
        
        // Clean up old windows periodically (keep only last 10 minutes worth)
        // Only cleanup every 60 seconds to avoid excessive cleanup overhead
        if (currentTimeMs % CLEANUP_INTERVAL_MS < windowSizeMs) {
            cleanupOldWindows(currentTimeMs);
        }
    }
    
    private void cleanupOldWindows(long currentTimeMs) {
        long cutoffTime = currentTimeMs - WINDOW_RETENTION_MS; // 10 minutes ago
        
        windowLock.writeLock().lock();
        try {
            timeWindows.entrySet().removeIf(entry -> entry.getKey() < cutoffTime);
        } finally {
            windowLock.writeLock().unlock();
        }
    }
    
    /**
     * Calculate percentiles for all recorded latencies
     */
    public PercentileStats calculatePercentiles(double[] percentiles) {
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
    
    /**
     * Get current metrics snapshot
     */
    public MetricsSnapshot getSnapshot() {
        long total = totalRequests.get();
        long successful = successfulRequests.get();
        long failed = failedRequests.get();
        
        Duration elapsed = Duration.between(startTime, Instant.now());
        double tps = elapsed.toMillis() > 0 ? (total * 1000.0) / elapsed.toMillis() : 0.0;
        
        double avgLatencyMs = total > 0 ? (totalLatencyNanos.doubleValue() / 1_000_000.0) / total : 0.0;
        double successRate = total > 0 ? (successful * 100.0) / total : 0.0;
        long avgResponseSize = successful > 0 ? totalResponseSize.longValue() / successful : 0;
        
        // Calculate standard percentiles
        double[] standardPercentiles = {0.1, 0.25, 0.5, 0.75, 0.9, 0.95, 0.99};
        PercentileStats percentileStats = calculatePercentiles(standardPercentiles);
        
        // Copy response code counts
        Map<Integer, Long> responseCodeSnapshot = new HashMap<>();
        responseCodeCounts.forEach((code, count) -> responseCodeSnapshot.put(code, count.get()));
        
        // Copy error counts (top 10 most frequent)
        Map<String, Long> errorSnapshot = errorCounts.entrySet().stream()
                .sorted(Map.Entry.<String, AtomicLong>comparingByValue((a, b) -> Long.compare(b.get(), a.get())))
                .limit(10)
                .collect(HashMap::new, 
                        (map, entry) -> map.put(entry.getKey(), entry.getValue().get()),
                        HashMap::putAll);
        
        return new MetricsSnapshot(
                startTime,
                elapsed,
                total,
                successful,
                failed,
                tps,
                avgLatencyMs,
                successRate,
                avgResponseSize,
                percentileStats,
                responseCodeSnapshot,
                errorSnapshot
        );
    }
    
    /**
     * Get recent metrics (last N seconds)
     */
    public MetricsSnapshot getRecentSnapshot(int lastSeconds) {
        long cutoffTime = System.currentTimeMillis() - (lastSeconds * 1000L);
        
        windowLock.readLock().lock();
        try {
            WindowMetrics aggregated = new WindowMetrics();
            
            timeWindows.entrySet().stream()
                    .filter(entry -> entry.getKey() >= cutoffTime)
                    .forEach(entry -> aggregated.merge(entry.getValue()));
            
            if (aggregated.getTotalRequests() == 0) {
                return getSnapshot(); // Fallback to overall snapshot
            }
            
            return aggregated.toSnapshot(startTime, Duration.ofSeconds(lastSeconds));
            
        } finally {
            windowLock.readLock().unlock();
        }
    }
    
    /**
     * Reset all metrics
     */
    public void reset() {
        totalRequests.set(0);
        successfulRequests.set(0);
        failedRequests.set(0);
        totalLatencyNanos.reset();
        totalResponseSize.reset();
        
        responseCodeCounts.clear();
        latencyHistory.clear();
        latencyHistoryCount.set(0);
        timeWindows.clear();
        errorCounts.clear();
        
        logger.info("MetricsCollector reset");
    }
    
    /**
     * Get current statistics summary
     */
    public String getSummary() {
        MetricsSnapshot snapshot = getSnapshot();
        return String.format(
                "Requests: %d (%.1f%% success), TPS: %.1f, Avg Latency: %.2fms, P95: %.2fms, P99: %.2fms",
                snapshot.getTotalRequests(),
                snapshot.getSuccessRate(),
                snapshot.getTps(),
                snapshot.getAvgLatencyMs(),
                snapshot.getPercentiles().getPercentile(0.95),
                snapshot.getPercentiles().getPercentile(0.99)
        );
    }
    
    @Override
    public void close() {
        // Clean up resources
        latencyHistory.clear();
        timeWindows.clear();
        responseCodeCounts.clear();
        errorCounts.clear();
        logger.info("MetricsCollector closed");
    }
    
    /**
     * Percentile statistics
     */
    public static class PercentileStats {
        private final double[] percentiles;
        private final double[] values;
        private final Map<Double, Double> percentileMap;
        
        public PercentileStats(double[] percentiles, double[] values) {
            this.percentiles = percentiles.clone();
            this.values = values.clone();
            this.percentileMap = new HashMap<>();
            
            for (int i = 0; i < percentiles.length; i++) {
                percentileMap.put(percentiles[i], values[i]);
            }
        }
        
        public double getPercentile(double percentile) {
            return percentileMap.getOrDefault(percentile, 0.0);
        }
        
        public double[] getPercentiles() { return percentiles.clone(); }
        public double[] getValues() { return values.clone(); }
        public Map<Double, Double> getPercentileMap() { return new HashMap<>(percentileMap); }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("Percentiles{");
            for (int i = 0; i < percentiles.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(String.format("P%.0f=%.2fms", percentiles[i] * 100, values[i]));
            }
            sb.append("}");
            return sb.toString();
        }
    }
    
    /**
     * Time window metrics for real-time statistics
     */
    private static class WindowMetrics {
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final AtomicLong successfulRequests = new AtomicLong(0);
        private final AtomicLong failedRequests = new AtomicLong(0);
        private final LongAdder totalLatencyNanos = new LongAdder();
        private final LongAdder totalResponseSize = new LongAdder();
        private final ConcurrentHashMap<Integer, AtomicLong> responseCodeCounts = new ConcurrentHashMap<>();
        private final ConcurrentLinkedQueue<Double> latencies = new ConcurrentLinkedQueue<>();
        
        public void record(CallResult result) {
            totalRequests.incrementAndGet();
            totalLatencyNanos.add(result.getLatencyNanos());
            latencies.offer(result.getLatencyMs());
            
            if (result.isSuccess()) {
                successfulRequests.incrementAndGet();
                totalResponseSize.add(result.getResponseSize());
                responseCodeCounts.computeIfAbsent(0, k -> new AtomicLong(0)).incrementAndGet();
            } else {
                failedRequests.incrementAndGet();
                responseCodeCounts.computeIfAbsent(result.getStatusCode(), k -> new AtomicLong(0)).incrementAndGet();
            }
        }
        
        public void merge(WindowMetrics other) {
            totalRequests.addAndGet(other.totalRequests.get());
            successfulRequests.addAndGet(other.successfulRequests.get());
            failedRequests.addAndGet(other.failedRequests.get());
            totalLatencyNanos.add(other.totalLatencyNanos.longValue());
            totalResponseSize.add(other.totalResponseSize.longValue());
            
            other.responseCodeCounts.forEach((code, count) -> 
                    responseCodeCounts.computeIfAbsent(code, k -> new AtomicLong(0)).addAndGet(count.get()));
            
            latencies.addAll(other.latencies);
        }
        
        public long getTotalRequests() {
            return totalRequests.get();
        }
        
        public MetricsSnapshot toSnapshot(Instant startTime, Duration elapsed) {
            long total = totalRequests.get();
            long successful = successfulRequests.get();
            
            double tps = elapsed.toMillis() > 0 ? (total * 1000.0) / elapsed.toMillis() : 0.0;
            double avgLatencyMs = total > 0 ? (totalLatencyNanos.doubleValue() / 1_000_000.0) / total : 0.0;
            double successRate = total > 0 ? (successful * 100.0) / total : 0.0;
            long avgResponseSize = successful > 0 ? totalResponseSize.longValue() / successful : 0;
            
            // Calculate percentiles
            List<Double> latencyList = new ArrayList<>(latencies);
            double[] standardPercentiles = {0.1, 0.25, 0.5, 0.75, 0.9, 0.95, 0.99};
            PercentileStats percentileStats;
            
            if (latencyList.isEmpty()) {
                percentileStats = new PercentileStats(standardPercentiles, new double[standardPercentiles.length]);
            } else {
                double[] values = latencyList.stream().mapToDouble(Double::doubleValue).toArray();
                Percentile percentileCalculator = new Percentile();
                percentileCalculator.setData(values);
                
                double[] results = new double[standardPercentiles.length];
                for (int i = 0; i < standardPercentiles.length; i++) {
                    results[i] = percentileCalculator.evaluate(standardPercentiles[i] * 100);
                }
                percentileStats = new PercentileStats(standardPercentiles, results);
            }
            
            Map<Integer, Long> responseCodeSnapshot = new HashMap<>();
            responseCodeCounts.forEach((code, count) -> responseCodeSnapshot.put(code, count.get()));
            
            return new MetricsSnapshot(
                    startTime,
                    elapsed,
                    total,
                    successful,
                    total - successful,
                    tps,
                    avgLatencyMs,
                    successRate,
                    avgResponseSize,
                    percentileStats,
                    responseCodeSnapshot,
                    new HashMap<>() // No error details in window snapshots
            );
        }
    }
}