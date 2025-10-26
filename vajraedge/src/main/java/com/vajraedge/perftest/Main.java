package com.vajraedge.perftest;

import com.vajraedge.perftest.core.Task;
import com.vajraedge.perftest.core.TaskFactory;
import com.vajraedge.perftest.core.SimpleTaskResult;
import com.vajraedge.perftest.runner.PerformanceTestRunner;
import com.vajraedge.perftest.runner.TestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Example main class demonstrating the generic performance test framework.
 * 
 * This framework can be used to test ANY workload:
 * - gRPC calls
 * - HTTP requests  
 * - Database queries
 * - Message queue operations
 * - Custom business logic
 * 
 * Simply implement the Task interface with your workload.
 */
public class Main {
    
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    public static void main(String[] args) throws InterruptedException {
        logger.info("Starting Generic Performance Test Framework");
        
        // Example 1: Simple sleep task (simulates I/O-bound work)
        TaskFactory sleepTaskFactory = taskId -> createSleepTask(taskId, 10);
        
        // Example 2: CPU-bound task (simulates computation)
        TaskFactory cpuTaskFactory = Main::createCpuTask;
        
        // Configure and run test
        int maxConcurrency = 100;
        int targetTps = 50;
        Duration rampUp = Duration.ofSeconds(2);
        Duration testDuration = Duration.ofSeconds(10);
        
        logger.info("Test Configuration: TPS={}, Concurrency={}, Duration={}", 
                   targetTps, maxConcurrency, testDuration);
        
        PerformanceTestRunner runner = new PerformanceTestRunner(
                sleepTaskFactory,
                maxConcurrency,
                targetTps,
                rampUp
        );
        
        TestResult result = runner.run(testDuration);
        runner.close();
        
        // Print results
        logger.info("Test completed: {}", result);
        logger.info("Metrics: {}", result.getMetrics());
        logger.info("Percentiles: {}", result.getMetrics().getPercentiles());
        
        System.exit(0);
    }
    
    /**
     * Example task that simulates I/O with sleep.
     */
    private static Task createSleepTask(long taskId, int sleepMs) {
        return () -> {
            long start = System.nanoTime();
            try {
                Thread.sleep(sleepMs);
                long latency = System.nanoTime() - start;
                return SimpleTaskResult.success(taskId, latency);
            } catch (Exception e) {
                long latency = System.nanoTime() - start;
                return SimpleTaskResult.failure(taskId, latency, e.getMessage());
            }
        };
    }
    
    /**
     * Example task that simulates CPU-bound work.
     */
    private static Task createCpuTask(long taskId) {
        return () -> {
            long start = System.nanoTime();
            try {
                // Simulate some computation
                int sum = 0;
                for (int i = 0; i < 10000; i++) {
                    sum += i;
                }
                long latency = System.nanoTime() - start;
                return SimpleTaskResult.success(taskId, latency, sum);
            } catch (Exception e) {
                long latency = System.nanoTime() - start;
                return SimpleTaskResult.failure(taskId, latency, e.getMessage());
            }
        };
    }
}
