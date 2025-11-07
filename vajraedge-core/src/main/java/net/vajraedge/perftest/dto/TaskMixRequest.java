package net.vajraedge.perftest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Request DTO for configuring a task mix.
 * 
 * @author Santhosh Kuppusamy
 * @since 1.0
 */
public class TaskMixRequest {
    
    @NotNull(message = "Task weights are required")
    private Map<String, Integer> weights = new HashMap<>();
    
    public Map<String, Integer> getWeights() {
        return weights;
    }
    
    public void setWeights(Map<String, Integer> weights) {
        this.weights = weights;
    }
    
    public void addTask(String taskType, int weight) {
        this.weights.put(taskType, weight);
    }
}
