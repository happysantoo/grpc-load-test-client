package net.vajraedge.worker;

import io.grpc.stub.StreamObserver;
import net.vajraedge.perftest.proto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC service implementation on the worker side.
 * Handles task assignments and test control commands from the controller.
 */
public class WorkerServiceImpl extends WorkerServiceGrpc.WorkerServiceImplBase {
    private static final Logger log = LoggerFactory.getLogger(WorkerServiceImpl.class);
    
    private final TaskAssignmentHandler assignmentHandler;
    
    public WorkerServiceImpl(TaskAssignmentHandler assignmentHandler) {
        this.assignmentHandler = assignmentHandler;
    }
    
    @Override
    public void assignTask(TaskAssignment request,
                          StreamObserver<TaskAssignmentResponse> responseObserver) {
        log.info("Received task assignment: testId={}, taskType={}, targetTps={}, duration={}s",
                request.getTestId(), request.getTaskType(), 
                request.getTargetTps(), request.getDurationSeconds());
        
        try {
            // Delegate to assignment handler
            TaskAssignmentResponse response = assignmentHandler.handleAssignment(request);
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
            if (response.getAccepted()) {
                log.info("Task assignment accepted: testId={}", request.getTestId());
            } else {
                log.warn("Task assignment rejected: testId={}, reason={}", 
                        request.getTestId(), response.getMessage());
            }
            
        } catch (Exception e) {
            log.error("Error processing task assignment", e);
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
        log.info("Received stop test request: testId={}, graceful={}", 
                request.getTestId(), request.getGraceful());
        
        try {
            boolean stopped = assignmentHandler.stopTest(request.getTestId(), request.getGraceful());
            
            StopTestResponse response = StopTestResponse.newBuilder()
                    .setStopped(stopped)
                    .setMessage(stopped ? "Test stopped" : "Test not found")
                    .setTasksInterrupted(0) // TODO: Track interrupted tasks
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
    
    // Note: Worker registration, heartbeat, and metrics streaming are handled
    // by the controller's gRPC server, not the worker's server.
    // The worker connects to the controller as a client for those operations.
}
