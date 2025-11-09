package net.vajraedge.perftest.distributed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages the pool of connected worker nodes.
 * Handles worker registration, health monitoring, and capability tracking.
 */
@Component
public class WorkerManager {
    private static final Logger log = LoggerFactory.getLogger(WorkerManager.class);
    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(30);
    
    private final Map<String, WorkerInfo> workers = new ConcurrentHashMap<>();
    
    /**
     * Register a new worker or update existing worker registration.
     *
     * @param workerId Worker identifier
     * @param hostname Worker hostname
     * @param maxCapacity Maximum concurrent tasks
     * @param supportedTaskTypes List of task types this worker supports
     * @param version Worker version
     * @param metadata Additional metadata
     * @return The registered WorkerInfo
     */
    public WorkerInfo registerWorker(String workerId, String hostname, int maxCapacity,
                                      List<String> supportedTaskTypes, String version,
                                      Map<String, String> metadata) {
        WorkerInfo existingWorker = workers.get(workerId);
        
        if (existingWorker != null) {
            log.info("Worker {} re-registered. Previous registration at: {}", 
                     workerId, existingWorker.getRegisteredAt());
        }
        
        WorkerInfo workerInfo = new WorkerInfo(workerId, hostname, maxCapacity,
                                               supportedTaskTypes, version, metadata);
        workers.put(workerId, workerInfo);
        
        log.info("Worker registered: {} (capacity: {}, tasks: {})", 
                 workerId, maxCapacity, supportedTaskTypes);
        
        return workerInfo;
    }
    
    /**
     * Update worker heartbeat and current load.
     *
     * @param workerId Worker identifier
     * @param currentLoad Current number of active tasks
     * @return true if heartbeat was successful, false if worker not found
     */
    public boolean updateHeartbeat(String workerId, int currentLoad) {
        WorkerInfo worker = workers.get(workerId);
        if (worker == null) {
            log.warn("Received heartbeat from unknown worker: {}", workerId);
            return false;
        }
        
        worker.updateHeartbeat(currentLoad);
        log.debug("Heartbeat from {}: load={}/{}, status={}", 
                  workerId, currentLoad, worker.getMaxCapacity(), worker.getHealthStatus());
        
        return true;
    }
    
    /**
     * Get worker by ID.
     *
     * @param workerId Worker identifier
     * @return WorkerInfo or null if not found
     */
    public WorkerInfo getWorker(String workerId) {
        return workers.get(workerId);
    }
    
    /**
     * Get all registered workers.
     *
     * @return List of all workers
     */
    public List<WorkerInfo> getAllWorkers() {
        return new ArrayList<>(workers.values());
    }
    
    /**
     * Get all healthy workers.
     *
     * @return List of healthy workers
     */
    public List<WorkerInfo> getHealthyWorkers() {
        return workers.values().stream()
                .filter(WorkerInfo::isHealthy)
                .collect(Collectors.toList());
    }
    
    /**
     * Get workers that support a specific task type.
     *
     * @param taskType Task type to search for
     * @return List of workers supporting this task type
     */
    public List<WorkerInfo> getWorkersForTaskType(String taskType) {
        return workers.values().stream()
                .filter(w -> w.supportsTaskType(taskType))
                .filter(WorkerInfo::isHealthy)
                .collect(Collectors.toList());
    }
    
    /**
     * Get workers with available capacity.
     *
     * @return List of workers that can accept more tasks
     */
    public List<WorkerInfo> getWorkersWithCapacity() {
        return workers.values().stream()
                .filter(WorkerInfo::isHealthy)
                .filter(WorkerInfo::hasCapacity)
                .collect(Collectors.toList());
    }
    
    /**
     * Find the best worker for a specific task type based on available capacity.
     *
     * @param taskType Task type
     * @return Best available worker or null if none available
     */
    public WorkerInfo findBestWorkerForTask(String taskType) {
        return workers.values().stream()
                .filter(w -> w.supportsTaskType(taskType))
                .filter(WorkerInfo::isHealthy)
                .filter(WorkerInfo::hasCapacity)
                .min(Comparator.comparingDouble(WorkerInfo::getLoadPercentage))
                .orElse(null);
    }
    
    /**
     * Unregister a worker.
     *
     * @param workerId Worker identifier
     * @return true if worker was removed, false if not found
     */
    public boolean unregisterWorker(String workerId) {
        WorkerInfo removed = workers.remove(workerId);
        if (removed != null) {
            log.info("Worker unregistered: {}", workerId);
            return true;
        }
        return false;
    }
    
    /**
     * Get total number of registered workers.
     *
     * @return Worker count
     */
    public int getWorkerCount() {
        return workers.size();
    }
    
    /**
     * Get total capacity across all healthy workers.
     *
     * @return Sum of max capacity
     */
    public int getTotalCapacity() {
        return workers.values().stream()
                .filter(WorkerInfo::isHealthy)
                .mapToInt(WorkerInfo::getMaxCapacity)
                .sum();
    }
    
    /**
     * Get total available capacity across all healthy workers.
     *
     * @return Sum of available capacity
     */
    public int getTotalAvailableCapacity() {
        return workers.values().stream()
                .filter(WorkerInfo::isHealthy)
                .mapToInt(WorkerInfo::getAvailableCapacity)
                .sum();
    }
    
    /**
     * Get statistics about the worker pool.
     *
     * @return Worker pool statistics
     */
    public WorkerPoolStats getPoolStats() {
        List<WorkerInfo> allWorkers = new ArrayList<>(workers.values());
        long healthyCount = allWorkers.stream().filter(WorkerInfo::isHealthy).count();
        int totalCapacity = allWorkers.stream()
                .filter(WorkerInfo::isHealthy)
                .mapToInt(WorkerInfo::getMaxCapacity)
                .sum();
        int totalLoad = allWorkers.stream()
                .filter(WorkerInfo::isHealthy)
                .mapToInt(WorkerInfo::getCurrentLoad)
                .sum();
        
        return new WorkerPoolStats(
                allWorkers.size(),
                (int) healthyCount,
                totalCapacity,
                totalLoad,
                totalCapacity > 0 ? (double) totalLoad / totalCapacity * 100.0 : 0.0
        );
    }
    
    /**
     * Periodic check for workers that have missed heartbeats.
     * Marks them as unhealthy if heartbeat timeout exceeded.
     */
    @Scheduled(fixedRate = 10000) // Check every 10 seconds
    public void checkWorkerHealth() {
        Instant now = Instant.now();
        workers.values().forEach(worker -> {
            Duration timeSinceHeartbeat = Duration.between(worker.getLastHeartbeat(), now);
            
            if (timeSinceHeartbeat.compareTo(HEARTBEAT_TIMEOUT) > 0) {
                if (worker.isHealthy()) {
                    worker.markUnhealthy();
                    log.warn("Worker {} marked unhealthy - no heartbeat for {}s", 
                             worker.getWorkerId(), timeSinceHeartbeat.toSeconds());
                }
            }
        });
    }
    
    /**
     * Statistics about the worker pool.
     */
    public record WorkerPoolStats(
            int totalWorkers,
            int healthyWorkers,
            int totalCapacity,
            int currentLoad,
            double loadPercentage
    ) {}
}
