package net.vajraedge.worker.tasks;

import net.vajraedge.sdk.SimpleTaskResult;
import net.vajraedge.sdk.Task;
import net.vajraedge.sdk.TaskResult;

/**
 * Simple sleep task for testing framework behavior without external dependencies.
 */
public class SleepTask implements Task {
    
    private final long sleepMillis;
    
    public SleepTask() {
        // Default sleep duration - can be overridden via environment variable
        String sleepDuration = System.getenv().getOrDefault("SLEEP_TASK_DURATION_MS", "100");
        this.sleepMillis = Long.parseLong(sleepDuration);
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
