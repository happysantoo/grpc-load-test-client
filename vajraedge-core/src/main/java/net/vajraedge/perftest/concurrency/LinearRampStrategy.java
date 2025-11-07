package net.vajraedge.perftest.concurrency;

/**
 * Linear ramp-up strategy with optional sustain phase.
 * 
 * <p>Increases concurrency linearly from starting level to maximum level
 * over a specified ramp duration, then maintains maximum concurrency for a sustain period.</p>
 * 
 * <p>Example: Start with 10 users, ramp to 100 users over 60 seconds,
 * then sustain at 100 users for 30 seconds.
 * At t=30s, concurrency will be 55 users (ramp phase).
 * At t=60s-90s, concurrency will be 100 users (sustain phase).</p>
 */
public class LinearRampStrategy implements RampStrategy {
    
    private final int startingConcurrency;
    private final int maxConcurrency;
    private final long rampDurationSeconds;
    private final long sustainDurationSeconds;
    
    /**
     * Create a linear ramp strategy without sustain phase.
     * 
     * @param startingConcurrency initial number of virtual users (must be > 0)
     * @param maxConcurrency maximum number of virtual users (must be >= startingConcurrency)
     * @param rampDurationSeconds duration of ramp-up period in seconds (must be > 0)
     * @throws IllegalArgumentException if parameters are invalid
     */
    public LinearRampStrategy(int startingConcurrency, int maxConcurrency, long rampDurationSeconds) {
        this(startingConcurrency, maxConcurrency, rampDurationSeconds, 0L);
    }
    
    /**
     * Create a linear ramp strategy with sustain phase.
     * 
     * @param startingConcurrency initial number of virtual users (must be > 0)
     * @param maxConcurrency maximum number of virtual users (must be >= startingConcurrency)
     * @param rampDurationSeconds duration of ramp-up period in seconds (must be > 0)
     * @param sustainDurationSeconds duration to maintain max concurrency in seconds (>= 0)
     * @throws IllegalArgumentException if parameters are invalid
     */
    public LinearRampStrategy(int startingConcurrency, int maxConcurrency, 
                              long rampDurationSeconds, long sustainDurationSeconds) {
        if (startingConcurrency <= 0) {
            throw new IllegalArgumentException("Starting concurrency must be greater than 0");
        }
        if (maxConcurrency < startingConcurrency) {
            throw new IllegalArgumentException("Max concurrency must be >= starting concurrency");
        }
        if (rampDurationSeconds <= 0) {
            throw new IllegalArgumentException("Ramp duration must be greater than 0");
        }
        if (sustainDurationSeconds < 0) {
            throw new IllegalArgumentException("Sustain duration cannot be negative");
        }
        
        this.startingConcurrency = startingConcurrency;
        this.maxConcurrency = maxConcurrency;
        this.rampDurationSeconds = rampDurationSeconds;
        this.sustainDurationSeconds = sustainDurationSeconds;
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
        String baseDesc = String.format("Linear ramp from %d to %d users over %d seconds",
            startingConcurrency, maxConcurrency, rampDurationSeconds);
        
        if (sustainDurationSeconds > 0) {
            return baseDesc + String.format(", then sustain at %d users for %d seconds",
                maxConcurrency, sustainDurationSeconds);
        }
        return baseDesc;
    }
    
    public long getRampDurationSeconds() {
        return rampDurationSeconds;
    }
    
    public long getSustainDurationSeconds() {
        return sustainDurationSeconds;
    }
    
    /**
     * Calculate the total time for ramp-up + sustain phases.
     * 
     * @return total duration in seconds
     */
    public long getTotalPhaseDuration() {
        return rampDurationSeconds + sustainDurationSeconds;
    }
}
