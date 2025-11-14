package net.vajraedge.worker;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import net.vajraedge.perftest.proto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * gRPC client for communicating with the controller.
 * 
 * <p>Handles:
 * <ul>
 *   <li>Worker registration/unregistration</li>
 *   <li>Task assignment receiving</li>
 *   <li>Metrics reporting</li>
 *   <li>Heartbeat messages</li>
 * </ul>
 *
 * @since 2.0.0
 */
public class GrpcClient {
    
    private static final Logger log = LoggerFactory.getLogger(GrpcClient.class);
    
    private final String controllerAddress;
    private volatile ManagedChannel channel;
    private volatile WorkerServiceGrpc.WorkerServiceBlockingStub blockingStub;
    private volatile WorkerServiceGrpc.WorkerServiceStub asyncStub;
    private volatile StreamObserver<net.vajraedge.perftest.proto.WorkerMetrics> metricsStream;
    private volatile TaskAssignmentHandler assignmentHandler;
    private volatile boolean connected;
    
    /**
     * Create a new gRPC client.
     *
     * @param controllerAddress Controller address (host:port)
     */
    public GrpcClient(String controllerAddress) {
        this.controllerAddress = controllerAddress;
        this.connected = false;
    }
    
    /**
     * Set the task assignment handler.
     * Must be called before connecting to controller.
     *
     * @param handler Task assignment handler
     */
    public void setAssignmentHandler(TaskAssignmentHandler handler) {
        this.assignmentHandler = handler;
    }
    
    /**
     * Connect to the controller.
     *
     * @throws Exception if connection fails
     */
    public void connect() throws Exception {
        log.info("Connecting to controller: {}", controllerAddress);
        
        try {
            channel = ManagedChannelBuilder
                .forTarget(controllerAddress)
                .usePlaintext() // TODO: Add TLS support in production
                .build();
            
            blockingStub = WorkerServiceGrpc.newBlockingStub(channel);
            asyncStub = WorkerServiceGrpc.newStub(channel);
            
            connected = true;
            log.info("Connected to controller");
            
        } catch (Exception e) {
            log.error("Failed to connect to controller", e);
            throw e;
        }
    }
    
