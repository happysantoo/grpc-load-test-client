package com.vajraedge.perftest.sdk.annotations;

import java.lang.annotation.*;

/**
 * Marks a class as a VajraEdge task plugin.
 * Classes annotated with this will be automatically discovered via classpath scanning.
 * 
 * <p>The annotated class must implement {@link com.vajraedge.perftest.sdk.TaskPlugin}.</p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * @VajraTask(
 *     name = "HTTP_GET",
 *     displayName = "HTTP GET Request",
 *     description = "Performs HTTP GET request to a URL",
 *     category = "HTTP"
 * )
 * public class HttpGetPlugin implements TaskPlugin {
 *     // Implementation
 * }
 * }</pre>
 *
 * @since 1.1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface VajraTask {
    
    /**
     * Unique task identifier.
     * Must be unique across all plugins.
     * Convention: UPPERCASE_WITH_UNDERSCORES (e.g., "HTTP_GET", "DATABASE_QUERY")
     *
     * @return Task name
     */
    String name();
    
    /**
     * Human-readable display name for UI.
     * 
     * @return Display name
     */
    String displayName();
    
    /**
     * Detailed description of what the task does.
     * Will be shown in UI tooltips and documentation.
     * 
     * @return Task description
     */
    String description() default "";
    
    /**
     * Task category for grouping in UI.
     * Examples: "HTTP", "DATABASE", "MESSAGING", "SYSTEM"
     * 
     * @return Category name
     */
    String category() default "OTHER";
    
    /**
     * Version of the plugin.
     * Follows semantic versioning (e.g., "1.0.0")
     * 
     * @return Plugin version
     */
    String version() default "1.0.0";
    
    /**
     * Plugin author or organization.
     * 
     * @return Author name
     */
    String author() default "";
}
