package com.example.perftest.core;

/**
 * Represents the result of a task execution.
 * Contains timing information and success/failure status.
 * This is protocol-agnostic and can represent any type of workload result.
 */
public interface TaskResult {
    
    /**
     * Get the unique identifier for this task execution.
     */
    long getTaskId();
    
    /**
     * Get the execution latency in nanoseconds.
     */
    long getLatencyNanos();
    
    /**
     * Get the execution latency in milliseconds.
     */
    default double getLatencyMs() {
        return getLatencyNanos() / 1_000_000.0;
    }
    
    /**
     * Check if the task executed successfully.
     */
    boolean isSuccess();
    
    /**
     * Get the error message if the task failed.
     * Returns null if the task was successful.
     */
    String getErrorMessage();
    
    /**
     * Get the size of the response/result in bytes (if applicable).
     * Returns 0 if not applicable.
     */
    default int getResponseSize() {
        return 0;
    }
    
    /**
     * Get any additional metadata about the task result.
     * Can be used for protocol-specific information.
     */
    default Object getMetadata() {
        return null;
    }
}
