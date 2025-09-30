package com.example.grpc.loadtest.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * Controls the rate of request execution to maintain a target TPS (Transactions Per Second)
 * Uses a token bucket-like algorithm with precise timing to ensure accurate rate limiting
 */
public class ThroughputController {
    
    private static final Logger logger = LoggerFactory.getLogger(ThroughputController.class);
    
    private final int targetTps;
    private final long intervalNanos; // Time between requests in nanoseconds
    private final AtomicLong nextExecutionTime; // Next allowed execution time in nanoseconds
    private final AtomicLong totalPermitsIssued = new AtomicLong(0);
    private final Instant startTime;
    
    // Ramp-up support
    private final Duration rampUpDuration;
    private final boolean hasRampUp;
    private final long rampUpEndNanos;
    
    public ThroughputController(int targetTps) {
        this(targetTps, Duration.ZERO);
    }
    
    public ThroughputController(int targetTps, Duration rampUpDuration) {
        if (targetTps <= 0) {
            throw new IllegalArgumentException("Target TPS must be positive");
        }
        
        this.targetTps = targetTps;
        this.rampUpDuration = rampUpDuration;
        this.hasRampUp = !rampUpDuration.isZero();
        this.startTime = Instant.now();
        
        // Calculate interval between requests in nanoseconds
        this.intervalNanos = 1_000_000_000L / targetTps;
        
        // Initialize next execution time to current time
        this.nextExecutionTime = new AtomicLong(System.nanoTime());
        this.rampUpEndNanos = System.nanoTime() + rampUpDuration.toNanos();
        
        logger.info("Created ThroughputController: targetTps={}, intervalNanos={}, rampUpDuration={}", 
                   targetTps, intervalNanos, rampUpDuration);
    }
    
    /**
     * Acquire a permit to execute a request. This method will block until it's time to execute
     * the next request according to the configured TPS rate.
     * 
     * @return true if permit was acquired, false if interrupted
     */
    public boolean acquirePermit() {
        try {
            long currentTime = System.nanoTime();
            long scheduledTime = nextExecutionTime.getAndAdd(getCurrentIntervalNanos());
            
            // If we're behind schedule, don't wait (catch up)
            if (scheduledTime <= currentTime) {
                totalPermitsIssued.incrementAndGet();
                return true;
            }
            
            // Wait until it's time to execute
            long waitTime = scheduledTime - currentTime;
            if (waitTime > 0) {
                LockSupport.parkNanos(waitTime);
            }
            
            totalPermitsIssued.incrementAndGet();
            return true;
            
        } catch (Exception e) {
            logger.debug("Interrupted while waiting for permit", e);
            return false;
        }
    }
    
    /**
     * Try to acquire a permit without waiting. Returns immediately.
     * 
     * @return true if permit was acquired, false if we need to wait
     */
    public boolean tryAcquirePermit() {
        long currentTime = System.nanoTime();
        long scheduledTime = nextExecutionTime.getAndAdd(getCurrentIntervalNanos());
        
        if (scheduledTime <= currentTime) {
            totalPermitsIssued.incrementAndGet();
            return true;
        }
        
        // Put back the interval we just consumed since we're not going to use it
        nextExecutionTime.addAndGet(-getCurrentIntervalNanos());
        return false;
    }
    
    /**
     * Get the current interval between requests, considering ramp-up
     */
    private long getCurrentIntervalNanos() {
        if (!hasRampUp) {
            return intervalNanos;
        }
        
        long currentTime = System.nanoTime();
        if (currentTime >= rampUpEndNanos) {
            return intervalNanos; // Ramp-up complete, use target interval
        }
        
        // Calculate current TPS based on ramp-up progress
        long elapsedNanos = currentTime - (rampUpEndNanos - rampUpDuration.toNanos());
        double progress = (double) elapsedNanos / rampUpDuration.toNanos();
        progress = Math.max(0.0, Math.min(1.0, progress)); // Clamp between 0 and 1
        
        // Start from 1 TPS and ramp up to target TPS
        int currentTps = (int) Math.max(1, 1 + (targetTps - 1) * progress);
        return 1_000_000_000L / currentTps;
    }
    
