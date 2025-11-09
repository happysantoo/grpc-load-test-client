package net.vajraedge.perftest.runner;

import net.vajraedge.sdk.Task;
import net.vajraedge.sdk.TaskFactory;
import net.vajraedge.sdk.TaskResult;
import net.vajraedge.sdk.metrics.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a single virtual user that executes tasks in a loop.
 * 
 * <p>A virtual user continuously executes tasks until stopped or interrupted.
 * Each task execution result is recorded in the metrics collector.</p>
 * 
 * <p>Thread-safe and handles interruptions gracefully.</p>
 */
public class VirtualUser {
    
    private static final Logger logger = LoggerFactory.getLogger(VirtualUser.class);
    
    private final TaskFactory taskFactory;
    private final MetricsCollector metricsCollector;
    private final AtomicLong taskIdGenerator;
    private final AtomicBoolean stopRequested;
    private final CompletableFuture<Void> future;
    private final AtomicBoolean running;
    
    /**
     * Create a virtual user.
     * 
     * @param taskFactory factory for creating tasks
     * @param metricsCollector collector for recording metrics
     * @param taskIdGenerator shared task ID generator
     * @param stopRequested shared stop flag
     */
    public VirtualUser(TaskFactory taskFactory, MetricsCollector metricsCollector, 
                      AtomicLong taskIdGenerator, AtomicBoolean stopRequested) {
        this.taskFactory = taskFactory;
        this.metricsCollector = metricsCollector;
        this.taskIdGenerator = taskIdGenerator;
        this.stopRequested = stopRequested;
        this.running = new AtomicBoolean(true);
        this.future = CompletableFuture.runAsync(this::run);
    }
    
    /**
     * Main execution loop for the virtual user.
     */
    private void run() {
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
    
    /**
     * Start the virtual user.
     * Note: Virtual user is already started in constructor via CompletableFuture.
     */
    public void start() {
        // Already started in constructor
    }
    
    /**
     * Stop the virtual user.
     * Sets the running flag to false and cancels the future.
     */
    public void stop() {
        running.set(false);
        future.cancel(true);
    }
    
    /**
     * Get the completion future for this virtual user.
     * 
     * @return completion future
     */
    public CompletableFuture<Void> getFuture() {
        return future;
    }
    
    /**
     * Check if the virtual user is running.
     * 
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return running.get() && !future.isDone();
    }
}
