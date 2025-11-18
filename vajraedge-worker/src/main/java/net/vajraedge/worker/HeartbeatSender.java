package net.vajraedge.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Periodically sends heartbeat messages to the controller.
 * Keeps the worker registration alive and reports current load.
 *
 * @since 2.0.0
 */
public class HeartbeatSender {
    
    private static final Logger log = LoggerFactory.getLogger(HeartbeatSender.class);
    
    private final String workerId;
    private final GrpcClient grpcClient;
    private final TaskExecutorService taskExecutor;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running;
    
    private static final long DEFAULT_HEARTBEAT_INTERVAL_SECONDS = 5;
    
    /**
     * Create a new heartbeat sender.
     *
     * @param workerId Unique worker identifier
     * @param grpcClient gRPC client for sending heartbeats
     * @param taskExecutor Task executor to get current load from
     */
    public HeartbeatSender(String workerId, GrpcClient grpcClient, TaskExecutorService taskExecutor) {
        this.workerId = workerId;
        this.grpcClient = grpcClient;
        this.taskExecutor = taskExecutor;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "heartbeat-sender");
            t.setDaemon(true);
            return t;
        });
        this.running = new AtomicBoolean(false);
    }
    
    /**
     * Start sending periodic heartbeats.
     */
    public void start() {
        start(DEFAULT_HEARTBEAT_INTERVAL_SECONDS);
    }
    
    /**
     * Start sending periodic heartbeats with custom interval.
     *
     * @param intervalSeconds Heartbeat interval in seconds
     */
    public void start(long intervalSeconds) {
        if (running.getAndSet(true)) {
            log.warn("Heartbeat sender already running");
            return;
        }
        
        log.info("Starting heartbeat sender: interval={}s", intervalSeconds);
        
        scheduler.scheduleAtFixedRate(
            this::sendHeartbeat,
            0,
            intervalSeconds,
            TimeUnit.SECONDS
        );
    }
    
    /**
     * Stop sending heartbeats.
     */
    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        
        log.info("Stopping heartbeat sender");
        
        try {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
            log.warn("Interrupted while stopping heartbeat sender", e);
        }
    }
    
    /**
     * Send a heartbeat to the controller.
     */
    private void sendHeartbeat() {
        try {
            // Get current load from executor
            TaskExecutorService.ExecutorStats stats = taskExecutor.getStats();
            int currentLoad = stats.activeTasks();
            
            // Send heartbeat
            grpcClient.sendHeartbeat(workerId, currentLoad);
            
            log.trace("Heartbeat sent: workerId={}, load={}", workerId, currentLoad);
            
        } catch (Exception e) {
            log.error("Failed to send heartbeat", e);
        }
    }
    
    /**
     * Check if heartbeat sender is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running.get();
    }
}
