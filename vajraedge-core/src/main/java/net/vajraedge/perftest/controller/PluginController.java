package net.vajraedge.perftest.controller;

import net.vajraedge.perftest.dto.PluginInfoResponse;
import net.vajraedge.perftest.sdk.plugin.PluginInfo;
import net.vajraedge.perftest.sdk.plugin.PluginRegistry;
import net.vajraedge.sdk.TaskMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for plugin management and discovery.
 * Provides endpoints to query available task plugins.
 *
 * @since 1.1.0
 */
@RestController
@RequestMapping("/api/plugins")
@CrossOrigin(origins = "*")
public class PluginController {
    
    private static final Logger logger = LoggerFactory.getLogger(PluginController.class);
    
    private final PluginRegistry pluginRegistry;
    
    public PluginController(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }
    
    /**
     * Get all available task types/plugins.
     * 
     * @return List of available plugins with metadata
     */
    @GetMapping("/types")
    public ResponseEntity<List<PluginInfoResponse>> getAvailableTaskTypes() {
        logger.debug("Fetching all available task types");
        
        Map<String, PluginInfo> plugins = pluginRegistry.getAllPlugins();
        
        List<PluginInfoResponse> response = plugins.values().stream()
            .map(this::toPluginInfoResponse)
            .sorted((a, b) -> a.category().compareTo(b.category()))
            .collect(Collectors.toList());
        
        logger.info("Returning {} available task types", response.size());
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get plugins by category.
     * 
     * @param category Category name (e.g., "HTTP", "DATABASE")
     * @return List of plugins in the category
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<List<PluginInfoResponse>> getPluginsByCategory(
            @PathVariable String category) {
        logger.debug("Fetching plugins for category: {}", category);
        
        List<PluginInfo> plugins = pluginRegistry.getPluginsByCategory(category);
        
        List<PluginInfoResponse> response = plugins.stream()
            .map(this::toPluginInfoResponse)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get detailed information about a specific plugin.
     * 
     * @param name Plugin name
     * @return Plugin information
     */
    @GetMapping("/{name}")
    public ResponseEntity<PluginInfoResponse> getPlugin(@PathVariable String name) {
        logger.debug("Fetching plugin: {}", name);
        
        PluginInfo plugin = pluginRegistry.getPlugin(name);
        
        if (plugin == null) {
            logger.warn("Plugin not found: {}", name);
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(toPluginInfoResponse(plugin));
    }
    
    /**
     * Get plugin registry statistics.
     * 
     * @return Plugin statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getPluginStats() {
        logger.debug("Fetching plugin statistics");
        
        Map<String, Object> stats = Map.of(
            "totalPlugins", pluginRegistry.getPluginCount(),
            "pluginNames", pluginRegistry.getPluginNames(),
            "categories", getCategories()
        );
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Rescan classpath for plugins.
     * Useful during development or hot-reload scenarios.
     * 
     * @return Updated plugin count
     */
    @PostMapping("/rescan")
    public ResponseEntity<Map<String, Object>> rescanPlugins() {
        logger.info("Rescanning classpath for plugins");
        
        pluginRegistry.clear();
        pluginRegistry.discoverPlugins();
        
        Map<String, Object> result = Map.of(
            "message", "Plugin rescan complete",
            "totalPlugins", pluginRegistry.getPluginCount()
        );
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Convert PluginInfo to response DTO.
     */
    private PluginInfoResponse toPluginInfoResponse(PluginInfo info) {
        TaskMetadata metadata = info.metadata();
        
        return new PluginInfoResponse(
            metadata.name(),
            metadata.displayName(),
            metadata.description(),
            metadata.category(),
            info.version(),
            info.author(),
            metadata.parameters(),
            metadata.metadata()
        );
    }
    
    /**
     * Get all unique categories.
     */
    private List<String> getCategories() {
        return pluginRegistry.getAllPlugins().values().stream()
            .map(info -> info.metadata().category())
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }
}
