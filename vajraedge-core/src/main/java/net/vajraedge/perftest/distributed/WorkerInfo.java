package net.vajraedge.perftest.distributed;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a connected worker node with its capabilities and current state.
 */
public class WorkerInfo {
    private final String workerId;
    private final String hostname;
    private final int maxCapacity;
    private final List<String> supportedTaskTypes;
    private final String version;
    private final Map<String, String> metadata;
    
    private final AtomicInteger currentLoad;
    private volatile Instant lastHeartbeat;
    private volatile WorkerHealthStatus healthStatus;
    private volatile Instant registeredAt;
    
    public WorkerInfo(String workerId, String hostname, int maxCapacity,
                      List<String> supportedTaskTypes, String version,
                      Map<String, String> metadata) {
        this.workerId = workerId;
        this.hostname = hostname;
        this.maxCapacity = maxCapacity;
        this.supportedTaskTypes = List.copyOf(supportedTaskTypes);
        this.version = version;
        this.metadata = Map.copyOf(metadata);
        this.currentLoad = new AtomicInteger(0);
        this.lastHeartbeat = Instant.now();
        this.healthStatus = WorkerHealthStatus.HEALTHY;
        this.registeredAt = Instant.now();
    }
    
    // Getters
    public String getWorkerId() {
        return workerId;
    }
    
    public String getHostname() {
        return hostname;
    }
    
    public int getMaxCapacity() {
        return maxCapacity;
    }
    
    public List<String> getSupportedTaskTypes() {
        return supportedTaskTypes;
    }
    
    public String getVersion() {
        return version;
    }
    
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public int getCurrentLoad() {
        return currentLoad.get();
    }
    
    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }
    
    public WorkerHealthStatus getHealthStatus() {
        return healthStatus;
    }
    
    public Instant getRegisteredAt() {
        return registeredAt;
    }
    
    // State management
    public void updateHeartbeat(int currentLoadValue) {
        this.lastHeartbeat = Instant.now();
        this.currentLoad.set(currentLoadValue);
        
        // Update health status based on load
        if (currentLoadValue > maxCapacity * 1.2) {
            this.healthStatus = WorkerHealthStatus.OVERLOADED;
        } else if (currentLoadValue > maxCapacity) {
            this.healthStatus = WorkerHealthStatus.AT_CAPACITY;
        } else {
            this.healthStatus = WorkerHealthStatus.HEALTHY;
        }
    }
    
    public void markUnhealthy() {
        this.healthStatus = WorkerHealthStatus.UNHEALTHY;
    }
    
    public void markHealthy() {
        this.healthStatus = WorkerHealthStatus.HEALTHY;
    }
    
    public boolean isHealthy() {
        return healthStatus == WorkerHealthStatus.HEALTHY || 
               healthStatus == WorkerHealthStatus.AT_CAPACITY;
    }
    
    public boolean supportsTaskType(String taskType) {
        return supportedTaskTypes.contains(taskType);
    }
    
    public double getLoadPercentage() {
        return maxCapacity > 0 ? (double) currentLoad.get() / maxCapacity * 100.0 : 0.0;
    }
    
    public int getAvailableCapacity() {
        return Math.max(0, maxCapacity - currentLoad.get());
    }
    
    public boolean hasCapacity() {
        return currentLoad.get() < maxCapacity;
    }
    
    @Override
    public String toString() {
        return "WorkerInfo{" +
                "workerId='" + workerId + '\'' +
                ", hostname='" + hostname + '\'' +
                ", maxCapacity=" + maxCapacity +
                ", currentLoad=" + currentLoad.get() +
                ", loadPercentage=" + String.format("%.1f%%", getLoadPercentage()) +
                ", healthStatus=" + healthStatus +
                ", supportedTaskTypes=" + supportedTaskTypes +
                ", version='" + version + '\'' +
                '}';
    }
    
    public enum WorkerHealthStatus {
        HEALTHY,
        AT_CAPACITY,
        OVERLOADED,
        UNHEALTHY,
        DISCONNECTED
    }
}
