package net.vajraedge.perftest.validation.checks;

import net.vajraedge.perftest.validation.CheckResult;
import net.vajraedge.perftest.validation.ValidationCheck;
import net.vajraedge.perftest.validation.ValidationContext;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates system resources (memory, threads, disk space).
 */
@Component
public class ResourceCheck implements ValidationCheck {
    
    private static final long BYTES_PER_MB = 1024 * 1024;
    private static final long BYTES_PER_GB = 1024 * BYTES_PER_MB;
    private static final long MIN_FREE_MEMORY_MB = 512;
    private static final long MIN_FREE_DISK_MB = 100;
    
    private final Runtime runtime = Runtime.getRuntime();
    
    @Override
    public String getName() {
        return "Resource Check";
    }
    
    @Override
    public CheckResult execute(ValidationContext context) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> details = new ArrayList<>();
        
        // Check memory
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long availableMemory = maxMemory - usedMemory;
        
        long availableMB = availableMemory / BYTES_PER_MB;
        long availableGB = availableMemory / BYTES_PER_GB;
        
        details.add(String.format("Max memory: %.2f GB", maxMemory / (double) BYTES_PER_GB));
        details.add(String.format("Used memory: %.2f GB", usedMemory / (double) BYTES_PER_GB));
        details.add(String.format("Available memory: %.2f GB", availableGB + (availableMB % 1024) / 1024.0));
        
        if (availableMB < MIN_FREE_MEMORY_MB) {
            errors.add(String.format("Insufficient memory: only %d MB available (minimum %d MB required)", 
                availableMB, MIN_FREE_MEMORY_MB));
        }
        
        // Estimate memory requirement based on test config
        Integer maxConcurrency = context.getMaxConcurrency();
        if (maxConcurrency != null) {
            long estimatedMemoryMB = estimateMemoryRequirement(maxConcurrency);
            details.add(String.format("Estimated memory needed: ~%d MB for %d concurrent users", 
                estimatedMemoryMB, maxConcurrency));
            
            if (estimatedMemoryMB > availableMB) {
                warnings.add(String.format("Available memory (%d MB) may be insufficient for %d concurrent users (estimated need: %d MB)", 
                    availableMB, maxConcurrency, estimatedMemoryMB));
                warnings.add("Consider reducing max concurrency or increasing JVM heap size (-Xmx)");
            }
        }
        
        // Check disk space
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        long freeDiskSpace = tempDir.getFreeSpace();
        long freeDiskMB = freeDiskSpace / BYTES_PER_MB;
        
        details.add(String.format("Free disk space: %.2f GB", freeDiskSpace / (double) BYTES_PER_GB));
        
        if (freeDiskMB < MIN_FREE_DISK_MB) {
            errors.add(String.format("Insufficient disk space: only %d MB available (minimum %d MB required)", 
                freeDiskMB, MIN_FREE_DISK_MB));
        }
        
        // Check available processors
        int availableProcessors = runtime.availableProcessors();
        details.add(String.format("Available CPUs: %d", availableProcessors));
        
        if (availableProcessors < 2) {
            warnings.add("System has fewer than 2 CPUs - performance may be limited");
        }
        
        // Virtual thread support check
        try {
            // Try to create a virtual thread to verify platform support
            Thread.ofVirtual().start(() -> {}).join();
            details.add("✓ Virtual threads supported");
        } catch (Exception e) {
            errors.add("Virtual threads not supported - Java 21+ is required");
        }
        
        // Return result
        if (!errors.isEmpty()) {
            return CheckResult.fail(getName(), "Insufficient system resources", errors);
        } else if (!warnings.isEmpty()) {
            return CheckResult.warn(getName(), "System resources may be limited", warnings);
        } else {
            return CheckResult.pass(getName(), "System resources are adequate", details);
        }
    }
    
    private long estimateMemoryRequirement(int maxConcurrency) {
        // Rough estimate: 1MB per concurrent virtual thread + 100MB overhead
        return maxConcurrency + 100;
    }
}
