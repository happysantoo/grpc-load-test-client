package net.vajraedge.worker;

/**
 * Snapshot of local worker metrics at a point in time.
 * 
 * @param completedTasks Total completed tasks
 * @param successfulTasks Successfully completed tasks
 * @param failedTasks Total failed tasks
 * @param activeTasks Currently executing tasks
 * @param currentTps Current transactions per second
 * @param p50Latency 50th percentile latency (ms)
 * @param p95Latency 95th percentile latency (ms)
 * @param p99Latency 99th percentile latency (ms)
 * @param timestamp Metrics timestamp (epoch millis)
 * 
 * @since 2.0.0
 */
public record LocalWorkerMetrics(
    long completedTasks,
    long successfulTasks,
    long failedTasks,
    int activeTasks,
    double currentTps,
    double p50Latency,
    double p95Latency,
    double p99Latency,
    long timestamp
) {
    
    /**
     * Get total tasks processed.
     *
     * @return Total tasks (successful + failed)
     */
    public long totalTasks() {
        return successfulTasks + failedTasks;
    }
    
    /**
     * Get success rate.
     *
     * @return Success rate (0.0 to 1.0)
     */
    public double successRate() {
        long total = totalTasks();
        return total == 0 ? 0.0 : (double) successfulTasks / total;
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
