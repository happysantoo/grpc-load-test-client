package com.vajraedge.sdk;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Helper class for common task execution patterns.
 * Reduces boilerplate code in task implementations.
 * 
 * @since 1.1.0
 */
public final class TaskExecutionHelper {
    
    private TaskExecutionHelper() {
        // Utility class
    }
    
    /**
     * Execute a task with automatic timing and error handling.
     * Handles timing, error capture, and result creation.
     * 
     * <p>Example usage:
     * <pre>
     * {@code
     * @Override
     * public TaskResult execute() throws Exception {
     *     return TaskExecutionHelper.executeWithTiming(() -> {
     *         // Your task logic here
     *         performWork();
     *         return SimpleTaskResult.success(getTaskId(), 0, responseSize);
     *     });
     * }
     * }
     * </pre>
     *
     * @param taskLogic The task logic to execute
     * @return TaskResult with timing information
     */
    public static TaskResult executeWithTiming(Callable<TaskResult> taskLogic) {
        long taskId = Thread.currentThread().threadId();
        long startTime = System.nanoTime();
        
        try {
            TaskResult result = taskLogic.call();
            
            // If result doesn't have latency set, set it
            if (result.getLatencyNanos() == 0) {
                long latency = System.nanoTime() - startTime;
                if (result instanceof SimpleTaskResult) {
                    return result; // Already has timing
                }
                // Wrap with timing
                return result.isSuccess() 
                    ? SimpleTaskResult.success(taskId, latency, result.getResponseSize(), result.getMetadata())
                    : SimpleTaskResult.failure(taskId, latency, result.getErrorMessage(), result.getMetadata());
            }
            
            return result;
            
        } catch (Exception e) {
            long latency = System.nanoTime() - startTime;
            return SimpleTaskResult.failure(taskId, latency, 
                "Task execution failed: " + e.getMessage(),
                Map.of("error", e.getClass().getSimpleName()));
        }
    }
    
    /**
     * Execute a task with automatic timing, returning success with optional size.
     * Use this for simple tasks that always succeed.
     * 
     * @param taskLogic The task logic to execute (should not return anything)
     * @param responseSize Optional response size in bytes
     * @return Success TaskResult with timing
     */
    public static TaskResult executeAndSucceed(Runnable taskLogic, int responseSize) {
        long taskId = Thread.currentThread().threadId();
        long startTime = System.nanoTime();
        
        try {
            taskLogic.run();
            long latency = System.nanoTime() - startTime;
            return SimpleTaskResult.success(taskId, latency, responseSize);
            
        } catch (Exception e) {
            long latency = System.nanoTime() - startTime;
            return SimpleTaskResult.failure(taskId, latency, e.getMessage());
        }
    }
    
    /**
     * Get current task ID from thread.
     * 
     * @return Current thread ID as task ID
     */
    public static long getCurrentTaskId() {
        return Thread.currentThread().threadId();
    }
    
    /**
     * Create a success result with current timing.
     * 
     * @param startTimeNanos The start time from System.nanoTime()
     * @param responseSize Response size in bytes
     * @param metadata Optional metadata
     * @return Success TaskResult
     */
    public static TaskResult createSuccessResult(long startTimeNanos, int responseSize, Object metadata) {
        long taskId = getCurrentTaskId();
        long latency = System.nanoTime() - startTimeNanos;
        return SimpleTaskResult.success(taskId, latency, responseSize, metadata);
    }
    
    /**
     * Create a success result with current timing.
     * 
     * @param startTimeNanos The start time from System.nanoTime()
     * @param responseSize Response size in bytes
     * @return Success TaskResult
     */
    public static TaskResult createSuccessResult(long startTimeNanos, int responseSize) {
        return createSuccessResult(startTimeNanos, responseSize, null);
    }
    
    /**
     * Create a success result with current timing.
     * 
     * @param startTimeNanos The start time from System.nanoTime()
     * @return Success TaskResult
     */
    public static TaskResult createSuccessResult(long startTimeNanos) {
        return createSuccessResult(startTimeNanos, 0, null);
    }
    
    /**
     * Create a failure result with current timing.
     * 
     * @param startTimeNanos The start time from System.nanoTime()
     * @param errorMessage Error message
     * @param metadata Optional metadata
     * @return Failure TaskResult
     */
    public static TaskResult createFailureResult(long startTimeNanos, String errorMessage, Object metadata) {
        long taskId = getCurrentTaskId();
        long latency = System.nanoTime() - startTimeNanos;
        return SimpleTaskResult.failure(taskId, latency, errorMessage, metadata);
    }
    
    /**
     * Create a failure result with current timing.
     * 
     * @param startTimeNanos The start time from System.nanoTime()
     * @param errorMessage Error message
     * @return Failure TaskResult
     */
    public static TaskResult createFailureResult(long startTimeNanos, String errorMessage) {
        return createFailureResult(startTimeNanos, errorMessage, null);
    }
}
