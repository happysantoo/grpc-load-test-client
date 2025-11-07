package com.vajraedge.perftest.sdk.plugin;

import com.vajraedge.perftest.sdk.TaskMetadata;
import com.vajraedge.perftest.sdk.TaskPlugin;

/**
 * Information about a registered plugin.
 * Combines the plugin class and its metadata.
 *
 * @param pluginClass The plugin class
 * @param metadata Plugin metadata
 * @param version Plugin version
 * @param author Plugin author
 *
 * @since 1.1.0
 */
public record PluginInfo(
    Class<? extends TaskPlugin> pluginClass,
    TaskMetadata metadata,
    String version,
    String author
) {
    
    /**
     * Create a plugin instance.
     *
     * @return New plugin instance
     * @throws Exception if instantiation fails
     */
    public TaskPlugin createInstance() throws Exception {
        return pluginClass.getDeclaredConstructor().newInstance();
    }
    
    /**
     * Get the plugin name from metadata.
     */
    public String getName() {
        return metadata.name();
    }
    
    /**
     * Get the plugin display name from metadata.
     */
    public String getDisplayName() {
        return metadata.displayName();
    }
    
    /**
     * Get the plugin category from metadata.
     */
    public String getCategory() {
        return metadata.category();
    }
}
