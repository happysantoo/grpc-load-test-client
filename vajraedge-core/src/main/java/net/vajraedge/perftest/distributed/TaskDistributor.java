package net.vajraedge.perftest.distributed;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import net.vajraedge.perftest.dto.DistributedTestRequest;
import net.vajraedge.perftest.proto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Distributes tasks to worker nodes based on capabilities, capacity, and load balancing.
 * Implements intelligent task distribution strategies for distributed testing.
 */
@Component
public class TaskDistributor {
    private static final Logger log = LoggerFactory.getLogger(TaskDistributor.class);
    
    private final WorkerManager workerManager;
    private final Map<String, List<ManagedChannel>> workerChannels = new ConcurrentHashMap<>();
    
    public TaskDistributor(WorkerManager workerManager) {
        this.workerManager = workerManager;
    }
    
    /**
     * Distribute a test across available workers.
     *
     * @param testId Test identifier
     * @param request Distributed test configuration
     * @return Distribution result with assigned workers
     */
    public DistributionResult distributeTest(String testId, DistributedTestRequest request) {
        log.info("Distributing test {} with targetTps={}, duration={}s", 
                 testId, request.getTargetTps(), request.getDurationSeconds());
        
        // Get task type from request
        String taskType = request.getTaskType();
        
        // Find workers that support this task type
        List<WorkerInfo> capableWorkers = workerManager.getWorkersForTaskType(taskType);
        
        if (capableWorkers.isEmpty()) {
            log.error("No workers available that support task type: {}", taskType);
            return new DistributionResult(false, "No capable workers available", Collections.emptyList());
        }
        
        log.info("Found {} capable workers for task type: {}", capableWorkers.size(), taskType);
        
        // Calculate TPS distribution across workers
        Map<WorkerInfo, Integer> tpsDistribution = calculateTpsDistribution(capableWorkers, request.getTargetTps());
        
        // Assign tasks to workers
        List<WorkerAssignment> assignments = new ArrayList<>();
        List<CompletableFuture<WorkerAssignment>> futures = new ArrayList<>();
        
        for (Map.Entry<WorkerInfo, Integer> entry : tpsDistribution.entrySet()) {
            WorkerInfo worker = entry.getKey();
            int workerTps = entry.getValue();
            
            CompletableFuture<WorkerAssignment> future = CompletableFuture.supplyAsync(() -> 
                assignTaskToWorker(testId, worker, taskType, workerTps, request)
            );
            
            futures.add(future);
        }
        
        // Wait for all assignments to complete
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(10, TimeUnit.SECONDS);
            
            for (CompletableFuture<WorkerAssignment> future : futures) {
                WorkerAssignment assignment = future.get();
                if (assignment != null) {
                    assignments.add(assignment);
                }
            }
            
        } catch (Exception e) {
            log.error("Error during task distribution", e);
            return new DistributionResult(false, "Distribution failed: " + e.getMessage(), assignments);
        }
        
        if (assignments.isEmpty()) {
            return new DistributionResult(false, "No workers accepted task assignments", assignments);
        }
        