    /**
     * Get the current effective TPS considering ramp-up
     */
    public int getCurrentTps() {
        if (!hasRampUp) {
            return targetTps;
        }
        
        long currentTime = System.nanoTime();
        if (currentTime >= rampUpEndNanos) {
            return targetTps;
        }
        
        long elapsedNanos = currentTime - (rampUpEndNanos - rampUpDuration.toNanos());
        double progress = (double) elapsedNanos / rampUpDuration.toNanos();
        progress = Math.max(0.0, Math.min(1.0, progress));
        
        return (int) Math.max(1, 1 + (targetTps - 1) * progress);
    }
    
    /**
     * Get the time until the next permit can be acquired
     */
    public Duration getTimeUntilNextPermit() {
        long currentTime = System.nanoTime();
        long nextTime = nextExecutionTime.get();
        
        long waitNanos = Math.max(0, nextTime - currentTime);
        return Duration.ofNanos(waitNanos);
    }
    
    /**
     * Get the total number of permits issued
     */
    public long getTotalPermitsIssued() {
        return totalPermitsIssued.get();
    }
    
    /**
     * Get the target TPS
     */
    public int getTargetTps() {
        return targetTps;
    }
    
    /**
     * Get the actual TPS based on permits issued
     */
    public double getActualTps() {
        Duration elapsed = Duration.between(startTime, Instant.now());
        if (elapsed.toMillis() < 1000) {
            return 0.0; // Not enough time to calculate meaningful TPS
        }
        
        return totalPermitsIssued.get() / elapsed.toSeconds();
    }
    
    /**
     * Check if we're currently in ramp-up phase
     */
    public boolean isInRampUp() {
        return hasRampUp && System.nanoTime() < rampUpEndNanos;
    }
    
    /**
     * Get ramp-up progress as a percentage (0-100)
     */
    public double getRampUpProgress() {
        if (!hasRampUp) {
            return 100.0;
        }
        
        long currentTime = System.nanoTime();
        if (currentTime >= rampUpEndNanos) {
            return 100.0;
        }
        
        long elapsedNanos = currentTime - (rampUpEndNanos - rampUpDuration.toNanos());
        double progress = (double) elapsedNanos / rampUpDuration.toNanos();
        return Math.max(0.0, Math.min(100.0, progress * 100.0));
    }
    
    /**
     * Reset the controller (useful for restarting tests)
     */
    public void reset() {
        nextExecutionTime.set(System.nanoTime());
        totalPermitsIssued.set(0);
        logger.info("ThroughputController reset");
    }
    
    /**
     * Get statistics about the throughput controller
     */
    public ThroughputStats getStats() {
        return new ThroughputStats(
            targetTps,
            getCurrentTps(),
            getActualTps(),
            totalPermitsIssued.get(),
            isInRampUp(),
            getRampUpProgress(),
            getTimeUntilNextPermit()
        );
    }
    
    /**
     * Statistics about the throughput controller
     */
    public static class ThroughputStats {
        private final int targetTps;
        private final int currentTps;
        private final double actualTps;
        private final long totalPermits;
        private final boolean inRampUp;
        private final double rampUpProgress;
        private final Duration timeUntilNextPermit;
        
        public ThroughputStats(int targetTps, int currentTps, double actualTps, long totalPermits,
                             boolean inRampUp, double rampUpProgress, Duration timeUntilNextPermit) {
            this.targetTps = targetTps;
            this.currentTps = currentTps;
            this.actualTps = actualTps;
            this.totalPermits = totalPermits;
            this.inRampUp = inRampUp;
            this.rampUpProgress = rampUpProgress;
            this.timeUntilNextPermit = timeUntilNextPermit;
        }
        
        // Getters
        public int getTargetTps() { return targetTps; }
        public int getCurrentTps() { return currentTps; }
        public double getActualTps() { return actualTps; }
        public long getTotalPermits() { return totalPermits; }
        public boolean isInRampUp() { return inRampUp; }
        public double getRampUpProgress() { return rampUpProgress; }
        public Duration getTimeUntilNextPermit() { return timeUntilNextPermit; }
        
        @Override
        public String toString() {
            if (inRampUp) {
                return String.format(
                    "ThroughputStats{target=%d, current=%d, actual=%.1f, permits=%d, rampUp=%.1f%%}",
                    targetTps, currentTps, actualTps, totalPermits, rampUpProgress
                );
            } else {
                return String.format(
                    "ThroughputStats{target=%d, actual=%.1f, permits=%d}",
                    targetTps, actualTps, totalPermits
                );
            }
        }
    }
}