package com.vajraedge.perftest.concurrency;

/**
 * Strategy interface for ramping up/down virtual user concurrency.
 * 
 * <p>Implementations define how the number of concurrent virtual users
 * changes over time during a load test.</p>
 * 
 * <p>This follows the Strategy pattern, allowing different ramp-up
 * behaviors to be plugged in without changing the execution engine.</p>
 */
public interface RampStrategy {
    
    /**
     * Calculate the target number of concurrent virtual users
     * at a given point in the test.
     * 
     * @param elapsedSeconds seconds elapsed since test start
     * @return target number of concurrent virtual users (>= 0)
     */
    int getTargetConcurrency(long elapsedSeconds);
    
    /**
     * Get the starting concurrency level.
     * 
     * @return initial number of concurrent virtual users
     */
    int getStartingConcurrency();
    
    /**
     * Get the maximum concurrency level.
     * 
     * @return maximum number of concurrent virtual users
     */
    int getMaxConcurrency();
    
    /**
     * Get a human-readable description of this ramp strategy.
     * 
     * @return strategy description for logging/display
     */
    String getDescription();
}
