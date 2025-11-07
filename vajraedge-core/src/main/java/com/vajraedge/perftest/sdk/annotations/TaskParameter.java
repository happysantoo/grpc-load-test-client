package com.vajraedge.perftest.sdk.annotations;

import java.lang.annotation.*;

/**
 * Marks a field or method parameter as a task parameter.
 * Used for automatic parameter extraction and validation.
 * 
 * <p>Example usage on fields:</p>
 * <pre>{@code
 * public class HttpGetPlugin implements TaskPlugin {
 *     @TaskParameter(
 *         name = "url",
 *         description = "Target URL",
 *         required = true
 *     )
 *     private String url;
 *     
 *     @TaskParameter(
 *         name = "timeout",
 *         description = "Request timeout in milliseconds",
 *         required = false,
 *         defaultValue = "5000"
 *     )
 *     private int timeout = 5000;
 * }
 * }</pre>
 *
 * @since 1.1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Documented
public @interface TaskParameter {
    
    /**
     * Parameter name.
     * If not specified, the field/parameter name will be used.
     * 
     * @return Parameter name
     */
    String name() default "";
    
    /**
     * Parameter description for documentation.
     * Will be shown in UI and generated docs.
     * 
     * @return Description
     */
    String description() default "";
    
    /**
     * Whether this parameter is required.
     * 
     * @return true if required, false otherwise
     */
    boolean required() default false;
    
    /**
     * Default value as a string.
     * Will be converted to the appropriate type.
     * 
     * @return Default value
     */
    String defaultValue() default "";
    
    /**
     * Regex pattern for validation (for string parameters).
     * 
     * @return Validation pattern
     */
    String pattern() default "";
    
    /**
     * Minimum value (for numeric parameters).
     * 
     * @return Minimum value
     */
    long min() default Long.MIN_VALUE;
    
    /**
     * Maximum value (for numeric parameters).
     * 
     * @return Maximum value
     */
    long max() default Long.MAX_VALUE;
    
    /**
     * Example values for documentation.
     * 
     * @return Array of example values
     */
    String[] examples() default {};
}
