package net.vajraedge.perftest.rate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * Controls the rate of task execution.
 * 
 * Single Responsibility: Rate limiting and pacing
 */
public class RateController {
    
    private static final Logger logger = LoggerFactory.getLogger(RateController.class);
    
    private final int targetTps;
    private final long intervalNanos;
    private final AtomicLong nextExecutionTime;
    private final AtomicLong totalPermitsIssued = new AtomicLong(0);
    private final Duration rampUpDuration;
    private final boolean hasRampUp;
    private final long rampUpEndNanos;
    private final long startNanos;
    
    public RateController(int targetTps, Duration rampUpDuration) {
        if (targetTps <= 0) {
            throw new IllegalArgumentException("Target TPS must be positive");
        }
        
        this.targetTps = targetTps;
        this.rampUpDuration = rampUpDuration;
        this.hasRampUp = !rampUpDuration.isZero();
        this.startNanos = System.nanoTime();
        this.intervalNanos = 1_000_000_000L / targetTps;
        this.nextExecutionTime = new AtomicLong(startNanos);
        this.rampUpEndNanos = startNanos + rampUpDuration.toNanos();
        
        logger.info("Created RateController: targetTps={}, rampUpDuration={}", targetTps, rampUpDuration);
    }
    
    public boolean acquirePermit() {
        try {
            long currentTime = System.nanoTime();
            long scheduledTime = nextExecutionTime.getAndAdd(getCurrentIntervalNanos());
            
            if (scheduledTime <= currentTime) {
                totalPermitsIssued.incrementAndGet();
                return true;
            }
            
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
    
    private long getCurrentIntervalNanos() {
        if (!hasRampUp) {
            return intervalNanos;
        }
        
        long currentTime = System.nanoTime();
        if (currentTime >= rampUpEndNanos) {
            return intervalNanos;
        }
        
        long elapsedNanos = currentTime - startNanos;
        double progress = (double) elapsedNanos / rampUpDuration.toNanos();
        progress = Math.max(0.0, Math.min(1.0, progress));
        
        int currentTps = (int) Math.max(1, 1 + (targetTps - 1) * progress);
        return 1_000_000_000L / currentTps;
    }
    
    public long getTotalPermitsIssued() {
        return totalPermitsIssued.get();
    }
}
