package net.vajraedge.worker;

/**
 * Snapshot of worker metrics at a point in time.
 * 
 * @param completedTasks Total completed tasks
 * @param failedTasks Total failed tasks
 * @param activeTasks Currently executing tasks
 * @param timestamp Metrics timestamp (epoch millis)
 * 
 * @since 2.0.0
 */
public record WorkerMetrics(
    long completedTasks,
    long failedTasks,
    int activeTasks,
    long timestamp
) {
    
    /**
     * Get total tasks processed.
     *
     * @return Total tasks (completed + failed)
     */
    public long totalTasks() {
        return completedTasks + failedTasks;
    }
    
    /**
     * Get success rate.
     *
     * @return Success rate (0.0 to 1.0)
     */
    public double successRate() {
        long total = totalTasks();
        return total == 0 ? 0.0 : (double) completedTasks / total;
    }
    
    /**
     * Get failure rate.
     *
     * @return Failure rate (0.0 to 1.0)
     */
    public double failureRate() {
        return 1.0 - successRate();
    }
}
