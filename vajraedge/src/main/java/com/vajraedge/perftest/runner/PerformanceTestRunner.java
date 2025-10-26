package com.vajraedge.perftest.runner;

import com.vajraedge.perftest.core.TaskFactory;
import com.vajraedge.perftest.core.TaskResult;
import com.vajraedge.perftest.executor.VirtualThreadTaskExecutor;
import com.vajraedge.perftest.metrics.MetricsCollector;
import com.vajraedge.perftest.rate.RateController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Orchestrates the execution of a performance test.
 * 
 * Single Responsibility: Test orchestration and lifecycle management
 */
public class PerformanceTestRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(PerformanceTestRunner.class);
    
    private final TaskFactory taskFactory;
    private final VirtualThreadTaskExecutor executor;
    private final MetricsCollector metricsCollector;
    private final RateController rateController;
    
    public PerformanceTestRunner(TaskFactory taskFactory,
                                 int maxConcurrency,
                                 int targetTps,
                                 Duration rampUpDuration) {
        this.taskFactory = taskFactory;
        this.executor = new VirtualThreadTaskExecutor(maxConcurrency);
        this.metricsCollector = new MetricsCollector();
        this.rateController = new RateController(targetTps, rampUpDuration);
    }
    
    public TestResult run(Duration testDuration) throws InterruptedException {
        logger.info("Starting performance test for {}", testDuration);
        
        AtomicBoolean testRunning = new AtomicBoolean(true);
        AtomicLong taskCounter = new AtomicLong(0);
        long startTime = System.currentTimeMillis();
        long endTime = startTime + testDuration.toMillis();
        
        while (System.currentTimeMillis() < endTime && testRunning.get()) {
            if (!rateController.acquirePermit()) {
                break;
            }
            
            long taskId = taskCounter.incrementAndGet();
            CompletableFuture<TaskResult> future = executor.trySubmit(taskFactory.createTask(taskId));
            
            if (future == null) {
                // Executor saturated, wait a bit
                Thread.sleep(1);
                continue;
            }
            
            future.thenAccept(result -> {
                metricsCollector.recordResult(result);
            }).exceptionally(throwable -> {
                logger.debug("Task {} failed with exception", taskId, throwable);
                return null;
            });
        }
        
        logger.info("Test duration completed, waiting for pending tasks...");
        awaitCompletion(30, TimeUnit.SECONDS);
        
        long actualDuration = System.currentTimeMillis() - startTime;
        logger.info("Performance test completed in {}ms", actualDuration);
        
        return new TestResult(metricsCollector.getSnapshot(), Duration.ofMillis(actualDuration));
    }
    
    private void awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        
        while (executor.getActiveTasks() > 0 && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        
        if (executor.getActiveTasks() > 0) {
            logger.warn("Timeout waiting for tasks to complete. Active tasks: {}", executor.getActiveTasks());
        }
    }
    
    // Getters for accessing components
    public VirtualThreadTaskExecutor getExecutor() {
        return executor;
    }
    
    public MetricsCollector getMetricsCollector() {
        return metricsCollector;
    }
    
    public void close() {
        executor.close();
        metricsCollector.close();
    }
}
