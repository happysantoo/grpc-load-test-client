package net.vajraedge.perftest.suite;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Represents a weighted distribution of task types within a scenario.
 * 
 * <p>Example: 70% reads, 20% writes, 10% deletes
 * 
 * <p>Task mix allows realistic load patterns where different operations
 * occur with different frequencies.
 * 
 * @author Santhosh Kuppusamy
 * @since 1.0
 */
public class TaskMix {
    private final Map<String, Integer> weights;
    private final Random random;
    private int totalWeight;
    
    public TaskMix() {
        this.weights = new HashMap<>();
        this.random = new Random();
        this.totalWeight = 0;
    }
    
    /**
     * Add a task type with its weight.
     * 
     * @param taskType the task type (e.g., "HTTP_GET", "HTTP_POST")
     * @param weight the relative weight (e.g., 70 for 70%)
     */
    public void addTask(String taskType, int weight) {
        if (weight <= 0) {
            throw new IllegalArgumentException("Weight must be positive: " + weight);
        }
        weights.put(taskType, weight);
        totalWeight += weight;
    }
    
    /**
     * Select a task type based on weighted distribution.
     * 
     * @return randomly selected task type according to weights
     */
    public String selectTask() {
        if (weights.isEmpty()) {
            throw new IllegalStateException("No tasks defined in mix");
        }
        
        int random = this.random.nextInt(totalWeight);
        int cumulative = 0;
        
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            cumulative += entry.getValue();
            if (random < cumulative) {
                return entry.getKey();
            }
        }
        
        // Should never reach here, but return first task as fallback
        return weights.keySet().iterator().next();
    }
    
    /**
     * Get the percentage for a specific task type.
     * 
     * @param taskType the task type
     * @return percentage (0-100)
     */
    public double getPercentage(String taskType) {
        Integer weight = weights.get(taskType);
        if (weight == null) {
            return 0.0;
        }
        return (weight * 100.0) / totalWeight;
    }
    
    /**
     * Get all task types in this mix.
     * 
     * @return map of task types to weights
     */
    public Map<String, Integer> getWeights() {
        return new HashMap<>(weights);
    }
    
    /**
     * Check if this mix is empty.
     * 
     * @return true if no tasks defined
     */
    public boolean isEmpty() {
        return weights.isEmpty();
    }
    
    /**
     * Get total number of task types.
     * 
     * @return count of task types
     */
    public int size() {
        return weights.size();
    }
}
