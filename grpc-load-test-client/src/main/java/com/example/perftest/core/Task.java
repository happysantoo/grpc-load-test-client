package com.example.perftest.core;

/**
 * Represents a unit of work to be executed in a performance test.
 * This is the core abstraction that allows any workload (gRPC, HTTP, DB query, etc.)
 * to be executed and measured by the framework.
 * 
 * Implementations should be thread-safe if they will be executed concurrently.
 */
@FunctionalInterface
public interface Task {
    
    /**
     * Execute the task and return the result.
     * 
     * @return the result of the task execution
     * @throws Exception if the task execution fails
     */
    TaskResult execute() throws Exception;
}
