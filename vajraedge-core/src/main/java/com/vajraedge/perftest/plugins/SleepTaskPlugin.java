package com.vajraedge.perftest.plugins;

import com.vajraedge.sdk.SimpleTaskResult;
import com.vajraedge.sdk.TaskResult;
import com.vajraedge.sdk.TaskMetadata;
import com.vajraedge.sdk.TaskMetadata.ParameterDef;
import com.vajraedge.sdk.TaskPlugin;
import com.vajraedge.sdk.annotations.VajraTask;

import java.util.List;
import java.util.Map;

/**
 * Sleep task plugin that pauses execution for a specified duration.
 * Useful for simulating I/O operations and testing concurrency.
 *
 * @since 1.1.0
 */
@VajraTask(
    name = "SLEEP",
    displayName = "Sleep Task",
    description = "Pauses execution for specified milliseconds to simulate I/O operations",
    category = "SYSTEM",
    version = "1.0.0",
    author = "VajraEdge"
)
public class SleepTaskPlugin implements TaskPlugin {
    
    private int durationMs;
    
    public SleepTaskPlugin() {
        this.durationMs = 100; // Default
    }
    
    public SleepTaskPlugin(int durationMs) {
        this.durationMs = durationMs;
    }
    
    @Override
    public TaskMetadata getMetadata() {
        return TaskMetadata.builder()
            .name("SLEEP")
            .displayName("Sleep Task")
            .description("Pauses execution for specified milliseconds to simulate I/O operations")
            .category("SYSTEM")
            .parameters(List.of(
                ParameterDef.optionalInteger(
                    "duration",
                    100,
                    1,
                    60000,
                    "Sleep duration in milliseconds (1-60000)"
                )
            ))
            .metadata(Map.of(
                "purpose", "simulation",
                "blocking", "true"
            ))
            .build();
    }
    
    @Override
    public void validateParameters(Map<String, Object> parameters) {
        if (parameters.containsKey("duration")) {
            Object durationObj = parameters.get("duration");
            int duration;
            
            if (durationObj instanceof Integer) {
                duration = (Integer) durationObj;
            } else if (durationObj instanceof String) {
                try {
                    duration = Integer.parseInt((String) durationObj);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Duration must be a valid integer");
                }
            } else {
                throw new IllegalArgumentException("Duration must be an integer");
            }
            
            if (duration < 1 || duration > 60000) {
                throw new IllegalArgumentException("Duration must be between 1 and 60000 milliseconds");
            }
        }
    }
    
    @Override
    public void initialize(Map<String, Object> parameters) {
        if (parameters.containsKey("duration")) {
            Object durationObj = parameters.get("duration");
            if (durationObj instanceof Integer) {
                this.durationMs = (Integer) durationObj;
            } else if (durationObj instanceof String) {
                this.durationMs = Integer.parseInt((String) durationObj);
            }
        }
    }
    
    @Override
    public TaskResult execute() throws Exception {
        long taskId = Thread.currentThread().threadId();
        long startTime = System.nanoTime();
        
        try {
            Thread.sleep(durationMs);
            long latency = System.nanoTime() - startTime;
            
            return SimpleTaskResult.success(taskId, latency);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long latency = System.nanoTime() - startTime;
            return SimpleTaskResult.failure(taskId, latency, "Interrupted: " + e.getMessage());
        }
    }
}
