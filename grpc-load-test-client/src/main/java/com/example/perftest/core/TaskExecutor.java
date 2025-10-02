package com.example.perftest.core;

import java.util.concurrent.CompletableFuture;

/**
 * Executes tasks with concurrency control.
 * 
 * Single Responsibility: Task execution management
 */
public interface TaskExecutor extends AutoCloseable {
    
    /**
     * Submit a task for execution.
     * May block if max concurrency limit is reached.
     * 
     * @param task the task to execute
     * @return future that completes when task finishes
     */
    CompletableFuture<TaskResult> submit(Task task);
    
    /**
     * Try to submit a task without blocking.
     * 
     * @param task the task to execute
     * @return future if submitted, null if executor is saturated
     */
    CompletableFuture<TaskResult> trySubmit(Task task);
    
    /**
     * Get current number of active tasks.
     */
    int getActiveTasks();
    
    /**
     * Get total number of submitted tasks.
     */
    long getSubmittedTasks();
    
    /**
     * Get total number of completed tasks.
     */
    long getCompletedTasks();
    
    /**
     * Close and cleanup resources.
     */
    @Override
    void close();
}
