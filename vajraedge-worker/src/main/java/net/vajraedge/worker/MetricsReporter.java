package net.vajraedge.worker;

import net.vajraedge.sdk.metrics.MetricsSnapshot;
import net.vajraedge.sdk.metrics.PercentileStats;
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
    private volatile String testId;  // Made mutable to support multiple tests
    private final GrpcClient grpcClient;
    private final TaskExecutorService taskExecutor;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running;
    
    private static final long DEFAULT_REPORT_INTERVAL_SECONDS = 5;
    
    /**
     * Create a new metrics reporter.
     *
     * @param workerId Unique worker identifier
     * @param testId Test identifier
     * @param grpcClient gRPC client for sending metrics
     * @param taskExecutor Task executor to collect metrics from
     */
    public MetricsReporter(String workerId, String testId, GrpcClient grpcClient, TaskExecutorService taskExecutor) {
        this.workerId = workerId;
        this.testId = testId;
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
            // Get metrics snapshot from executor
            MetricsSnapshot snapshot = taskExecutor.getMetricsSnapshot();
            
            // Skip reporting if no tasks have been executed yet
            if (snapshot.getTotalTasks() == 0) {
                log.trace("No tasks executed yet, skipping metrics report");
                return;
            }
            
            // Get percentile stats
            PercentileStats percentiles = snapshot.getPercentiles();
            
            // Create metrics snapshot
            LocalWorkerMetrics metrics = new LocalWorkerMetrics(
                snapshot.getTotalTasks(),
                snapshot.getSuccessfulTasks(),
                snapshot.getFailedTasks(),
                taskExecutor.getStats().activeTasks(),  // Get active from ExecutorStats
                snapshot.getTps(),
                percentiles.getPercentile(0.5),  // p50
                percentiles.getPercentile(0.95), // p95
                percentiles.getPercentile(0.99), // p99
                System.currentTimeMillis()
            );
            
            // Send to controller
            grpcClient.sendMetrics(workerId, testId, metrics);
            
            log.debug("Metrics reported: workerId={}, completed={}, failed={}, active={}, tps={}", 
                workerId,
                metrics.completedTasks(), 
                metrics.failedTasks(), 
                metrics.activeTasks(),
                metrics.currentTps());
            
        } catch (Exception e) {
            log.error("Failed to report metrics", e);
        }
    }
    
    /**
     * Set the current test ID for metrics reporting.
     * This allows a single metrics reporter to report for different tests over time.
     *
     * @param testId Test identifier
     */
    public void setTestId(String testId) {
        String oldTestId = this.testId;
        this.testId = testId;
        log.info("Metrics reporter testId updated: {} -> {}", oldTestId, testId);
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
