package net.vajraedge.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Periodically reports worker metrics to the controller.
 * 
 * <p>Reports include:
 * <ul>
 *   <li>Completed task count</li>
 *   <li>Failed task count</li>
 *   <li>Active task count</li>
 *   <li>Latency statistics</li>
 * </ul>
 *
 * @since 2.0.0
 */
public class MetricsReporter {
    
    private static final Logger log = LoggerFactory.getLogger(MetricsReporter.class);
    
    private final String workerId;
    private final GrpcClient grpcClient;
    private final TaskExecutorService taskExecutor;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running;
    
    private static final long DEFAULT_REPORT_INTERVAL_SECONDS = 5;
    
    /**
     * Create a new metrics reporter.
     *
     * @param workerId Unique worker identifier
     * @param grpcClient gRPC client for sending metrics
     * @param taskExecutor Task executor to collect metrics from
     */
    public MetricsReporter(String workerId, GrpcClient grpcClient, TaskExecutorService taskExecutor) {
        this.workerId = workerId;
        this.grpcClient = grpcClient;
        this.taskExecutor = taskExecutor;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "metrics-reporter");
            t.setDaemon(true);
            return t;
        });
        this.running = new AtomicBoolean(false);
    }
    
    /**
     * Start periodic metrics reporting.
     */
    public void start() {
        start(DEFAULT_REPORT_INTERVAL_SECONDS);
    }
    
    /**
     * Start periodic metrics reporting with custom interval.
     *
     * @param intervalSeconds Reporting interval in seconds
     */
    public void start(long intervalSeconds) {
        if (running.getAndSet(true)) {
            log.warn("Metrics reporter already running");
            return;
        }
        
        log.info("Starting metrics reporter: interval={}s", intervalSeconds);
        
        scheduler.scheduleAtFixedRate(
            this::reportMetrics,
            0,
            intervalSeconds,
            TimeUnit.SECONDS
        );
    }
    
    /**
     * Stop metrics reporting.
     */
    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        
        log.info("Stopping metrics reporter");
        
        try {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
            log.warn("Interrupted while stopping metrics reporter", e);
        }
    }
    
    /**
     * Collect and report current metrics.
     */
    private void reportMetrics() {
        try {
            // Get current stats from executor
            TaskExecutorService.ExecutorStats stats = taskExecutor.getStats();
            
            // Create metrics snapshot
            WorkerMetrics metrics = new WorkerMetrics(
                stats.completedTasks(),
                stats.failedTasks(),
                stats.activeTasks(),
                System.currentTimeMillis()
            );
            
            // Send to controller
            grpcClient.sendMetrics(workerId, metrics);
            
            log.debug("Metrics reported: workerId={}, completed={}, failed={}, active={}", 
                workerId,
                metrics.completedTasks(), 
                metrics.failedTasks(), 
                metrics.activeTasks());
            
        } catch (Exception e) {
            log.error("Failed to report metrics", e);
        }
    }
    
    /**
     * Check if reporter is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running.get();
    }
}