    /**
     * Disconnect from the controller.
     */
    public void disconnect() {
        if (!connected) {
            return;
        }
        
        log.info("Disconnecting from controller");
        
        try {
            // Close metrics stream if active
            if (metricsStream != null) {
                metricsStream.onCompleted();
                metricsStream = null;
            }
            
            // Shutdown channel
            if (channel != null) {
                channel.shutdown();
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Channel did not terminate gracefully, forcing shutdown");
                    channel.shutdownNow();
                }
            }
            
            connected = false;
            log.info("Disconnected from controller");
            
        } catch (InterruptedException e) {
            log.error("Interrupted while disconnecting", e);
            Thread.currentThread().interrupt();
            if (channel != null) {
                channel.shutdownNow();
            }
        }
    }
    
    /**
     * Register worker with the controller.
     *
     * @param workerId Worker identifier
     * @param capabilities Available task types
     * @param maxConcurrency Maximum concurrent tasks
     * @param workerPort Worker's gRPC server port
     * @throws Exception if registration fails
     */
    public void registerWorker(String workerId, List<String> capabilities, int maxConcurrency, int workerPort) 
            throws Exception {
        log.info("Registering worker: id={}, capabilities={}, maxConcurrency={}, port={}", 
            workerId, capabilities, maxConcurrency, workerPort);
        
        try {
            String hostname = java.net.InetAddress.getLocalHost().getHostName();
            
            // Build metadata with worker's gRPC address
            Map<String, String> metadata = new HashMap<>();
            metadata.put("worker.host", hostname);
            metadata.put("worker.port", String.valueOf(workerPort));
            metadata.put("worker.address", "127.0.0.1:" + workerPort); // Use 127.0.0.1 to force IPv4
            
            WorkerRegistrationRequest request = WorkerRegistrationRequest.newBuilder()
                .setWorkerId(workerId)
                .setHostname(hostname)
                .setMaxCapacity(maxConcurrency)
                .addAllSupportedTaskTypes(capabilities)
                .setVersion("1.0.0")
                .putAllMetadata(metadata)
                .build();
            
            WorkerRegistrationResponse response = blockingStub.registerWorker(request);
            
            if (!response.getAccepted()) {
                throw new Exception("Worker registration rejected: " + response.getMessage());
            }
            
            log.info("Worker registered successfully: {}", response.getMessage());
            log.info("Heartbeat interval: {}s, Metrics interval: {}s",
                    response.getHeartbeatIntervalSeconds(),
                    response.getMetricsIntervalSeconds());
            
        } catch (StatusRuntimeException e) {
            log.error("gRPC error during registration", e);
            throw new Exception("Failed to register worker: " + e.getStatus(), e);
        }
    }
    
    /**
     * Unregister worker from the controller.
     *
     * @param workerId Worker identifier
     */
    public void unregisterWorker(String workerId) {
        log.info("Unregistering worker: {}", workerId);
        
        try {
            // Note: No explicit unregister in current proto
            // Worker is considered disconnected when heartbeats stop
            log.info("Worker unregistration implicit (via heartbeat timeout)");
        } catch (Exception e) {
            log.error("Failed to unregister worker", e);
        }
    }
    
    /**
     * Send metrics to controller.
     *
     * @param workerId Worker identifier
     * @param testId Test identifier
     * @param metrics Worker metrics
     */
    public void sendMetrics(String workerId, String testId, LocalWorkerMetrics metrics) {
        if (!connected) {
            log.warn("Cannot send metrics: not connected to controller");
            return;
        }
        
        log.debug("Sending metrics: workerId={}, testId={}, total={}, success={}, tps={}", 
            workerId, testId, metrics.completedTasks(), 
            metrics.successfulTasks(), metrics.currentTps());
        
        try {
            // Initialize metrics stream if not exists
            if (metricsStream == null) {
                initializeMetricsStream();
            }
            
            // Build protobuf metrics
            net.vajraedge.perftest.proto.WorkerMetrics protoMetrics = 
                    net.vajraedge.perftest.proto.WorkerMetrics.newBuilder()
                .setWorkerId(workerId)
                .setTestId(testId)
                .setTimestampMs(System.currentTimeMillis())
                .setTotalRequests(metrics.completedTasks())
                .setSuccessfulRequests(metrics.successfulTasks())
                .setFailedRequests(metrics.failedTasks())
                .setCurrentTps(metrics.currentTps())
                .setActiveTasks(metrics.activeTasks())
                .setLatency(LatencyStatistics.newBuilder()
                        .setP50Ms(metrics.p50Latency())
                        .setP95Ms(metrics.p95Latency())
                        .setP99Ms(metrics.p99Latency())
                        .build())
                .build();
            
            metricsStream.onNext(protoMetrics);
            
        } catch (Exception e) {
            log.error("Failed to send metrics", e);
            // Reset stream on error
            metricsStream = null;
        }
    }
    
    /**
     * Send heartbeat to controller.
     *
     * @param workerId Worker identifier
     * @param currentLoad Current number of active tasks
     */
    public void sendHeartbeat(String workerId, int currentLoad) {
        if (!connected) {
            log.warn("Cannot send heartbeat: not connected to controller");
            return;
        }
        
        log.trace("Sending heartbeat: workerId={}, load={}", workerId, currentLoad);
        
        try {
            HeartbeatRequest request = HeartbeatRequest.newBuilder()
                .setWorkerId(workerId)
                .setCurrentLoad(currentLoad)
                .setTimestampMs(System.currentTimeMillis())
                .setStatus(WorkerStatus.WORKER_STATUS_RUNNING)
                .build();
            
            HeartbeatResponse response = blockingStub.heartbeat(request);
            
            if (!response.getHealthy()) {
                log.warn("Controller marked worker as unhealthy: {}", response.getMessage());
            }
            
        } catch (StatusRuntimeException e) {
            log.error("gRPC error during heartbeat", e);
        }
    }
    
    /**
     * Initialize metrics streaming to controller.
     */
    private void initializeMetricsStream() {
        log.info("Initializing metrics stream");
        
        metricsStream = asyncStub.streamMetrics(new StreamObserver<MetricsAcknowledgment>() {
            @Override
            public void onNext(MetricsAcknowledgment ack) {
                if (ack.getReceived()) {
                    log.trace("Metrics acknowledged: {}", ack.getMessage());
                } else {
                    log.warn("Metrics not acknowledged: {}", ack.getMessage());
                }
            }
            
            @Override
            public void onError(Throwable t) {
                log.debug("Metrics stream error (expected when no active test): {}", t.getMessage());
                metricsStream = null;
            }
            
            @Override
            public void onCompleted() {
                log.info("Metrics stream completed");
                metricsStream = null;
            }
        });
    }
    
    /**
     * Check if connected to controller.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return connected;
    }
}
