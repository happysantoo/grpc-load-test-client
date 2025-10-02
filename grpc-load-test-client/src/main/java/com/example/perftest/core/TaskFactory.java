package com.example.perftest.core;

/**
 * Factory that creates Task instances for execution.
 * This allows dynamic task creation with different payloads/configurations.
 * 
 * Single Responsibility: Task creation logic
 */
@FunctionalInterface
public interface TaskFactory {
    
    /**
     * Create a new task instance.
     * 
     * @param taskId unique identifier for this task
     * @return a new Task ready for execution
     */
    Task createTask(long taskId);
}
