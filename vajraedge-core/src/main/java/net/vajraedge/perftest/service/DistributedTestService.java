package net.vajraedge.perftest.service;

import net.vajraedge.perftest.distributed.DistributedMetricsCollector;
import net.vajraedge.perftest.distributed.TaskDistributor;
import net.vajraedge.perftest.distributed.TaskDistributor.DistributionResult;
import net.vajraedge.perftest.distributed.WorkerManager;
import net.vajraedge.perftest.dto.DistributedTestRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing distributed performance tests across multiple workers.
 */
@Service
public class DistributedTestService {
    private static final Logger log = LoggerFactory.getLogger(DistributedTestService.class);
    
    private final TaskDistributor taskDistributor;
    private final WorkerManager workerManager;
    private final DistributedMetricsCollector metricsCollector;
    
    // Track active distributed tests
    private final Map<String, DistributedTestInfo> activeTests = new ConcurrentHashMap<>();
    
    public DistributedTestService(TaskDistributor taskDistributor,
                                   WorkerManager workerManager,
                                   DistributedMetricsCollector metricsCollector) {
        this.taskDistributor = taskDistributor;
        this.workerManager = workerManager;
        this.metricsCollector = metricsCollector;
    }
    
    /**
     * Start a new distributed test.
     *
     * @param request Distributed test configuration
     * @return Test ID and distribution result
     */
    public DistributedTestResponse startDistributedTest(DistributedTestRequest request) {
        // Generate unique test ID
        String testId = "test-" + UUID.randomUUID().toString().substring(0, 8);
        
        log.info("Starting distributed test: testId={}, taskType={}, targetTps={}, duration={}s",
                testId, request.getTaskType(), request.getTargetTps(), request.getDurationSeconds());
        
        // Check if enough workers are available
        int availableWorkers = workerManager.getWorkersForTaskType(request.getTaskType()).size();
        if (request.getMinWorkers() != null && availableWorkers < request.getMinWorkers()) {
            String errorMsg = String.format("Insufficient workers: required %d, available %d",
                    request.getMinWorkers(), availableWorkers);
            log.error(errorMsg);
            return new DistributedTestResponse(null, false, errorMsg, null);
        }
        
        // Distribute test to workers
        DistributionResult distribution = taskDistributor.distributeTest(testId, request);
        
        if (!distribution.success()) {
            log.error("Failed to distribute test: {}", distribution.message());
            return new DistributedTestResponse(null, false, distribution.message(), null);
        }
        
        // Track test
        DistributedTestInfo testInfo = new DistributedTestInfo(
                testId,
                request.getTaskType(),
                request.getTargetTps(),
                request.getDurationSeconds(),
                distribution.getAcceptedWorkerCount(),
                distribution.getTotalAssignedTps(),
                System.currentTimeMillis()
        );
        
        activeTests.put(testId, testInfo);
        
        log.info("Distributed test started: testId={}, workers={}, assignedTps={}",
                testId, distribution.getAcceptedWorkerCount(), distribution.getTotalAssignedTps());
        
        return new DistributedTestResponse(
                testId,
                true,
                "Test distributed successfully to " + distribution.getAcceptedWorkerCount() + " workers",
                testInfo
        );
    }
    
    /**
     * Stop a distributed test.
     *
     * @param testId Test identifier
     * @param graceful Whether to stop gracefully
     * @return Success status
     */
    public boolean stopDistributedTest(String testId, boolean graceful) {
        DistributedTestInfo testInfo = activeTests.get(testId);
        
        if (testInfo == null) {
            log.warn("Cannot stop test {}: not found", testId);
            return false;
        }
        
        log.info("Stopping distributed test: testId={}, graceful={}", testId, graceful);
        
        // Stop test on all workers
        taskDistributor.stopDistributedTest(testId, graceful);
        
        // Remove from active tests
        activeTests.remove(testId);
        
        return true;
    }
    
    /**
     * Get test status.
     *
     * @param testId Test identifier
     * @return Test information or null if not found
     */
    public DistributedTestInfo getTestInfo(String testId) {
        return activeTests.get(testId);
    }
    
    /**
     * Get aggregated metrics for a distributed test.
     *
     * @param testId Test identifier
     * @return Aggregated metrics
     */
    public DistributedMetricsCollector.AggregatedMetrics getTestMetrics(String testId) {
        return metricsCollector.getAggregatedMetrics(testId);
    }
    
    /**
     * Get all active distributed tests.
     *
     * @return Map of test ID to test info
     */
    public Map<String, DistributedTestInfo> getActiveTests() {
        return Map.copyOf(activeTests);
    }
    
    /**
     * Information about a distributed test.
     */
    public record DistributedTestInfo(
            String testId,
            String taskType,
            int targetTps,
            int durationSeconds,
            int workerCount,
            int actualTps,
            long startedAt
    ) {}
    
    /**
     * Response from starting a distributed test.
     */
    public record DistributedTestResponse(
            String testId,
            boolean success,
            String message,
            DistributedTestInfo testInfo
    ) {}
}
