package net.vajraedge.worker;

import net.vajraedge.sdk.SimpleTaskResult;
import net.vajraedge.sdk.Task;
import net.vajraedge.sdk.TaskResult;
import net.vajraedge.sdk.metrics.MetricsCollector;
import net.vajraedge.sdk.metrics.MetricsSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;

/**
 * Service for executing tasks using virtual threads.
 * 
 * <p>Uses Java 21 virtual threads for efficient concurrent task execution.
 * Supports tens of thousands of concurrent tasks with minimal resource overhead.
 *
 * @since 2.0.0
 */
public class TaskExecutorService {
    
    private static final Logger log = LoggerFactory.getLogger(TaskExecutorService.class);
    
    private final int maxConcurrency;
    private final ExecutorService executor;
    private final Semaphore concurrencyLimit;
    private final AtomicBoolean accepting;
    
    // Per-test metrics collectors to isolate metrics
    private final Map<String, MetricsCollector> testMetrics = new ConcurrentHashMap<>();
    
    /**
     * Create a new task executor with the specified concurrency limit.
     *
     * @param maxConcurrency Maximum number of concurrent tasks
     */
    public TaskExecutorService(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.concurrencyLimit = new Semaphore(maxConcurrency);
        this.accepting = new AtomicBoolean(false);
    }
    
    /**
     * Start accepting tasks.
     */
    public void start() {
        accepting.set(true);
        log.info("Task executor started: maxConcurrency={}", maxConcurrency);
    }
    
    /**
     * Stop accepting new tasks.
     */
    public void stopAcceptingTasks() {
        accepting.set(false);
        log.info("Task executor stopped accepting new tasks");
    }
    
    /**
     * Register a test to track its metrics separately.
     *
     * @param testId Test identifier
     */
    public void registerTest(String testId) {
        testMetrics.computeIfAbsent(testId, k -> new MetricsCollector());
        log.debug("Registered test for metrics tracking: {}", testId);
    }
    
    /**
     * Unregister a test and clean up its metrics.
     *
     * @param testId Test identifier
     */
    public void unregisterTest(String testId) {
        MetricsCollector removed = testMetrics.remove(testId);
        if (removed != null) {
            try {
                removed.close();
            } catch (Exception e) {
                log.warn("Error closing metrics collector for test {}", testId, e);
            }
            log.debug("Unregistered test: {}", testId);
        }
    }
    
    /**
     * Execute a task asynchronously.
     *
     * @param task Task to execute
     * @return CompletableFuture with task result
     * @throws RejectedExecutionException if executor is not accepting tasks
     */
    public CompletableFuture<TaskResult> executeAsync(Task task) {
        return executeAsync(task, null);
    }
    
    /**
     * Execute a task asynchronously with test ID for metrics isolation.
     *
     * @param task Task to execute
     * @param testId Test identifier for metrics tracking (null for global metrics)
     * @return CompletableFuture with task result
     * @throws RejectedExecutionException if executor is not accepting tasks
     */
    public CompletableFuture<TaskResult> executeAsync(Task task, String testId) {
        if (!accepting.get()) {
            throw new RejectedExecutionException("Task executor is not accepting new tasks");
        }
        
        // Acquire concurrency permit
        try {
            concurrencyLimit.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RejectedExecutionException("Interrupted while acquiring concurrency permit", e);
        }
        
        CompletableFuture<TaskResult> future = new CompletableFuture<>();
        
        executor.submit(() -> {
            long startNanos = System.nanoTime();
            try {
                TaskResult result = task.execute();
                
                // Record result in appropriate metrics collector
                if (testId != null) {
                    MetricsCollector collector = testMetrics.get(testId);
                    if (collector != null) {
                        collector.recordResult(result);
                    }
                }
                
                future.complete(result);
                
            } catch (Exception e) {
                long latencyNanos = System.nanoTime() - startNanos;
                log.error("Task execution failed", e);
                
                // Record failure in metrics
                TaskResult failedResult = SimpleTaskResult.failure(
                    0L,  // taskId
                    latencyNanos,
                    e.getMessage()
                );
                
                if (testId != null) {
                    MetricsCollector collector = testMetrics.get(testId);
                    if (collector != null) {
                        collector.recordResult(failedResult);
                    }
                }
                
                future.completeExceptionally(e);
            } finally {
                concurrencyLimit.release();
            }
        });
        
        return future;
    }
    
