package com.vajraedge.plugins.http;

import com.vajraedge.sdk.SimpleTaskResult;
import com.vajraedge.sdk.TaskResult;
import com.vajraedge.sdk.TaskMetadata;
import com.vajraedge.sdk.TaskMetadata.ParameterDef;
import com.vajraedge.sdk.TaskPlugin;
import com.vajraedge.sdk.ParameterValidator;
import com.vajraedge.sdk.TaskExecutionHelper;
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
public class SleepTask implements TaskPlugin {
    
    private int durationMs;
    
    public SleepTask() {
        this.durationMs = 100; // Default
    }
    
    public SleepTask(int durationMs) {
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
        ParameterValidator.requireIntegerInRange(parameters, "duration", 1, 60000);
    }
    
    @Override
    public void initialize(Map<String, Object> parameters) {
        this.durationMs = ParameterValidator.getIntegerOrDefault(parameters, "duration", 100);
    }
    
    @Override
    public TaskResult execute() throws Exception {
        long startTime = System.nanoTime();
        
        try {
            Thread.sleep(durationMs);
            return TaskExecutionHelper.createSuccessResult(startTime);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TaskExecutionHelper.createFailureResult(startTime, "Interrupted: " + e.getMessage());
        }
    }
}