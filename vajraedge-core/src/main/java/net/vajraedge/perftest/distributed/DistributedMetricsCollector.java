package net.vajraedge.perftest.distributed;

import net.vajraedge.perftest.proto.WorkerMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Collects and aggregates metrics from multiple distributed workers.
 */
@Component
public class DistributedMetricsCollector {
    private static final Logger log = LoggerFactory.getLogger(DistributedMetricsCollector.class);
    
    // testId -> (workerId -> latest metrics)
    private final Map<String, Map<String, WorkerMetrics>> testMetrics = new ConcurrentHashMap<>();
    
    /**
     * Record metrics received from a worker.
     *
     * @param metrics Worker metrics
     */
    public void recordWorkerMetrics(WorkerMetrics metrics) {
        String testId = metrics.getTestId();
        String workerId = metrics.getWorkerId();
        
        testMetrics.computeIfAbsent(testId, k -> new ConcurrentHashMap<>())
                   .put(workerId, metrics);
        
        log.debug("Recorded metrics for test={}, worker={}, tps={}, total={}", 
                  testId, workerId, metrics.getCurrentTps(), metrics.getTotalRequests());
    }
    
    /**
     * Get aggregated metrics across all workers for a test.
     *
     * @param testId Test identifier
     * @return Aggregated metrics or null if no metrics available
     */
    public AggregatedMetrics getAggregatedMetrics(String testId) {
        Map<String, WorkerMetrics> workerMetrics = testMetrics.get(testId);
        if (workerMetrics == null || workerMetrics.isEmpty()) {
            return null;
        }
        
        Collection<WorkerMetrics> allMetrics = workerMetrics.values();
        
        // Aggregate counts
        long totalRequests = allMetrics.stream()
                .mapToLong(WorkerMetrics::getTotalRequests)
                .sum();
        
        long successfulRequests = allMetrics.stream()
                .mapToLong(WorkerMetrics::getSuccessfulRequests)
                .sum();
        
        long failedRequests = allMetrics.stream()
                .mapToLong(WorkerMetrics::getFailedRequests)
                .sum();
        
        // Aggregate TPS
        double totalTps = allMetrics.stream()
                .mapToDouble(WorkerMetrics::getCurrentTps)
                .sum();
        
        // Aggregate active tasks
        int totalActiveTasks = allMetrics.stream()
                .mapToInt(WorkerMetrics::getActiveTasks)
                .sum();
        
        // Aggregate latencies (weighted by request count)
        List<LatencyDataPoint> latencyDataPoints = allMetrics.stream()
                .filter(m -> m.hasLatency())
                .map(m -> new LatencyDataPoint(
                        m.getTotalRequests(),
                        m.getLatency().getP50Ms(),
                        m.getLatency().getP75Ms(),
                        m.getLatency().getP90Ms(),
                        m.getLatency().getP95Ms(),
                        m.getLatency().getP99Ms(),
                        m.getLatency().getP999Ms(),
                        m.getLatency().getAvgMs(),
                        m.getLatency().getMinMs(),
                        m.getLatency().getMaxMs()
                ))
                .collect(Collectors.toList());
        
        AggregatedLatency aggregatedLatency = aggregateLatencies(latencyDataPoints);
        
        // Calculate success rate
        double successRate = totalRequests > 0 
                ? (double) successfulRequests / totalRequests * 100.0 
                : 0.0;
        
        return new AggregatedMetrics(
                testId,
                Instant.now(),
                allMetrics.size(),
                totalRequests,
                successfulRequests,
                failedRequests,
                successRate,
                totalTps,
                totalActiveTasks,
                aggregatedLatency
        );
    }
    
    /**
     * Get metrics for a specific worker.
     *
     * @param testId Test identifier
     * @param workerId Worker identifier
     * @return Worker metrics or null if not found
     */
    public WorkerMetrics getWorkerMetrics(String testId, String workerId) {
        Map<String, WorkerMetrics> workerMetrics = testMetrics.get(testId);
        if (workerMetrics == null) {
            return null;
        }
        return workerMetrics.get(workerId);
    }
    
    /**
     * Get all worker metrics for a test.
     *
     * @param testId Test identifier
     * @return Map of workerId to metrics
     */
    public Map<String, WorkerMetrics> getAllWorkerMetrics(String testId) {
        Map<String, WorkerMetrics> workerMetrics = testMetrics.get(testId);
        if (workerMetrics == null) {
            return Collections.emptyMap();
        }
        return new HashMap<>(workerMetrics);
    }
    
    /**
     * Clear metrics for a test.
     *
     * @param testId Test identifier
     */
    public void clearTestMetrics(String testId) {
        testMetrics.remove(testId);
        log.info("Cleared metrics for test: {}", testId);
    }
    
    /**
     * Clear all metrics.
     */
    public void clearAllMetrics() {
        testMetrics.clear();
        log.info("Cleared all metrics");
    }
    
    /**
     * Aggregate latencies from multiple workers using weighted averaging.
     */
    private AggregatedLatency aggregateLatencies(List<LatencyDataPoint> dataPoints) {
        if (dataPoints.isEmpty()) {
            return new AggregatedLatency(0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        
        long totalWeight = dataPoints.stream().mapToLong(dp -> dp.requestCount).sum();
        
        if (totalWeight == 0) {
            return new AggregatedLatency(0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        
        // Weighted average for percentiles
        double p50 = weightedAverage(dataPoints, totalWeight, dp -> dp.p50);
        double p75 = weightedAverage(dataPoints, totalWeight, dp -> dp.p75);
        double p90 = weightedAverage(dataPoints, totalWeight, dp -> dp.p90);
        double p95 = weightedAverage(dataPoints, totalWeight, dp -> dp.p95);
        double p99 = weightedAverage(dataPoints, totalWeight, dp -> dp.p99);
        double p999 = weightedAverage(dataPoints, totalWeight, dp -> dp.p999);
        double avg = weightedAverage(dataPoints, totalWeight, dp -> dp.avg);
        
        // Min/max across all workers
        double min = dataPoints.stream().mapToDouble(dp -> dp.min).min().orElse(0);
        double max = dataPoints.stream().mapToDouble(dp -> dp.max).max().orElse(0);
        
        return new AggregatedLatency(p50, p75, p90, p95, p99, p999, avg, min, max);
    }
    
    private double weightedAverage(List<LatencyDataPoint> dataPoints, long totalWeight,
                                  java.util.function.ToDoubleFunction<LatencyDataPoint> extractor) {
        return dataPoints.stream()
                .mapToDouble(dp -> extractor.applyAsDouble(dp) * dp.requestCount)
                .sum() / totalWeight;
    }
    
    /**
     * Latency data point from a worker.
     */
    private record LatencyDataPoint(
            long requestCount,
            double p50,
            double p75,
            double p90,
            double p95,
            double p99,
            double p999,
            double avg,
            double min,
            double max
    ) {}
    
    /**
     * Aggregated metrics across all workers.
     */
    public record AggregatedMetrics(
            String testId,
            Instant timestamp,
            int workerCount,
            long totalRequests,
            long successfulRequests,
            long failedRequests,
            double successRate,
            double totalTps,
            int totalActiveTasks,
            AggregatedLatency latency
    ) {}
    
    /**
     * Aggregated latency statistics.
     */
    public record AggregatedLatency(
            double p50Ms,
            double p75Ms,
            double p90Ms,
            double p95Ms,
            double p99Ms,
            double p999Ms,
            double avgMs,
            double minMs,
            double maxMs
    ) {}
}
