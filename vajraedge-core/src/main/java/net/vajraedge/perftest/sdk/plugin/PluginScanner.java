package net.vajraedge.perftest.sdk.plugin;

import net.vajraedge.sdk.TaskPlugin;
import net.vajraedge.sdk.annotations.VajraTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * Scans the classpath for task plugins annotated with {@link VajraTask}.
 * Uses Spring's classpath scanning for efficient discovery.
 *
 * @since 1.1.0
 */
@Component
public class PluginScanner {
    
    private static final Logger logger = LoggerFactory.getLogger(PluginScanner.class);
    
    private static final String BASE_PACKAGE = "net.vajraedge.perftest";
    
    /**
     * Scan the classpath for task plugins.
     * Finds all classes annotated with @VajraTask that implement TaskPlugin.
     *
     * @return List of discovered plugin classes
     */
    public List<Class<? extends TaskPlugin>> scanForPlugins() {
        return scanForPlugins(BASE_PACKAGE);
    }
    
    /**
     * Scan specific packages for task plugins.
     *
     * @param basePackages Base packages to scan
     * @return List of discovered plugin classes
     */
    public List<Class<? extends TaskPlugin>> scanForPlugins(String... basePackages) {
        List<Class<? extends TaskPlugin>> plugins = new ArrayList<>();
        
        for (String basePackage : basePackages) {
            logger.info("Scanning package '{}' for task plugins...", basePackage);
            
            ClassPathScanningCandidateComponentProvider scanner = createScanner();
            Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);
            
            logger.debug("Found {} candidate components in package '{}'", candidates.size(), basePackage);
            
            for (BeanDefinition beanDef : candidates) {
                try {
                    String className = beanDef.getBeanClassName();
                    if (className == null) {
                        logger.warn("BeanDefinition has null class name, skipping");
                        continue;
                    }
                    
                    Class<?> clazz = Class.forName(className);
                    
                    if (validatePlugin(clazz)) {
                        @SuppressWarnings("unchecked")
                        Class<? extends TaskPlugin> pluginClass = (Class<? extends TaskPlugin>) clazz;
                        plugins.add(pluginClass);
                        logger.info("Discovered plugin: {} ({})", 
                                  clazz.getAnnotation(VajraTask.class).name(), 
                                  className);
                    }
                    
                } catch (ClassNotFoundException e) {
                    logger.error("Failed to load plugin class: {}", beanDef.getBeanClassName(), e);
                } catch (Exception e) {
                    logger.error("Error processing plugin candidate: {}", beanDef.getBeanClassName(), e);
                }
            }
        }
        
        logger.info("Plugin scan complete. Found {} plugins", plugins.size());
        return plugins;
    }
    
    /**
     * Create a classpath scanner configured for plugin discovery.
     */
    private ClassPathScanningCandidateComponentProvider createScanner() {
        ClassPathScanningCandidateComponentProvider scanner = 
            new ClassPathScanningCandidateComponentProvider(false);
        
        // Add filter for @VajraTask annotation
        scanner.addIncludeFilter(new AnnotationTypeFilter(VajraTask.class));
        
        return scanner;
    }
    
    /**
     * Validate that a class is a valid plugin.
     */
    private boolean validatePlugin(Class<?> clazz) {
        // Must have @VajraTask annotation
        if (!clazz.isAnnotationPresent(VajraTask.class)) {
            logger.warn("Class {} is not annotated with @VajraTask", clazz.getName());
            return false;
        }
        
        // Must implement TaskPlugin
        if (!TaskPlugin.class.isAssignableFrom(clazz)) {
            logger.warn("Class {} does not implement TaskPlugin", clazz.getName());
            return false;
        }
        
        // Must not be abstract
        if (Modifier.isAbstract(clazz.getModifiers())) {
            logger.warn("Class {} is abstract, skipping", clazz.getName());
            return false;
        }
        
        // Must not be an interface
        if (clazz.isInterface()) {
            logger.warn("Class {} is an interface, skipping", clazz.getName());
            return false;
        }
        
        // Must have a no-arg constructor
        try {
            clazz.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            logger.warn("Class {} does not have a no-arg constructor", clazz.getName());
            return false;
        }
        
        return true;
    }
}
