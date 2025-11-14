package net.vajraedge.worker.tasks;

import net.vajraedge.sdk.SimpleTaskResult;
import net.vajraedge.sdk.Task;
import net.vajraedge.sdk.TaskResult;

import java.util.Map;

/**
 * Simple sleep task for testing framework behavior without external dependencies.
 * Supports configurable sleep duration via parameters.
 */
public class SleepTask implements Task {
    
    private final long sleepMillis;
    
    /**
     * Constructor with parameters from task assignment.
     * Falls back to environment variable and default if parameter not provided.
     */
    public SleepTask(Map<String, String> parameters) {
        if (parameters == null) {
            parameters = Map.of();
        }
        
        // Duration: parameter > env var > default
        String durationStr = parameters.getOrDefault("duration",
                System.getenv().getOrDefault("SLEEP_TASK_DURATION_MS", "100"));
        this.sleepMillis = Long.parseLong(durationStr);
    }
    
    /**
     * Default constructor for backwards compatibility.
     */
    public SleepTask() {
        this(Map.of());
    }
    
    @Override
    public TaskResult execute() throws Exception {
        long startTimeNanos = System.nanoTime();
        long taskId = Thread.currentThread().threadId();
        
        try {
            Thread.sleep(sleepMillis);
            long latencyNanos = System.nanoTime() - startTimeNanos;
            return SimpleTaskResult.success(taskId, latencyNanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long latencyNanos = System.nanoTime() - startTimeNanos;
            return SimpleTaskResult.failure(taskId, latencyNanos, "Interrupted");
        }
    }
}
