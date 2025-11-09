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
    private final MetricsCollector metricsCollector;
    
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
        this.metricsCollector = new MetricsCollector();
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
     * Execute a task asynchronously.
     *
     * @param task Task to execute
     * @return CompletableFuture with task result
     * @throws RejectedExecutionException if executor is not accepting tasks
     */
    public CompletableFuture<TaskResult> executeAsync(Task task) {
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
                
                // Record result in metrics collector
                metricsCollector.recordResult(result);
                
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
                metricsCollector.recordResult(failedResult);
                
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
        executeAsync(task);  // Fire and forget
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
     * Get current executor statistics.
     *
     * @return Executor statistics
     */
    public ExecutorStats getStats() {
        MetricsSnapshot snapshot = metricsCollector.getSnapshot();
        int active = maxConcurrency - concurrencyLimit.availablePermits();
        
        return new ExecutorStats(
            snapshot.getTotalTasks(),
            snapshot.getFailedTasks(),
            active,
            snapshot.getTps(),
            accepting.get()
        );
    }
    
    /**
     * Get metrics snapshot for reporting.
     *
     * @return Metrics snapshot
     */
    public MetricsSnapshot getMetricsSnapshot() {
        return metricsCollector.getSnapshot();
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
