package net.vajraedge.perftest.concurrency;

/**
 * Step-based ramp-up strategy with optional sustain phase.
 * 
 * <p>Increases concurrency in discrete steps at regular intervals,
 * then maintains maximum concurrency for a sustain period.</p>
 * 
 * <p>Example: Start with 10 users, add 10 users every 30 seconds up to 100 users,
 * then sustain at 100 users for 60 seconds.
 * Timeline:
 * <ul>
 *   <li>t=0s: 10 users (ramp phase)</li>
 *   <li>t=30s: 20 users (ramp phase)</li>
 *   <li>t=60s: 30 users (ramp phase)</li>
 *   <li>...</li>
 *   <li>t=270s: 100 users (max reached - sustain phase begins)</li>
 *   <li>t=271s-330s: 100 users (sustain phase continues)</li>
 *   <li>t=330s+: test completes or ramps down</li>
 * </ul>
 * </p>
 */
public class StepRampStrategy implements RampStrategy {
    
    private final int startingConcurrency;
    private final int rampStep;
    private final long rampIntervalSeconds;
    private final int maxConcurrency;
    private final long sustainDurationSeconds;
    
    /**
     * Create a step ramp strategy without sustain phase.
     * 
     * @param startingConcurrency initial number of virtual users (must be > 0)
     * @param rampStep number of users to add at each interval (must be > 0)
     * @param rampIntervalSeconds time between steps in seconds (must be > 0)
     * @param maxConcurrency maximum number of virtual users (must be >= startingConcurrency)
     * @throws IllegalArgumentException if parameters are invalid
     */
    public StepRampStrategy(int startingConcurrency, int rampStep, long rampIntervalSeconds, int maxConcurrency) {
        this(startingConcurrency, rampStep, rampIntervalSeconds, maxConcurrency, 0L);
    }
    
    /**
     * Create a step ramp strategy with sustain phase.
     * 
     * @param startingConcurrency initial number of virtual users (must be > 0)
     * @param rampStep number of users to add at each interval (must be > 0)
     * @param rampIntervalSeconds time between steps in seconds (must be > 0)
     * @param maxConcurrency maximum number of virtual users (must be >= startingConcurrency)
     * @param sustainDurationSeconds duration to maintain max concurrency in seconds (>= 0)
     * @throws IllegalArgumentException if parameters are invalid
     */
    public StepRampStrategy(int startingConcurrency, int rampStep, long rampIntervalSeconds, 
                            int maxConcurrency, long sustainDurationSeconds) {
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
        if (sustainDurationSeconds < 0) {
            throw new IllegalArgumentException("Sustain duration cannot be negative");
        }
        
        this.startingConcurrency = startingConcurrency;
        this.rampStep = rampStep;
        this.rampIntervalSeconds = rampIntervalSeconds;
        this.maxConcurrency = maxConcurrency;
        this.sustainDurationSeconds = sustainDurationSeconds;
    }
    
    @Override
    public int getTargetConcurrency(long elapsedSeconds) {
        if (elapsedSeconds < 0) {
            return startingConcurrency;
        }
        
        // Calculate the ramp-up duration
        long rampUpDuration = getEstimatedRampDuration();
        
        // If we're still in the ramp-up phase
        if (elapsedSeconds < rampUpDuration) {
            // Calculate how many full intervals have passed
            long completedIntervals = elapsedSeconds / rampIntervalSeconds;
            
            // Calculate current concurrency based on steps
            long currentConcurrency = startingConcurrency + (completedIntervals * rampStep);
            
            // Cap at maximum
            return (int) Math.min(currentConcurrency, maxConcurrency);
        } else {
            // We've reached max concurrency - maintain it during sustain phase
            return maxConcurrency;
        }
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
        String baseDesc = String.format("Step ramp from %d to %d users, adding %d users every %d seconds",
            startingConcurrency, maxConcurrency, rampStep, rampIntervalSeconds);
        
        if (sustainDurationSeconds > 0) {
            return baseDesc + String.format(", then sustain at %d users for %d seconds",
                maxConcurrency, sustainDurationSeconds);
        }
        return baseDesc;
    }
    
    public int getRampStep() {
        return rampStep;
    }
    
    public long getRampIntervalSeconds() {
        return rampIntervalSeconds;
    }
    
    public long getSustainDurationSeconds() {
        return sustainDurationSeconds;
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
    
    /**
     * Calculate the total time for ramp-up + sustain phases.
     * 
     * @return total duration in seconds
     */
    public long getTotalPhaseDuration() {
        return getEstimatedRampDuration() + sustainDurationSeconds;
    }
}
