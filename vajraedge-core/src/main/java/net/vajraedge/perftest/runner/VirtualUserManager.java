package net.vajraedge.perftest.runner;

import net.vajraedge.sdk.TaskFactory;
import net.vajraedge.perftest.metrics.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages the lifecycle of virtual users.
 * 
 * <p>Responsibilities:
 * <ul>
 *   <li>Create and start virtual users</li>
 *   <li>Stop and remove virtual users</li>
 *   <li>Track active virtual users</li>
 *   <li>Coordinate graceful shutdown</li>
 * </ul>
 * </p>
 * 
 * <p>Thread-safe for concurrent access.</p>
 */
public class VirtualUserManager {
    
    private static final Logger logger = LoggerFactory.getLogger(VirtualUserManager.class);
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;
    
    private final TaskFactory taskFactory;
    private final MetricsCollector metricsCollector;
    private final AtomicLong taskIdGenerator;
    private final AtomicBoolean stopRequested;
    private final List<VirtualUser> activeUsers;
    private final int maxCapacity;
    
    /**
     * Create a virtual user manager.
     * 
     * @param taskFactory factory for creating tasks
     * @param metricsCollector collector for recording metrics
     * @param taskIdGenerator shared task ID generator
     * @param stopRequested shared stop flag
     * @param maxCapacity maximum number of virtual users
     */
    public VirtualUserManager(TaskFactory taskFactory, MetricsCollector metricsCollector,
                             AtomicLong taskIdGenerator, AtomicBoolean stopRequested,
                             int maxCapacity) {
        this.taskFactory = taskFactory;
        this.metricsCollector = metricsCollector;
        this.taskIdGenerator = taskIdGenerator;
        this.stopRequested = stopRequested;
        this.maxCapacity = maxCapacity;
        // Pre-allocate ArrayList to max capacity to avoid resizing during ramp-up
        this.activeUsers = new ArrayList<>(maxCapacity);
    }
    
    /**
     * Adjust the number of active virtual users to match target concurrency.
     * 
     * @param targetConcurrency desired number of virtual users
     */
    public void adjustConcurrency(int targetConcurrency) {
        synchronized (activeUsers) {
            int currentConcurrency = activeUsers.size();
            
            if (targetConcurrency > currentConcurrency) {
                rampUp(targetConcurrency - currentConcurrency, currentConcurrency, targetConcurrency);
            } else if (targetConcurrency < currentConcurrency) {
                rampDown(currentConcurrency - targetConcurrency, currentConcurrency, targetConcurrency);
            }
        }
    }
    
    /**
     * Ramp up by adding virtual users.
     * 
     * @param count number of users to add
     * @param currentConcurrency current concurrency level
     * @param targetConcurrency target concurrency level
     */
    private void rampUp(int count, int currentConcurrency, int targetConcurrency) {
        logger.debug("Ramping up: adding {} virtual users (current: {}, target: {})",
            count, currentConcurrency, targetConcurrency);
        
        for (int i = 0; i < count; i++) {
            VirtualUser user = new VirtualUser(taskFactory, metricsCollector, 
                taskIdGenerator, stopRequested);
            activeUsers.add(user);
            user.start();
        }
    }
    
    /**
     * Ramp down by removing virtual users.
     * 
     * @param count number of users to remove
     * @param currentConcurrency current concurrency level
     * @param targetConcurrency target concurrency level
     */
    private void rampDown(int count, int currentConcurrency, int targetConcurrency) {
        logger.debug("Ramping down: removing {} virtual users (current: {}, target: {})",
            count, currentConcurrency, targetConcurrency);
        
        for (int i = 0; i < count; i++) {
            if (!activeUsers.isEmpty()) {
                VirtualUser user = activeUsers.remove(activeUsers.size() - 1);
                user.stop();
            }
        }
    }
    
    /**
     * Get the number of active virtual users.
     * 
     * @return active user count
     */
    public int getActiveUserCount() {
        synchronized (activeUsers) {
            return activeUsers.size();
        }
    }
    
    /**
     * Shutdown all active virtual users.
     * Waits for all users to complete with a timeout.
     */
    public void shutdownAll() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        synchronized (activeUsers) {
            logger.info("Shutting down {} virtual users", activeUsers.size());
            
            // Stop all users and collect their futures
            for (VirtualUser user : activeUsers) {
                user.stop();
                futures.add(user.getFuture());
            }
            
            activeUsers.clear();
        }
        
        // Wait for all users to complete (with timeout)
        if (!futures.isEmpty()) {
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                logger.debug("All virtual users stopped gracefully");
            } catch (Exception e) {
                logger.warn("Timeout waiting for virtual users to stop, proceeding with shutdown", e);
            }
        }
    }
    
    /**
     * Get the maximum capacity.
     * 
     * @return max capacity
     */
    public int getMaxCapacity() {
        return maxCapacity;
    }
}
