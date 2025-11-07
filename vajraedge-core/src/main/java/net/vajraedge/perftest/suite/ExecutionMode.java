package net.vajraedge.perftest.suite;

/**
 * Defines how scenarios within a test suite should be executed.
 * 
 * <p>SEQUENTIAL executes scenarios one after another, waiting for each to complete.
 * PARALLEL executes all scenarios simultaneously for maximum concurrency.
 * 
 * @author Santhosh Kuppusamy
 * @since 1.0
 */
public enum ExecutionMode {
    /**
     * Execute scenarios sequentially, one after another.
     * Each scenario waits for the previous one to complete.
     */
    SEQUENTIAL,
    
    /**
     * Execute all scenarios in parallel.
     * All scenarios start simultaneously.
     */
    PARALLEL
}
