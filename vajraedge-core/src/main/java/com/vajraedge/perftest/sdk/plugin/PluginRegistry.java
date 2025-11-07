package com.vajraedge.perftest.sdk.plugin;

import com.vajraedge.perftest.sdk.TaskMetadata;
import com.vajraedge.perftest.sdk.TaskPlugin;
import com.vajraedge.perftest.sdk.annotations.VajraTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Central registry for task plugins.
 * Manages plugin discovery, registration, and retrieval.
 *
 * @since 1.1.0
 */
@Component
public class PluginRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(PluginRegistry.class);
    
    private final PluginScanner scanner;
    private final Map<String, PluginInfo> plugins = new ConcurrentHashMap<>();
    
    public PluginRegistry(PluginScanner scanner) {
        this.scanner = scanner;
    }
    
    /**
     * Initialize the registry by scanning for plugins.
     * Called automatically by Spring after construction.
     */
    @PostConstruct
    public void initialize() {
        logger.info("Initializing PluginRegistry...");
        discoverPlugins();
        logger.info("PluginRegistry initialized with {} plugins", plugins.size());
    }
    
    /**
     * Discover and register all plugins on the classpath.
     */
    public void discoverPlugins() {
        List<Class<? extends TaskPlugin>> pluginClasses = scanner.scanForPlugins();
        
        for (Class<? extends TaskPlugin> pluginClass : pluginClasses) {
            try {
                registerPlugin(pluginClass);
            } catch (Exception e) {
                logger.error("Failed to register plugin: {}", pluginClass.getName(), e);
            }
        }
    }
    
    /**
     * Register a plugin class.
     *
     * @param pluginClass Plugin class to register
     * @throws Exception if registration fails
     */
    public void registerPlugin(Class<? extends TaskPlugin> pluginClass) throws Exception {
        VajraTask annotation = pluginClass.getAnnotation(VajraTask.class);
        if (annotation == null) {
            throw new IllegalArgumentException("Plugin class must be annotated with @VajraTask");
        }
        
        // Create a temporary instance to get metadata
        TaskPlugin instance = pluginClass.getDeclaredConstructor().newInstance();
        TaskMetadata metadata = instance.getMetadata();
        
        String name = metadata.name();
        
        // Check for duplicate names
        if (plugins.containsKey(name)) {
            logger.warn("Plugin with name '{}' already registered, overwriting", name);
        }
        
        PluginInfo info = new PluginInfo(
            pluginClass,
            metadata,
            annotation.version(),
            annotation.author()
        );
        
        plugins.put(name, info);
        logger.info("Registered plugin: {} v{} by {}", name, annotation.version(), 
                   annotation.author().isEmpty() ? "unknown" : annotation.author());
    }
    
    /**
     * Get plugin info by name.
     *
     * @param name Plugin name
     * @return Plugin info, or null if not found
     */
    public PluginInfo getPlugin(String name) {
        return plugins.get(name);
    }
    
    /**
     * Check if a plugin is registered.
     *
     * @param name Plugin name
     * @return true if registered
     */
    public boolean hasPlugin(String name) {
        return plugins.containsKey(name);
    }
    
    /**
     * Get all registered plugins.
     *
     * @return Map of plugin name to plugin info
     */
    public Map<String, PluginInfo> getAllPlugins() {
        return Collections.unmodifiableMap(plugins);
    }
    
    /**
     * Get plugins by category.
     *
     * @param category Category name
     * @return List of plugins in the category
     */
    public List<PluginInfo> getPluginsByCategory(String category) {
        return plugins.values().stream()
            .filter(info -> info.getCategory().equalsIgnoreCase(category))
            .collect(Collectors.toList());
    }
    
    /**
     * Get all plugin names.
     *
     * @return Set of plugin names
     */
    public Set<String> getPluginNames() {
        return Collections.unmodifiableSet(plugins.keySet());
    }
    
    /**
     * Get count of registered plugins.
     *
     * @return Number of plugins
     */
    public int getPluginCount() {
        return plugins.size();
    }
    
    /**
     * Create a new instance of a plugin.
     *
     * @param name Plugin name
     * @return New plugin instance
     * @throws Exception if plugin not found or instantiation fails
     */
    public TaskPlugin createPluginInstance(String name) throws Exception {
        PluginInfo info = plugins.get(name);
        if (info == null) {
            throw new IllegalArgumentException("Plugin not found: " + name);
        }
        return info.createInstance();
    }
    
    /**
     * Clear all registered plugins.
     * Mainly for testing purposes.
     */
    public void clear() {
        plugins.clear();
        logger.info("Plugin registry cleared");
    }
}
