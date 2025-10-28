package com.vajraedge.perftest.concurrency;

/**
 * Defines the load test execution mode.
 * 
 * <p>CONCURRENCY_BASED: Control the number of concurrent virtual users,
 * measure the actual TPS the system achieves.</p>
 * 
 * <p>RATE_LIMITED: Concurrency-based with an optional maximum TPS limit
 * to protect downstream systems.</p>
 */
public enum LoadTestMode {
    /**
     * Pure concurrency-based mode.
     * Controls concurrent virtual users, measures actual TPS.
     */
    CONCURRENCY_BASED,
    
    /**
     * Hybrid mode: concurrency-based with rate limiting.
     * Uses concurrency control but caps maximum TPS.
     */
    RATE_LIMITED
}
