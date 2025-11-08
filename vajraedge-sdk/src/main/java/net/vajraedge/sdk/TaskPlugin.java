package net.vajraedge.sdk;

import java.util.Map;

/**
 * Marker interface for VajraEdge task plugins.
 * Implement this interface to create discoverable task plugins.
 * 
 * <p>Plugins are automatically discovered via classpath scanning
 * when annotated with {@link VajraTask}.</p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * @VajraTask(
 *     name = "HTTP_GET",
 *     displayName = "HTTP GET Request",
 *     description = "Performs HTTP GET request to a URL"
 * )
 * public class HttpGetPlugin implements TaskPlugin {
 *     // Implementation
 * }
 * }</pre>
 *
 * @since 1.1.0
 */
public interface TaskPlugin extends Task {
    
    /**
     * Get metadata about this task type.
     * This provides information for UI rendering and documentation.
     *
     * @return Task metadata including name, description, and parameters
     */
    TaskMetadata getMetadata();
    
    /**
     * Validate parameters before execution.
     * Override this method to provide custom validation logic.
     * 
     * <p>Default implementation does no validation.</p>
     *
     * @param parameters Task parameters to validate
     * @throws IllegalArgumentException if parameters are invalid
     */
    default void validateParameters(Map<String, Object> parameters) {
        // Override if validation needed
    }
    
    /**
     * Initialize the task with given parameters.
     * Called before execute() to configure the task instance.
     * 
     * <p>Default implementation does nothing.</p>
     *
     * @param parameters Task parameters for initialization
     * @throws Exception if initialization fails
     */
    default void initialize(Map<String, Object> parameters) throws Exception {
        // Override if initialization needed
    }
}
