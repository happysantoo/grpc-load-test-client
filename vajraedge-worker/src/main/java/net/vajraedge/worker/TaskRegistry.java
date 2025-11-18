package net.vajraedge.worker;

import net.vajraedge.sdk.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of available task implementations.
 * Scans and maintains a map of task types to their implementation classes.
 */
public class TaskRegistry {
    private static final Logger log = LoggerFactory.getLogger(TaskRegistry.class);
    
    private final Map<String, Class<? extends Task>> taskRegistry = new ConcurrentHashMap<>();
    
    public TaskRegistry() {
        // TODO: Implement classpath scanning for @VajraTask annotated classes
        // For now, tasks can be registered manually
        log.info("TaskRegistry initialized");
    }
    
    /**
     * Register a task type manually.
     *
     * @param taskType Task type identifier
     * @param taskClass Task implementation class
     */
    public void registerTask(String taskType, Class<? extends Task> taskClass) {
        taskRegistry.put(taskType, taskClass);
        log.info("Registered task: {} -> {}", taskType, taskClass.getName());
    }
    
    /**
     * Get task class for a given task type.
     *
     * @param taskType Task type identifier
     * @return Task class or null if not found
     */
    public Class<? extends Task> getTaskClass(String taskType) {
        return taskRegistry.get(taskType);
    }
    
    /**
     * Check if a task type is supported.
     *
     * @param taskType Task type identifier
     * @return true if task type is registered
     */
    public boolean supports(String taskType) {
        return taskRegistry.containsKey(taskType);
    }
    
    /**
     * Get all supported task types.
     *
     * @return Array of supported task types
     */
    public String[] getSupportedTaskTypes() {
        return taskRegistry.keySet().toArray(new String[0]);
    }
}
