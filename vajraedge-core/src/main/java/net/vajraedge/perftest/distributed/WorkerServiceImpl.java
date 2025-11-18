package net.vajraedge.perftest.distributed;

import io.grpc.stub.StreamObserver;
import net.vajraedge.perftest.proto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * gRPC service implementation for worker management.
 * Handles worker registration, heartbeats, task assignments, and metrics.
 */
@Service
public class WorkerServiceImpl extends WorkerServiceGrpc.WorkerServiceImplBase {
    private static final Logger log = LoggerFactory.getLogger(WorkerServiceImpl.class);
    
    private static final int DEFAULT_HEARTBEAT_INTERVAL_SECONDS = 5;
    private static final int DEFAULT_METRICS_INTERVAL_SECONDS = 1;
    
    private final WorkerManager workerManager;
    private final DistributedMetricsCollector metricsCollector;
    
    public WorkerServiceImpl(WorkerManager workerManager, 
                            DistributedMetricsCollector metricsCollector) {
        this.workerManager = workerManager;
        this.metricsCollector = metricsCollector;
    }
    
    @Override
    public void registerWorker(WorkerRegistrationRequest request,
                              StreamObserver<WorkerRegistrationResponse> responseObserver) {
        log.info("Worker registration request: id={}, hostname={}, capacity={}, tasks={}", 
                 request.getWorkerId(), request.getHostname(), 
                 request.getMaxCapacity(), request.getSupportedTaskTypesList());
        
        try {
            // Validate request
            if (request.getWorkerId().isEmpty()) {
                sendRegistrationError(responseObserver, "Worker ID cannot be empty");
                return;
            }
            
            if (request.getMaxCapacity() <= 0) {
                sendRegistrationError(responseObserver, "Max capacity must be positive");
                return;
            }
            
            if (request.getSupportedTaskTypesList().isEmpty()) {
                sendRegistrationError(responseObserver, 
                        "Worker must support at least one task type");
                return;
            }
            
            // Register worker
            Map<String, String> metadata = new HashMap<>(request.getMetadataMap());
            WorkerInfo workerInfo = workerManager.registerWorker(
                    request.getWorkerId(),
                    request.getHostname(),
                    request.getMaxCapacity(),
                    new ArrayList<>(request.getSupportedTaskTypesList()),
                    request.getVersion(),
                    metadata
            );
            
            // Send successful response
            WorkerRegistrationResponse response = WorkerRegistrationResponse.newBuilder()
                    .setAccepted(true)
                    .setMessage("Worker registered successfully")
                    .setHeartbeatIntervalSeconds(DEFAULT_HEARTBEAT_INTERVAL_SECONDS)
                    .setMetricsIntervalSeconds(DEFAULT_METRICS_INTERVAL_SECONDS)
                    .setAssignedWorkerId(workerInfo.getWorkerId())
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
            log.info("Worker {} registered successfully", request.getWorkerId());
            
        } catch (Exception e) {
            log.error("Error registering worker: {}", request.getWorkerId(), e);
            sendRegistrationError(responseObserver, "Internal error: " + e.getMessage());
        }
    }
    
    @Override
    public void heartbeat(HeartbeatRequest request,
                         StreamObserver<HeartbeatResponse> responseObserver) {
        log.debug("Heartbeat from worker: {}, load: {}", 
                  request.getWorkerId(), request.getCurrentLoad());
        
        try {
            boolean success = workerManager.updateHeartbeat(
                    request.getWorkerId(), 
                    request.getCurrentLoad()
            );
            
            HeartbeatResponse response = HeartbeatResponse.newBuilder()
                    .setHealthy(success)
                    .setMessage(success ? "OK" : "Worker not registered")
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            log.error("Error processing heartbeat from {}", request.getWorkerId(), e);
            HeartbeatResponse response = HeartbeatResponse.newBuilder()
                    .setHealthy(false)
                    .setMessage("Error: " + e.getMessage())
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void assignTask(TaskAssignment request,
                          StreamObserver<TaskAssignmentResponse> responseObserver) {
        log.info("Task assignment received for test: {}, type: {}, targetTps: {}", 
                 request.getTestId(), request.getTaskType(), request.getTargetTps());
        
        // Note: Actual task assignment is handled by TaskDistributor
        // This method is called by TaskDistributor when assigning tasks to workers
        
        try {
            TaskAssignmentResponse response = TaskAssignmentResponse.newBuilder()
                    .setAccepted(true)
                    .setMessage("Task assignment accepted")
                    .setEstimatedTaskCount(
                            (long) request.getTargetTps() * request.getDurationSeconds())
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            log.error("Error assigning task", e);
            TaskAssignmentResponse response = TaskAssignmentResponse.newBuilder()
                    .setAccepted(false)
                    .setMessage("Error: " + e.getMessage())
                    .setEstimatedTaskCount(0)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void stopTest(StopTestRequest request,
                        StreamObserver<StopTestResponse> responseObserver) {
        log.info("Stop test request for: {}, graceful: {}", 
                 request.getTestId(), request.getGraceful());
        
        try {
            StopTestResponse response = StopTestResponse.newBuilder()
                    .setStopped(true)
                    .setMessage("Test stopped")
                    .setTasksInterrupted(0) // Updated by worker
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            log.error("Error stopping test", e);
            StopTestResponse response = StopTestResponse.newBuilder()
                    .setStopped(false)
                    .setMessage("Error: " + e.getMessage())
                    .setTasksInterrupted(0)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public StreamObserver<WorkerMetrics> streamMetrics(
            StreamObserver<MetricsAcknowledgment> responseObserver) {
        log.debug("Metrics stream established");
        
        return new StreamObserver<>() {
            @Override
            public void onNext(WorkerMetrics metrics) {
                log.debug("Received metrics from worker: {}, test: {}, tps: {}", 
                         metrics.getWorkerId(), metrics.getTestId(), metrics.getCurrentTps());
                
                try {
                    // Store metrics
                    metricsCollector.recordWorkerMetrics(metrics);
                    
                    // Send acknowledgment
                    MetricsAcknowledgment ack = MetricsAcknowledgment.newBuilder()
                            .setReceived(true)
                            .setMessage("Metrics received")
                            .build();
                    responseObserver.onNext(ack);
                    
                } catch (Exception e) {
                    log.error("Error processing metrics from {}", metrics.getWorkerId(), e);
                }
            }
            
            @Override
            public void onError(Throwable t) {
                log.error("Error in metrics stream", t);
            }
            
            @Override
            public void onCompleted() {
                log.debug("Metrics stream completed");
                responseObserver.onCompleted();
            }
        };
    }
    
    private void sendRegistrationError(StreamObserver<WorkerRegistrationResponse> responseObserver,
                                      String errorMessage) {
        WorkerRegistrationResponse response = WorkerRegistrationResponse.newBuilder()
                .setAccepted(false)
                .setMessage(errorMessage)
                .setHeartbeatIntervalSeconds(DEFAULT_HEARTBEAT_INTERVAL_SECONDS)
                .setMetricsIntervalSeconds(DEFAULT_METRICS_INTERVAL_SECONDS)
                .setAssignedWorkerId("")
                .build();
        
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
