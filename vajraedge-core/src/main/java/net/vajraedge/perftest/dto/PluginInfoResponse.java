package net.vajraedge.perftest.dto;

import net.vajraedge.sdk.TaskMetadata;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for plugin information.
 * Contains metadata about a task plugin.
 *
 * @since 1.1.0
 */
public record PluginInfoResponse(
    String name,
    String displayName,
    String description,
    String category,
    String version,
    String author,
    List<TaskMetadata.ParameterDef> parameters,
    Map<String, String> metadata
) {
    
    /**
     * Check if plugin has required parameters.
     */
    public boolean hasRequiredParameters() {
        return parameters != null && parameters.stream()
            .anyMatch(TaskMetadata.ParameterDef::required);
    }
    
    /**
     * Get count of parameters.
     */
    public int getParameterCount() {
        return parameters != null ? parameters.size() : 0;
    }
}