        log.info("Successfully distributed test {} to {} workers", testId, assignments.size());
        return new DistributionResult(true, "Test distributed successfully", assignments);
    }
    
    /**
     * Calculate TPS distribution across workers based on their capacity.
     * Uses weighted distribution based on available capacity.
     */
    private Map<WorkerInfo, Integer> calculateTpsDistribution(List<WorkerInfo> workers, int targetTps) {
        Map<WorkerInfo, Integer> distribution = new HashMap<>();
        
        // Calculate total available capacity
        int totalCapacity = workers.stream()
                .mapToInt(WorkerInfo::getAvailableCapacity)
                .sum();
        
        if (totalCapacity == 0) {
            log.warn("No available capacity across workers, distributing evenly");
            int tpsPerWorker = targetTps / workers.size();
            workers.forEach(w -> distribution.put(w, tpsPerWorker));
            return distribution;
        }
        
        // Distribute proportionally to available capacity
        int assignedTps = 0;
        for (int i = 0; i < workers.size(); i++) {
            WorkerInfo worker = workers.get(i);
            int workerTps;
            
            if (i == workers.size() - 1) {
                // Last worker gets remaining TPS to avoid rounding errors
                workerTps = targetTps - assignedTps;
            } else {
                // Proportional distribution
                double proportion = (double) worker.getAvailableCapacity() / totalCapacity;
                workerTps = (int) (targetTps * proportion);
            }
            
            distribution.put(worker, workerTps);
            assignedTps += workerTps;
            
            log.debug("Assigned {} TPS to worker {} (capacity: {}/{})", 
                     workerTps, worker.getWorkerId(), 
                     worker.getAvailableCapacity(), worker.getMaxCapacity());
        }
        
        return distribution;
    }
    
    /**
     * Assign a task to a specific worker via gRPC.
     */
    private WorkerAssignment assignTaskToWorker(String testId, WorkerInfo worker, 
                                                 String taskType, int targetTps, 
                                                 DistributedTestRequest request) {
        try {
            // Get or create gRPC channel to worker
            ManagedChannel channel = getOrCreateChannel(worker);
            WorkerServiceGrpc.WorkerServiceBlockingStub stub = WorkerServiceGrpc.newBlockingStub(channel);
            
            // Build task assignment
            TaskAssignment.Builder assignmentBuilder = TaskAssignment.newBuilder()
                    .setTestId(testId)
                    .setTaskType(taskType)
                    .setTargetTps(targetTps)
                    .setDurationSeconds(request.getDurationSeconds())
                    .setMaxConcurrency(request.getMaxConcurrency())
                    .setAssignedAtMs(System.currentTimeMillis());
            
            // Add ramp-up if specified
            if (request.getRampUpSeconds() != null && request.getRampUpSeconds() > 0) {
                assignmentBuilder.setRampUpSeconds(request.getRampUpSeconds());
            }
            
            // Add task parameters if available
            if (request.getTaskParameters() != null && !request.getTaskParameters().isEmpty()) {
                assignmentBuilder.putAllParameters(request.getTaskParameters());
            }
            
            TaskAssignment assignment = assignmentBuilder.build();
            
            // Send assignment to worker
            log.info("Assigning task to worker {}: testId={}, taskType={}, tps={}", 
                     worker.getWorkerId(), testId, taskType, targetTps);
            
            TaskAssignmentResponse response = stub.assignTask(assignment);
            
            if (response.getAccepted()) {
                log.info("Worker {} accepted assignment: {}", worker.getWorkerId(), response.getMessage());
                return new WorkerAssignment(
                        worker.getWorkerId(),
                        taskType,
                        targetTps,
                        response.getEstimatedTaskCount(),
                        true,
                        response.getMessage()
                );
            } else {
                log.warn("Worker {} rejected assignment: {}", worker.getWorkerId(), response.getMessage());
                return new WorkerAssignment(
                        worker.getWorkerId(),
                        taskType,
                        targetTps,
                        0,
                        false,
                        response.getMessage()
                );
            }
            
        } catch (StatusRuntimeException e) {
            log.error("gRPC error assigning task to worker {}: {}", worker.getWorkerId(), e.getStatus());
            return new WorkerAssignment(
                    worker.getWorkerId(),
                    taskType,
                    targetTps,
                    0,
                    false,
                    "gRPC error: " + e.getStatus()
            );
        } catch (Exception e) {
            log.error("Error assigning task to worker {}", worker.getWorkerId(), e);
            return new WorkerAssignment(
                    worker.getWorkerId(),
                    taskType,
                    targetTps,
                    0,
                    false,
                    "Error: " + e.getMessage()
            );
        }
    }
    
    /**
     * Get or create a gRPC channel to a worker.
     * Channels are cached per worker for reuse.
     */
    private ManagedChannel getOrCreateChannel(WorkerInfo worker) {
        String workerId = worker.getWorkerId();
        
        List<ManagedChannel> channels = workerChannels.computeIfAbsent(workerId, k -> new ArrayList<>());
        
        // Reuse existing channel if available
        for (ManagedChannel channel : channels) {
            if (!channel.isShutdown() && !channel.isTerminated()) {
                return channel;
            }
        }
        
        // Get worker address from metadata
        String target = worker.getMetadata().getOrDefault("worker.address", 
                                                          worker.getHostname() + ":9091");
        
        ManagedChannel channel = ManagedChannelBuilder
                .forTarget(target)
                .usePlaintext() // TODO: Add TLS support
                .build();
        
        channels.add(channel);
        
        log.debug("Created new gRPC channel to worker {} at {}", workerId, target);
        
        return channel;
    }
    
    /**
     * Stop a distributed test on all assigned workers.
     */
    public void stopDistributedTest(String testId, boolean graceful) {
        log.info("Stopping distributed test {} (graceful={})", testId, graceful);
        
        List<WorkerInfo> allWorkers = workerManager.getAllWorkers();
        
        List<CompletableFuture<Void>> futures = allWorkers.stream()
                .map(worker -> CompletableFuture.runAsync(() -> 
                    stopTestOnWorker(testId, worker, graceful)
                ))
                .collect(Collectors.toList());
        
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(10, TimeUnit.SECONDS);
            log.info("Stopped test {} on all workers", testId);
        } catch (Exception e) {
            log.error("Error stopping test on some workers", e);
        }
    }
    
    /**
     * Stop test on a specific worker.
     */
    private void stopTestOnWorker(String testId, WorkerInfo worker, boolean graceful) {
        try {
            ManagedChannel channel = getOrCreateChannel(worker);
            WorkerServiceGrpc.WorkerServiceBlockingStub stub = WorkerServiceGrpc.newBlockingStub(channel);
            
            StopTestRequest request = StopTestRequest.newBuilder()
                    .setTestId(testId)
                    .setGraceful(graceful)
                    .setTimeoutSeconds(30)
                    .build();
            
            StopTestResponse response = stub.stopTest(request);
            
            if (response.getStopped()) {
                log.info("Stopped test {} on worker {}: {}", testId, worker.getWorkerId(), response.getMessage());
            } else {
                log.warn("Failed to stop test {} on worker {}: {}", testId, worker.getWorkerId(), response.getMessage());
            }
            
        } catch (Exception e) {
            log.error("Error stopping test on worker {}", worker.getWorkerId(), e);
        }
    }
    
    /**
     * Shutdown all gRPC channels.
     */
    public void shutdown() {
        log.info("Shutting down TaskDistributor and all gRPC channels");
        
        workerChannels.values().stream()
                .flatMap(List::stream)
                .forEach(channel -> {
                    try {
                        channel.shutdown();
                        if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                            channel.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        channel.shutdownNow();
                        Thread.currentThread().interrupt();
                    }
                });
        
        workerChannels.clear();
    }
    
    /**
     * Result of test distribution across workers.
     */
    public record DistributionResult(
            boolean success,
            String message,
            List<WorkerAssignment> assignments
    ) {
        public int getTotalAssignedTps() {
            return assignments.stream()
                    .filter(WorkerAssignment::accepted)
                    .mapToInt(WorkerAssignment::assignedTps)
                    .sum();
        }
        
        public int getAcceptedWorkerCount() {
            return (int) assignments.stream()
                    .filter(WorkerAssignment::accepted)
                    .count();
        }
    }
    
    /**
     * Assignment of a task to a worker.
     */
    public record WorkerAssignment(
            String workerId,
            String taskType,
            int assignedTps,
            long estimatedTaskCount,
            boolean accepted,
            String message
    ) {}
}
