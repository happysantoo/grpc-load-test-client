package com.vajraedge.perftest.concurrency;

/**
 * Step-based ramp-up strategy.
 * 
 * <p>Increases concurrency in discrete steps at regular intervals.</p>
 * 
 * <p>Example: Start with 10 users, add 10 users every 30 seconds up to 100 users.
 * Timeline:
 * <ul>
 *   <li>t=0s: 10 users</li>
 *   <li>t=30s: 20 users</li>
 *   <li>t=60s: 30 users</li>
 *   <li>t=90s: 40 users</li>
 *   <li>...</li>
 *   <li>t=270s: 100 users (stays at max)</li>
 * </ul>
 * </p>
 */
public class StepRampStrategy implements RampStrategy {
    
    private final int startingConcurrency;
    private final int rampStep;
    private final long rampIntervalSeconds;
    private final int maxConcurrency;
    
    /**
     * Create a step ramp strategy.
     * 
     * @param startingConcurrency initial number of virtual users (must be > 0)
     * @param rampStep number of users to add at each interval (must be > 0)
     * @param rampIntervalSeconds time between steps in seconds (must be > 0)
     * @param maxConcurrency maximum number of virtual users (must be >= startingConcurrency)
     * @throws IllegalArgumentException if parameters are invalid
     */
    public StepRampStrategy(int startingConcurrency, int rampStep, long rampIntervalSeconds, int maxConcurrency) {
        if (startingConcurrency <= 0) {
            throw new IllegalArgumentException("Starting concurrency must be greater than 0");
        }
        if (rampStep <= 0) {
            throw new IllegalArgumentException("Ramp step must be greater than 0");
        }
        if (rampIntervalSeconds <= 0) {
            throw new IllegalArgumentException("Ramp interval must be greater than 0");
        }
        if (maxConcurrency < startingConcurrency) {
            throw new IllegalArgumentException("Max concurrency must be >= starting concurrency");
        }
        
        this.startingConcurrency = startingConcurrency;
        this.rampStep = rampStep;
        this.rampIntervalSeconds = rampIntervalSeconds;
        this.maxConcurrency = maxConcurrency;
    }
    
    @Override
    public int getTargetConcurrency(long elapsedSeconds) {
        if (elapsedSeconds < 0) {
            return startingConcurrency;
        }
        
        // Calculate how many full intervals have passed
        long completedIntervals = elapsedSeconds / rampIntervalSeconds;
        
        // Calculate current concurrency based on steps
        long currentConcurrency = startingConcurrency + (completedIntervals * rampStep);
        
        // Cap at maximum
        return (int) Math.min(currentConcurrency, maxConcurrency);
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
        return String.format("Step ramp from %d to %d users, adding %d users every %d seconds",
            startingConcurrency, maxConcurrency, rampStep, rampIntervalSeconds);
    }
    
    public int getRampStep() {
        return rampStep;
    }
    
    public long getRampIntervalSeconds() {
        return rampIntervalSeconds;
    }
    
    /**
     * Calculate the total time needed to reach maximum concurrency.
     * 
     * @return estimated ramp-up duration in seconds
     */
    public long getEstimatedRampDuration() {
        int usersToAdd = maxConcurrency - startingConcurrency;
        long stepsNeeded = (long) Math.ceil((double) usersToAdd / rampStep);
        return stepsNeeded * rampIntervalSeconds;
    }
}
