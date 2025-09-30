package com.example.grpc.loadtest.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Virtual Threads-based executor for high-concurrency load testing
 * Leverages Java 21's virtual threads for efficient handling of many concurrent requests
 */
public class VirtualThreadExecutor implements AutoCloseable {
    
    private static final Logger logger = LoggerFactory.getLogger(VirtualThreadExecutor.class);
    
    private final ExecutorService virtualThreadExecutor;
    private final Semaphore concurrencyLimiter;
    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private final AtomicLong submittedTasks = new AtomicLong(0);
    private final AtomicLong completedTasks = new AtomicLong(0);
    private final int maxConcurrentRequests;
    
    public VirtualThreadExecutor(int maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
        this.concurrencyLimiter = new Semaphore(maxConcurrentRequests);
        
        // Create virtual thread executor using Java 21's virtual threads
        this.virtualThreadExecutor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual()
                .name("load-test-", 0)
                .factory()
        );
        
        logger.info("Created VirtualThreadExecutor with max concurrent requests: {}", maxConcurrentRequests);
    }
    
    /**
     * Submit a task to be executed on a virtual thread
     * This method will block if the maximum concurrency limit is reached
     */
    public <T> CompletableFuture<T> submit(Callable<T> task) {
        return submitInternal(task, true);
    }
    
    /**
     * Submit a task to be executed on a virtual thread
     * This method will return null immediately if the maximum concurrency limit is reached
     */
    public <T> CompletableFuture<T> trySubmit(Callable<T> task) {
        return submitInternal(task, false);
    }
    
    private <T> CompletableFuture<T> submitInternal(Callable<T> task, boolean blocking) {
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
            return null; // Could not acquire permit
        }
        
        submittedTasks.incrementAndGet();
        activeRequests.incrementAndGet();
        
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, virtualThreadExecutor);
        
        // Release the semaphore when the task completes (success or failure)
        future.whenComplete((result, throwable) -> {
            activeRequests.decrementAndGet();
            completedTasks.incrementAndGet();
            concurrencyLimiter.release();
        });
        
        return future;
    }
    
    /**
     * Submit a runnable task
     */
    public CompletableFuture<Void> submit(Runnable task) {
        return submit(() -> {
            task.run();
            return null;
        });
    }
    
    /**
     * Try to submit a runnable task (non-blocking)
     */
    public CompletableFuture<Void> trySubmit(Runnable task) {
        return trySubmit(() -> {
            task.run();
            return null;
        });
    }
    
    /**
     * Get current number of active requests
     */
    public int getActiveRequests() {
        return activeRequests.get();
    }
    
    /**
     * Get total number of submitted tasks
     */
    public long getSubmittedTasks() {
        return submittedTasks.get();
    }
    
    /**
     * Get total number of completed tasks
     */
    public long getCompletedTasks() {
        return completedTasks.get();
    }
    
    /**
     * Get available permits (how many more concurrent requests can be submitted)
     */
    public int getAvailablePermits() {
        return concurrencyLimiter.availablePermits();
    }
    
    /**
     * Get the maximum number of concurrent requests
     */
    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }
    
    /**
     * Get current utilization as a percentage (0-100)
     */
    public double getUtilizationPercent() {
        return ((double) activeRequests.get() / maxConcurrentRequests) * 100.0;
    }
    
    /**
     * Check if the executor is saturated (no available permits)
     */
    public boolean isSaturated() {
        return concurrencyLimiter.availablePermits() == 0;
    }
    
    /**
     * Wait for all currently submitted tasks to complete
     */
    public void awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        
        while (activeRequests.get() > 0 && System.nanoTime() < deadline) {
            Thread.sleep(100); // Check every 100ms
        }
        
        if (activeRequests.get() > 0) {
            logger.warn("Timeout waiting for tasks to complete. Active requests: {}", activeRequests.get());
        }
    }
    
    /**
     * Get statistics about the executor
     */
    public ExecutorStats getStats() {
        return new ExecutorStats(
            submittedTasks.get(),
            completedTasks.get(),
            activeRequests.get(),
            maxConcurrentRequests,
            getAvailablePermits(),
            getUtilizationPercent()
        );
    }
    
    @Override
    public void close() {
        logger.info("Shutting down VirtualThreadExecutor...");
        
        try {
            // Wait for active requests to complete
            awaitCompletion(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.warn("Interrupted while waiting for tasks to complete", e);
            Thread.currentThread().interrupt();
        }
        
        // Shutdown the executor
        virtualThreadExecutor.shutdown();
        
        try {
            if (!virtualThreadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("Executor did not terminate gracefully, forcing shutdown");
                virtualThreadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.warn("Interrupted while shutting down executor", e);
            virtualThreadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        ExecutorStats finalStats = getStats();
        logger.info("VirtualThreadExecutor shutdown complete. Final stats: {}", finalStats);
    }
    
    /**
     * Statistics about the executor
     */
    public static class ExecutorStats {
        private final long submittedTasks;
        private final long completedTasks;
        private final int activeRequests;
        private final int maxConcurrentRequests;
        private final int availablePermits;
        private final double utilizationPercent;
        
        public ExecutorStats(long submittedTasks, long completedTasks, int activeRequests,
                           int maxConcurrentRequests, int availablePermits, double utilizationPercent) {
            this.submittedTasks = submittedTasks;
            this.completedTasks = completedTasks;
            this.activeRequests = activeRequests;
            this.maxConcurrentRequests = maxConcurrentRequests;
            this.availablePermits = availablePermits;
            this.utilizationPercent = utilizationPercent;
        }
        
        // Getters
        public long getSubmittedTasks() { return submittedTasks; }
        public long getCompletedTasks() { return completedTasks; }
        public int getActiveRequests() { return activeRequests; }
        public int getMaxConcurrentRequests() { return maxConcurrentRequests; }
        public int getAvailablePermits() { return availablePermits; }
        public double getUtilizationPercent() { return utilizationPercent; }
        
        @Override
        public String toString() {
            return String.format(
                "ExecutorStats{submitted=%d, completed=%d, active=%d, max=%d, available=%d, utilization=%.1f%%}",
                submittedTasks, completedTasks, activeRequests, maxConcurrentRequests, 
                availablePermits, utilizationPercent
            );
        }
    }
}