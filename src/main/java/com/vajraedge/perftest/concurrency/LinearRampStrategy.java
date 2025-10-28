package com.vajraedge.perftest.concurrency;

/**
 * Linear ramp-up strategy.
 * 
 * <p>Increases concurrency linearly from starting level to maximum level
 * over a specified ramp duration.</p>
 * 
 * <p>Example: Start with 10 users, ramp to 100 users over 60 seconds.
 * At t=30s, concurrency will be 55 users.</p>
 */
public class LinearRampStrategy implements RampStrategy {
    
    private final int startingConcurrency;
    private final int maxConcurrency;
    private final long rampDurationSeconds;
    
    /**
     * Create a linear ramp strategy.
     * 
     * @param startingConcurrency initial number of virtual users (must be > 0)
     * @param maxConcurrency maximum number of virtual users (must be >= startingConcurrency)
     * @param rampDurationSeconds duration of ramp-up period in seconds (must be > 0)
     * @throws IllegalArgumentException if parameters are invalid
     */
    public LinearRampStrategy(int startingConcurrency, int maxConcurrency, long rampDurationSeconds) {
        if (startingConcurrency <= 0) {
            throw new IllegalArgumentException("Starting concurrency must be greater than 0");
        }
        if (maxConcurrency < startingConcurrency) {
            throw new IllegalArgumentException("Max concurrency must be >= starting concurrency");
        }
        if (rampDurationSeconds <= 0) {
            throw new IllegalArgumentException("Ramp duration must be greater than 0");
        }
        
        this.startingConcurrency = startingConcurrency;
        this.maxConcurrency = maxConcurrency;
        this.rampDurationSeconds = rampDurationSeconds;
    }
    
    @Override
    public int getTargetConcurrency(long elapsedSeconds) {
        if (elapsedSeconds <= 0) {
            return startingConcurrency;
        }
        
        if (elapsedSeconds >= rampDurationSeconds) {
            return maxConcurrency;
        }
        
        // Linear interpolation
        double progress = (double) elapsedSeconds / rampDurationSeconds;
        int range = maxConcurrency - startingConcurrency;
        int increase = (int) Math.round(range * progress);
        
        return startingConcurrency + increase;
    }
    
    @Override
    public int getStartingConcurrency() {
        return startingConcurrency;
    }
    
    @Override
    public int getMaxConcurrency() {
        return maxConcurrency;
    }
    
    @Override
    public String getDescription() {
        return String.format("Linear ramp from %d to %d users over %d seconds",
            startingConcurrency, maxConcurrency, rampDurationSeconds);
    }
    
    public long getRampDurationSeconds() {
        return rampDurationSeconds;
    }
}
