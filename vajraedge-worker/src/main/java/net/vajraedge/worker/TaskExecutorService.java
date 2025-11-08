package net.vajraedge.worker;

import net.vajraedge.sdk.Task;
import net.vajraedge.sdk.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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
    private final AtomicLong completedTasks;
    private final AtomicLong failedTasks;
    
    private final ConcurrentLinkedQueue<TaskResult> recentResults;
    private static final int MAX_RECENT_RESULTS = 1000;
    
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
        this.completedTasks = new AtomicLong(0);
        this.failedTasks = new AtomicLong(0);
        this.recentResults = new ConcurrentLinkedQueue<>();
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
            try {
                TaskResult result = task.execute();
                
                // Track result
                if (result.isSuccess()) {
                    completedTasks.incrementAndGet();
                } else {
                    failedTasks.incrementAndGet();
                }
                
                // Store recent result
                addRecentResult(result);
                
                future.complete(result);
                
            } catch (Exception e) {
                log.error("Task execution failed", e);
                failedTasks.incrementAndGet();
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
     * Add result to recent results queue (bounded).
     */
    private void addRecentResult(TaskResult result) {
        recentResults.offer(result);
        
        // Keep queue bounded
        while (recentResults.size() > MAX_RECENT_RESULTS) {
            recentResults.poll();
        }
    }
    
    /**
     * Get current executor statistics.
     *
     * @return Executor statistics
     */
    public ExecutorStats getStats() {
        return new ExecutorStats(
            completedTasks.get(),
            failedTasks.get(),
            maxConcurrency - concurrencyLimit.availablePermits(),
            accepting.get()
        );
    }
    
    /**
     * Get recent task results for metrics reporting.
     *
     * @return List of recent results
     */
    public ConcurrentLinkedQueue<TaskResult> getRecentResults() {
        return recentResults;
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
        boolean accepting
    ) {}
}
