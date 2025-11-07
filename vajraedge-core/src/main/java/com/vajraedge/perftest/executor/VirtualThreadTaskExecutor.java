package com.vajraedge.perftest.executor;

import com.vajraedge.sdk.Task;
import com.vajraedge.sdk.TaskExecutor;
import com.vajraedge.sdk.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Virtual thread-based task executor.
 * 
 * Single Responsibility: Concurrent task execution using virtual threads
 */
public class VirtualThreadTaskExecutor implements TaskExecutor {
    
    private static final Logger logger = LoggerFactory.getLogger(VirtualThreadTaskExecutor.class);
    private static final int DEFAULT_SHUTDOWN_TIMEOUT_SECONDS = 5;
    
    private final ExecutorService executor;
    private final Semaphore concurrencyLimiter;
    private final AtomicInteger activeTasks = new AtomicInteger(0);
    private final AtomicLong submittedTasks = new AtomicLong(0);
    private final AtomicLong completedTasks = new AtomicLong(0);
    
    public VirtualThreadTaskExecutor(int maxConcurrency) {
        this.concurrencyLimiter = new Semaphore(maxConcurrency);
        this.executor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual()
                .name("perf-test-", 0)
                .factory()
        );
        logger.info("Created VirtualThreadTaskExecutor with max concurrency: {}", maxConcurrency);
    }
    
    @Override
    public CompletableFuture<TaskResult> submit(Task task) {
        return submitInternal(task, true);
    }
    
    @Override
    public CompletableFuture<TaskResult> trySubmit(Task task) {
        return submitInternal(task, false);
    }
    
    private CompletableFuture<TaskResult> submitInternal(Task task, boolean blocking) {
        boolean acquired;
        
        if (blocking) {
            try {
                concurrencyLimiter.acquire();
                acquired = true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return CompletableFuture.failedFuture(e);
            }
        } else {
            acquired = concurrencyLimiter.tryAcquire();
        }
        
        if (!acquired) {
            return null;
        }
        
        submittedTasks.incrementAndGet();
        activeTasks.incrementAndGet();
        
        CompletableFuture<TaskResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                return task.execute();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor);
        
        future.whenComplete((result, throwable) -> {
            activeTasks.decrementAndGet();
            completedTasks.incrementAndGet();
            concurrencyLimiter.release();
        });
        
        return future;
    }
    
    @Override
    public int getActiveTasks() {
        return activeTasks.get();
    }
    
    @Override
    public long getSubmittedTasks() {
        return submittedTasks.get();
    }
    
    @Override
    public long getCompletedTasks() {
        return completedTasks.get();
    }
    
    /**
     * Get the number of pending tasks (submitted but not yet started execution).
     * These are tasks waiting for a semaphore permit or queued in the executor.
     * 
     * @return number of pending tasks
     */
    public int getPendingTasks() {
        long submitted = submittedTasks.get();
        long completed = completedTasks.get();
        int active = activeTasks.get();
        return (int) Math.max(0, submitted - completed - active);
    }
    
    @Override
    public void close() {
        logger.info("Shutting down VirtualThreadTaskExecutor...");
        
        executor.shutdown();
        
        try {
            if (!executor.awaitTermination(DEFAULT_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                logger.warn("Executor did not terminate gracefully, forcing shutdown");
                executor.shutdownNow();
                if (!executor.awaitTermination(DEFAULT_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    logger.error("Executor did not terminate after forced shutdown");
                }
            }
        } catch (InterruptedException e) {
            logger.warn("Interrupted while shutting down executor", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("VirtualThreadTaskExecutor shutdown complete");
    }
}
