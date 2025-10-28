package com.vajraedge.perftest.runner;

import com.vajraedge.perftest.concurrency.ConcurrencyController;
import com.vajraedge.perftest.core.Task;
import com.vajraedge.perftest.core.TaskFactory;
import com.vajraedge.perftest.core.TaskResult;
import com.vajraedge.perftest.executor.VirtualThreadTaskExecutor;
import com.vajraedge.perftest.metrics.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Concurrency-based load test runner.
 * 
 * <p>This runner simulates concurrent virtual users rather than controlling TPS directly.
 * Each virtual user executes tasks in a loop until the test completes.</p>
 * 
 * <p>Key differences from rate-based runner:
 * <ul>
 *   <li>Concurrency is the independent variable (controlled)</li>
 *   <li>TPS is the dependent variable (measured)</li>
 *   <li>Virtual users ramp up/down based on RampStrategy</li>
 *   <li>More realistic simulation of actual user behavior</li>
 * </ul>
 * </p>
 */
public class ConcurrencyBasedTestRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(ConcurrencyBasedTestRunner.class);
    private static final long CONTROL_LOOP_INTERVAL_MS = 100; // Check concurrency every 100ms
    private static final long THROTTLE_BACKOFF_MS = 10; // Backoff when throttling
    
    private final TaskFactory taskFactory;
    private final ConcurrencyController concurrencyController;
    private final VirtualThreadTaskExecutor executor;
    private final MetricsCollector metricsCollector;
    private final AtomicLong taskIdGenerator;
    private final AtomicBoolean stopRequested;
    private final List<VirtualUser> activeUsers;
    
    /**
     * Create a concurrency-based test runner.
     * 
     * @param taskFactory factory for creating tasks
     * @param concurrencyController controls concurrency ramp-up
     */
    public ConcurrencyBasedTestRunner(TaskFactory taskFactory, ConcurrencyController concurrencyController) {
        this.taskFactory = taskFactory;
        this.concurrencyController = concurrencyController;
        this.executor = new VirtualThreadTaskExecutor(concurrencyController.getMaxConcurrency());
        this.metricsCollector = new MetricsCollector();
        this.taskIdGenerator = new AtomicLong(0);
        this.stopRequested = new AtomicBoolean(false);
        // Pre-allocate ArrayList to max capacity to avoid resizing during ramp-up
        this.activeUsers = new ArrayList<>(concurrencyController.getMaxConcurrency());
        
        logger.info("Concurrency-based test runner initialized with strategy: {}",
            concurrencyController.getRampStrategy().getDescription());
    }
    
    /**
     * Run the load test for the specified duration.
     * 
     * @param testDuration total test duration
     * @return test results
     */
    public TestResult run(Duration testDuration) {
        logger.info("Starting concurrency-based load test for duration: {}", testDuration);
        
        long startTime = System.currentTimeMillis();
        long endTime = startTime + testDuration.toMillis();
        
        try {
            // Main control loop
            while (!stopRequested.get() && System.currentTimeMillis() < endTime) {
                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                int targetConcurrency = concurrencyController.getTargetConcurrency(elapsed);
                
                // Adjust active virtual users to match target
                adjustConcurrency(targetConcurrency);
                
                // Check if we need to throttle (hybrid mode)
                if (concurrencyController.shouldThrottle(metricsCollector.getSnapshot().getTps())) {
                    logger.debug("Throttling: current TPS exceeds limit");
                    Thread.sleep(THROTTLE_BACKOFF_MS);
                }
                
                Thread.sleep(CONTROL_LOOP_INTERVAL_MS);
            }
        } catch (InterruptedException e) {
            logger.warn("Test runner interrupted", e);
            Thread.currentThread().interrupt();
        } finally {
            stopRequested.set(true);
            shutdownAllUsers();
        }
        
        long duration = System.currentTimeMillis() - startTime;
        logger.info("Load test completed. Duration: {}ms, Final concurrency: {}, Total requests: {}",
            duration, activeUsers.size(), metricsCollector.getSnapshot().getTotalTasks());
        
        return buildTestResult(duration);
    }
    
    /**
     * Stop the test early.
     */
    public void stop() {
        logger.info("Stop requested");
        stopRequested.set(true);
    }
    
    /**
     * Adjust the number of active virtual users to match target concurrency.
     * 
     * @param targetConcurrency desired number of virtual users
     */
    private void adjustConcurrency(int targetConcurrency) {
        synchronized (activeUsers) {
            int currentConcurrency = activeUsers.size();
            
            if (targetConcurrency > currentConcurrency) {
                // Ramp up: add users
                int toAdd = targetConcurrency - currentConcurrency;
                logger.debug("Ramping up: adding {} virtual users (current: {}, target: {})",
                    toAdd, currentConcurrency, targetConcurrency);
                
                for (int i = 0; i < toAdd; i++) {
                    VirtualUser user = new VirtualUser();
                    activeUsers.add(user);
                    user.start();
                }
            } else if (targetConcurrency < currentConcurrency) {
                // Ramp down: remove users
                int toRemove = currentConcurrency - targetConcurrency;
                logger.debug("Ramping down: removing {} virtual users (current: {}, target: {})",
                    toRemove, currentConcurrency, targetConcurrency);
                
                for (int i = 0; i < toRemove; i++) {
                    if (!activeUsers.isEmpty()) {
                        VirtualUser user = activeUsers.remove(activeUsers.size() - 1);
                        user.stop();
                    }
                }
            }
        }
    }
    
    /**
     * Shutdown all active virtual users.
     */
    private void shutdownAllUsers() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        synchronized (activeUsers) {
            logger.info("Shutting down {} virtual users", activeUsers.size());
            
            // Stop all users and collect their futures
            for (VirtualUser user : activeUsers) {
                user.stop();
                futures.add(user.future);
            }
            
            activeUsers.clear();
        }
        
        // Wait for all users to complete (with timeout)
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(5, java.util.concurrent.TimeUnit.SECONDS);
            logger.debug("All virtual users stopped gracefully");
        } catch (Exception e) {
            logger.warn("Timeout waiting for virtual users to stop, proceeding with shutdown", e);
        }
        
        executor.close();
    }
    
    /**
     * Build the test result.
     * 
     * @param durationMs test duration in milliseconds
     * @return test result
     */
    private TestResult buildTestResult(long durationMs) {
        return new TestResult(
            metricsCollector.getSnapshot(),
            Duration.ofMillis(durationMs)
        );
    }
    
    /**
     * Close the runner and release resources.
     */
    public void close() {
        stop();
        executor.close();
    }
    
    public MetricsCollector getMetricsCollector() {
        return metricsCollector;
    }
    
    public VirtualThreadTaskExecutor getExecutor() {
        return executor;
    }
    
    /**
     * Get the number of active virtual users.
     * This represents the current concurrency level.
     * 
     * @return number of active virtual users
     */
    public int getActiveVirtualUsers() {
        synchronized (activeUsers) {
            return activeUsers.size();
        }
    }
    
    /**
     * Represents a single virtual user that executes tasks in a loop.
     */
    private class VirtualUser {
        private final CompletableFuture<Void> future;
        private final AtomicBoolean running;
        
        VirtualUser() {
            this.running = new AtomicBoolean(true);
            this.future = CompletableFuture.runAsync(this::run);
        }
        
        void run() {
            logger.trace("Virtual user started");
            
            try {
                while (running.get() && !stopRequested.get() && !Thread.currentThread().isInterrupted()) {
                    try {
                        // Create and execute task
                        long taskId = taskIdGenerator.incrementAndGet();
                        Task task = taskFactory.createTask(taskId);
                        TaskResult result = task.execute();
                        
                        // Record metrics
                        metricsCollector.recordResult(result);
                        
                    } catch (InterruptedException e) {
                        logger.debug("Virtual user interrupted during task execution", e);
                        Thread.currentThread().interrupt(); // Restore interrupted status
                        break; // Exit loop on interruption
                    } catch (Exception e) {
                        logger.error("Error executing task in virtual user", e);
                        // Continue running despite individual task failures
                    }
                }
            } finally {
                logger.trace("Virtual user stopped");
            }
        }
        
        void start() {
            // Already started in constructor
        }
        
        void stop() {
            running.set(false);
            future.cancel(true);
        }
    }
}