    /**
     * Execute a task synchronously (blocking).
     *
     * @param task Task to execute
     * @return Task result
     * @throws Exception if task execution fails
     */
    public TaskResult execute(Task task) throws Exception {
        return executeAsync(task).get();
    }
    
    /**
     * Submit a task for asynchronous execution (fire and forget).
     *
     * @param task Task to execute
     */
    public void submit(Task task) {
        executeAsync(task, null);  // Fire and forget
    }
    
    /**
     * Submit a task for asynchronous execution with test ID (fire and forget).
     *
     * @param task Task to execute
     * @param testId Test identifier for metrics tracking
     */
    public void submit(Task task, String testId) {
        executeAsync(task, testId);  // Fire and forget
    }
    
    /**
     * Check if executor can accept more tasks.
     *
     * @return true if under capacity
     */
    public boolean canAcceptMore() {
        return accepting.get() && concurrencyLimit.availablePermits() > 0;
    }
    
    /**
     * Get current executor statistics (aggregated across all tests).
     *
     * @return Executor statistics
     */
    public ExecutorStats getStats() {
        // Aggregate metrics from all active tests
        long totalTasks = 0;
        long failedTasks = 0;
        double totalTps = 0.0;
        
        for (MetricsCollector collector : testMetrics.values()) {
            MetricsSnapshot snapshot = collector.getSnapshot();
            totalTasks += snapshot.getTotalTasks();
            failedTasks += snapshot.getFailedTasks();
            totalTps += snapshot.getTps();
        }
        
        int active = maxConcurrency - concurrencyLimit.availablePermits();
        
        return new ExecutorStats(
            totalTasks,
            failedTasks,
            active,
            totalTps,
            accepting.get()
        );
    }
    
    /**
     * Get metrics snapshot for a specific test.
     *
     * @param testId Test identifier
     * @return Metrics snapshot, or null if test not found
     */
    public MetricsSnapshot getMetricsSnapshot(String testId) {
        MetricsCollector collector = testMetrics.get(testId);
        return collector != null ? collector.getSnapshot() : null;
    }
    
    /**
     * Get metrics snapshot aggregated across all tests.
     * Returns metrics for single test if only one active, otherwise null.
     *
     * @return Metrics snapshot for single test, or null if multiple tests
     */
    public MetricsSnapshot getMetricsSnapshot() {
        // If only one test, return its metrics
        if (testMetrics.size() == 1) {
            return testMetrics.values().iterator().next().getSnapshot();
        }
        
        // Otherwise return null - caller should use getMetricsSnapshot(testId)
        return null;
    }
    
    /**
     * Await termination of all in-flight tasks.
     *
     * @param timeoutSeconds Timeout in seconds
     * @return true if all tasks completed, false if timeout
     */
    public boolean awaitTermination(long timeoutSeconds) {
        stopAcceptingTasks();
        
        try {
            // Wait for all permits to be returned (all tasks done)
            long startTime = System.currentTimeMillis();
            long deadline = startTime + timeoutSeconds * 1000;
            
            while (concurrencyLimit.availablePermits() < maxConcurrency) {
                long currentTime = System.currentTimeMillis();
                if (currentTime >= deadline) {
                    log.warn("Timeout waiting for tasks to complete: {} tasks still running", 
                        maxConcurrency - concurrencyLimit.availablePermits());
                    return false;
                }
                Thread.sleep(100);
            }
            
            // Calculate remaining time for executor shutdown
            long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
            long remainingSeconds = Math.max(1, timeoutSeconds - elapsedSeconds);
            
            executor.shutdown();
            return executor.awaitTermination(remainingSeconds, TimeUnit.SECONDS);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while awaiting termination", e);
            return false;
        }
    }
    
    /**
     * Executor statistics.
     */
    public record ExecutorStats(
        long completedTasks,
        long failedTasks,
        int activeTasks,
        double currentTps,
        boolean accepting
    ) {}
}
