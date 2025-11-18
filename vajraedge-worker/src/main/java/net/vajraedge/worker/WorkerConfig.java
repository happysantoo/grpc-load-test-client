package net.vajraedge.worker;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for worker node.
 * 
 * <p>Configuration can be provided via:
 * <ul>
 *   <li>Command line arguments</li>
 *   <li>Environment variables</li>
 *   <li>Configuration file</li>
 *   <li>Builder pattern</li>
 * </ul>
 *
 * @since 2.0.0
 */
@Command(name = "vajraedge-worker", mixinStandardHelpOptions = true, version = "2.0.0",
         description = "VajraEdge distributed load testing worker node")
public class WorkerConfig implements Runnable {
    
    @Option(names = {"-i", "--worker-id"}, 
            description = "Unique worker identifier",
            required = true,
            defaultValue = "${WORKER_ID}")
    private String workerId;
    
    @Option(names = {"-c", "--controller-address"}, 
            description = "Controller address (host:port)",
            required = true,
            defaultValue = "${CONTROLLER_ADDRESS:-localhost:9090}")
    private String controllerAddress;
    
    @Option(names = {"-p", "--grpc-port"}, 
            description = "Worker gRPC server port (default: ${DEFAULT-VALUE})",
            defaultValue = "9091")
    private int grpcPort;
    
    @Option(names = {"-m", "--max-concurrency"}, 
            description = "Maximum concurrent tasks (default: ${DEFAULT-VALUE})",
            defaultValue = "10000")
    private int maxConcurrency;
    
    @Option(names = {"-r", "--region"}, 
            description = "Worker region/zone",
            defaultValue = "default")
    private String region;
    
    @Option(names = {"-t", "--tags"}, 
            description = "Worker tags (comma-separated)",
            split = ",")
    private List<String> tags = new ArrayList<>();
    
    @Option(names = {"--metrics-interval"}, 
            description = "Metrics reporting interval in seconds (default: ${DEFAULT-VALUE})",
            defaultValue = "5")
    private int metricsIntervalSeconds;
    
    @Option(names = {"--heartbeat-interval"}, 
            description = "Heartbeat interval in seconds (default: ${DEFAULT-VALUE})",
            defaultValue = "10")
    private int heartbeatIntervalSeconds;
    
    /**
     * Parse configuration from command line arguments.
     *
     * @param args Command line arguments
     * @return Parsed configuration
     */
    public static WorkerConfig fromArgs(String[] args) {
        WorkerConfig config = new WorkerConfig();
        new CommandLine(config).parseArgs(args);
        return config;
    }
    
    /**
     * Create a new builder.
     *
     * @return Configuration builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    @Override
    public void run() {
        // Called by picocli when command is executed
        System.out.println("Worker Configuration:");
        System.out.println("  Worker ID: " + workerId);
        System.out.println("  Controller: " + controllerAddress);
        System.out.println("  gRPC Port: " + grpcPort);
        System.out.println("  Max Concurrency: " + maxConcurrency);
        System.out.println("  Region: " + region);
        System.out.println("  Tags: " + tags);
    }
    
    // Getters
    public String getWorkerId() {
        return workerId;
    }
    
    public String getControllerAddress() {
        return controllerAddress;
    }
    
    public int getGrpcPort() {
        return grpcPort;
    }
    
    public int getMaxConcurrency() {
        return maxConcurrency;
    }
    
    public String getRegion() {
        return region;
    }
    
    public List<String> getTags() {
        return tags;
    }
    
    public int getMetricsIntervalSeconds() {
        return metricsIntervalSeconds;
    }
    
    public int getHeartbeatIntervalSeconds() {
        return heartbeatIntervalSeconds;
    }
    
    /**
     * Get worker capabilities (plugins available).
     *
     * @return List of available task types
     */
    public List<String> getCapabilities() {
        // Return basic capabilities
        // These will be validated against actual TaskRegistry at runtime
        List<String> capabilities = new ArrayList<>();
        capabilities.add("HTTP");
        capabilities.add("SLEEP");
        return capabilities;
    }
    
    /**
     * Builder for WorkerConfig.
     */
    public static class Builder {
        private final WorkerConfig config = new WorkerConfig();
        
        public Builder workerId(String workerId) {
            config.workerId = workerId;
            return this;
        }
        
        public Builder controllerAddress(String controllerAddress) {
            config.controllerAddress = controllerAddress;
            return this;
        }
        
        public Builder grpcPort(int grpcPort) {
            config.grpcPort = grpcPort;
            return this;
        }
        
        public Builder maxConcurrency(int maxConcurrency) {
            config.maxConcurrency = maxConcurrency;
            return this;
        }
        
        public Builder region(String region) {
            config.region = region;
            return this;
        }
        
        public Builder tags(List<String> tags) {
            config.tags = tags;
            return this;
        }
        
        public Builder metricsIntervalSeconds(int seconds) {
            config.metricsIntervalSeconds = seconds;
            return this;
        }
        
        public Builder heartbeatIntervalSeconds(int seconds) {
            config.heartbeatIntervalSeconds = seconds;
            return this;
        }
        
        public WorkerConfig build() {
            if (config.workerId == null || config.workerId.isEmpty()) {
                throw new IllegalStateException("Worker ID is required");
            }
            if (config.controllerAddress == null || config.controllerAddress.isEmpty()) {
                throw new IllegalStateException("Controller address is required");
            }
            return config;
        }
    }
}
