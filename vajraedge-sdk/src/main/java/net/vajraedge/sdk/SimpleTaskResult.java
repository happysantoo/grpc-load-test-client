package net.vajraedge.sdk;

/**
 * Simple immutable implementation of TaskResult.
 * Suitable for most use cases.
 */
public class SimpleTaskResult implements TaskResult {
    
    private final long taskId;
    private final long latencyNanos;
    private final boolean success;
    private final String errorMessage;
    private final int responseSize;
    private final Object metadata;
    
    private SimpleTaskResult(long taskId, long latencyNanos, boolean success, 
                             String errorMessage, int responseSize, Object metadata) {
        this.taskId = taskId;
        this.latencyNanos = latencyNanos;
        this.success = success;
        this.errorMessage = errorMessage;
        this.responseSize = responseSize;
        this.metadata = metadata;
    }
    
    public static SimpleTaskResult success(long taskId, long latencyNanos) {
        return new SimpleTaskResult(taskId, latencyNanos, true, null, 0, null);
    }
    
    public static SimpleTaskResult success(long taskId, long latencyNanos, int responseSize) {
        return new SimpleTaskResult(taskId, latencyNanos, true, null, responseSize, null);
    }
    
    public static SimpleTaskResult success(long taskId, long latencyNanos, int responseSize, Object metadata) {
        return new SimpleTaskResult(taskId, latencyNanos, true, null, responseSize, metadata);
    }
    
    public static SimpleTaskResult failure(long taskId, long latencyNanos, String errorMessage) {
        return new SimpleTaskResult(taskId, latencyNanos, false, errorMessage, 0, null);
    }
    
    public static SimpleTaskResult failure(long taskId, long latencyNanos, String errorMessage, Object metadata) {
        return new SimpleTaskResult(taskId, latencyNanos, false, errorMessage, 0, metadata);
    }
    
    @Override
    public long getTaskId() {
        return taskId;
    }
    
    @Override
    public long getLatencyNanos() {
        return latencyNanos;
    }
    
    @Override
    public boolean isSuccess() {
        return success;
    }
    
    @Override
    public String getErrorMessage() {
        return errorMessage;
    }
    
    @Override
    public int getResponseSize() {
        return responseSize;
    }
    
    @Override
    public Object getMetadata() {
        return metadata;
    }
    
    @Override
    public String toString() {
        if (success) {
            return String.format("TaskResult{id=%d, latency=%.2fms, success=true, size=%d}", 
                    taskId, getLatencyMs(), responseSize);
        } else {
            return String.format("TaskResult{id=%d, latency=%.2fms, success=false, error='%s'}", 
                    taskId, getLatencyMs(), errorMessage);
        }
    }
}
