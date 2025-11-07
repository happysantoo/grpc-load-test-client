package net.vajraedge.perftest.concurrency;

/**
 * Types of ramp strategies available for concurrency control.
 */
public enum RampStrategyType {
    /**
     * Linear ramp: steady increase from start to max over duration.
     */
    LINEAR,
    
    /**
     * Step ramp: discrete increments at regular intervals.
     */
    STEP
}
