package net.vajraedge.perftest.concurrency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Controller for managing virtual user concurrency during load tests.
 * 
 * <p>Responsibilities:
 * <ul>
 *   <li>Determines target concurrency at any point in time using a RampStrategy</li>
 *   <li>Enforces maximum concurrency limits</li>
 *   <li>Optionally throttles based on maximum TPS (hybrid mode)</li>
 * </ul>
 * </p>
 * 
 * <p>This class is thread-safe and can be called from multiple threads.</p>
 */
public class ConcurrencyController {
    
    private static final Logger logger = LoggerFactory.getLogger(ConcurrencyController.class);
    
    private final RampStrategy rampStrategy;
    private final LoadTestMode mode;
    private final Optional<Integer> maxTpsLimit;
    
    /**
     * Create a concurrency controller for pure concurrency-based mode.
     * 
     * @param rampStrategy the ramp strategy to use
     */
    public ConcurrencyController(RampStrategy rampStrategy) {
        this(rampStrategy, LoadTestMode.CONCURRENCY_BASED, null);
    }
    
    /**
     * Create a concurrency controller with optional rate limiting.
     * 
     * @param rampStrategy the ramp strategy to use
     * @param mode the load test mode
     * @param maxTpsLimit optional maximum TPS limit (null for no limit)
     */
    public ConcurrencyController(RampStrategy rampStrategy, LoadTestMode mode, Integer maxTpsLimit) {
        if (rampStrategy == null) {
            throw new IllegalArgumentException("Ramp strategy cannot be null");
        }
        if (mode == null) {
            throw new IllegalArgumentException("Load test mode cannot be null");
        }
        if (maxTpsLimit != null && maxTpsLimit <= 0) {
            throw new IllegalArgumentException("Max TPS limit must be greater than 0");
        }
        
        this.rampStrategy = rampStrategy;
        this.mode = mode;
        this.maxTpsLimit = Optional.ofNullable(maxTpsLimit);
        
        logger.info("Concurrency controller initialized: mode={}, strategy={}, maxTpsLimit={}",
            mode, rampStrategy.getDescription(), maxTpsLimit);
    }
    
    /**
     * Get the target number of concurrent virtual users at a given time.
     * 
     * @param elapsedSeconds seconds elapsed since test start
     * @return target concurrency level
     */
    public int getTargetConcurrency(long elapsedSeconds) {
        return rampStrategy.getTargetConcurrency(elapsedSeconds);
    }
    
    /**
     * Check if requests should be throttled based on current TPS.
     * 
     * <p>Only applies in RATE_LIMITED mode with a configured max TPS.</p>
     * 
     * @param currentTps current transactions per second
     * @return true if should throttle, false otherwise
     */
    public boolean shouldThrottle(double currentTps) {
        if (mode != LoadTestMode.RATE_LIMITED) {
            return false;
        }
        
        return maxTpsLimit.map(limit -> currentTps >= limit).orElse(false);
    }
    
    /**
     * Get the load test mode.
     * 
     * @return the mode
     */
    public LoadTestMode getMode() {
        return mode;
    }
    
    /**
     * Get the maximum TPS limit if configured.
     * 
     * @return optional max TPS limit
     */
    public Optional<Integer> getMaxTpsLimit() {
        return maxTpsLimit;
    }
    
    /**
     * Get the ramp strategy.
     * 
     * @return the ramp strategy
     */
    public RampStrategy getRampStrategy() {
        return rampStrategy;
    }
    
    /**
     * Get the starting concurrency level.
     * 
     * @return starting concurrency
     */
    public int getStartingConcurrency() {
        return rampStrategy.getStartingConcurrency();
    }
    
    /**
     * Get the maximum concurrency level.
     * 
     * @return maximum concurrency
     */
    public int getMaxConcurrency() {
        return rampStrategy.getMaxConcurrency();
    }
    
    /**
     * Calculate the ramp-up progress as a percentage (0-100).
     * 
     * @param elapsedSeconds seconds elapsed since test start
     * @return ramp-up progress percentage
     */
    public double getRampUpProgress(long elapsedSeconds) {
        int startingConcurrency = rampStrategy.getStartingConcurrency();
        int maxConcurrency = rampStrategy.getMaxConcurrency();
        int currentConcurrency = rampStrategy.getTargetConcurrency(elapsedSeconds);
        
        // Avoid division by zero if starting == max
        if (maxConcurrency == startingConcurrency) {
            return 100.0;
        }
        
        double progress = ((double) (currentConcurrency - startingConcurrency) / 
                          (maxConcurrency - startingConcurrency)) * 100.0;
        
        // Clamp to 0-100 range
        return Math.max(0.0, Math.min(100.0, progress));
    }
}
