package com.vajraedge.plugins.grpc;

import com.vajraedge.sdk.SimpleTaskResult;
import com.vajraedge.sdk.TaskResult;
import com.vajraedge.sdk.TaskMetadata;
import com.vajraedge.sdk.TaskMetadata.ParameterDef;
import com.vajraedge.sdk.TaskPlugin;
import com.vajraedge.sdk.annotations.VajraTask;

import java.util.List;
import java.util.Map;

/**
 * Example gRPC unary call task plugin for load testing gRPC services.
 * This is a reference implementation showing how to create a gRPC plugin.
 * 
 * <p>To use this plugin, you would:
 * <ol>
 *   <li>Add your protobuf dependencies to the plugin module</li>
 *   <li>Generate gRPC stubs from your .proto files</li>
 *   <li>Initialize ManagedChannel and stub in initialize()</li>
 *   <li>Make gRPC calls in execute()</li>
 *   <li>Properly shutdown channel in a cleanup method</li>
 * </ol>
 * 
 * @since 1.1.0
 */
@VajraTask(
    name = "GRPC_UNARY",
    displayName = "gRPC Unary Call",
    description = "Performs unary gRPC calls to test gRPC services (example implementation)",
    category = "GRPC",
    version = "1.0.0",
    author = "VajraEdge"
)
public class GrpcUnaryTask implements TaskPlugin {
    
    private String target;
    private String method;
    private String requestJson;
    private int timeoutMs;
    private boolean useTls;
    
    // In real implementation:
    // private ManagedChannel channel;
    // private YourServiceGrpc.YourServiceBlockingStub stub;
    
    public GrpcUnaryTask() {
        this.target = "localhost:50051";
        this.method = "yourservice.YourMethod";
        this.requestJson = "{}";
        this.timeoutMs = 5000;
        this.useTls = false;
    }
    
    @Override
    public TaskMetadata getMetadata() {
        return TaskMetadata.builder()
            .name("GRPC_UNARY")
            .displayName("gRPC Unary Call")
            .description("Performs unary gRPC calls to test gRPC services")
            .category("GRPC")
            .parameters(List.of(
                ParameterDef.requiredString(
                    "target",
                    "gRPC server target (host:port)"
                ),
                ParameterDef.requiredString(
                    "method",
                    "gRPC method name (fully qualified)"
                ),
                ParameterDef.requiredString(
                    "request",
                    "Request payload in JSON format"
                ),
                ParameterDef.optionalInteger(
                    "timeout",
                    5000,
                    100,
                    60000,
                    "Request timeout in milliseconds (100-60000)"
                ),
                new ParameterDef(
                    "useTls",
                    "boolean",
                    false,
                    false,
                    "Whether to use TLS for the connection",
                    null,
                    null,
                    null
                )
            ))
            .metadata(Map.of(
                "protocol", "gRPC",
                "blocking", "true",
                "example", "true"
            ))
            .build();
    }
    
    @Override
    public void validateParameters(Map<String, Object> parameters) {
        if (!parameters.containsKey("target") || parameters.get("target").toString().isBlank()) {
            throw new IllegalArgumentException("Target parameter is required");
        }
        
        if (!parameters.containsKey("method") || parameters.get("method").toString().isBlank()) {
            throw new IllegalArgumentException("Method parameter is required");
        }
        
        if (!parameters.containsKey("request") || parameters.get("request").toString().isBlank()) {
            throw new IllegalArgumentException("Request parameter is required");
        }
        
        if (parameters.containsKey("timeout")) {
            Object timeoutObj = parameters.get("timeout");
            int timeout = timeoutObj instanceof Integer ? (Integer) timeoutObj : 
                         Integer.parseInt(timeoutObj.toString());
            
            if (timeout < 100 || timeout > 60000) {
                throw new IllegalArgumentException("Timeout must be between 100 and 60000 milliseconds");
            }
        }
    }
    
    @Override
    public void initialize(Map<String, Object> parameters) {
        this.target = parameters.get("target").toString();
        this.method = parameters.get("method").toString();
        this.requestJson = parameters.get("request").toString();
        
        if (parameters.containsKey("timeout")) {
            Object timeoutObj = parameters.get("timeout");
            this.timeoutMs = timeoutObj instanceof Integer ? (Integer) timeoutObj : 
                            Integer.parseInt(timeoutObj.toString());
        }
        
        if (parameters.containsKey("useTls")) {
            this.useTls = Boolean.parseBoolean(parameters.get("useTls").toString());
        }
        
        // In real implementation, initialize gRPC channel and stub:
        // this.channel = useTls 
        //     ? ManagedChannelBuilder.forTarget(target).useTransportSecurity().build()
        //     : ManagedChannelBuilder.forTarget(target).usePlaintext().build();
        // this.stub = YourServiceGrpc.newBlockingStub(channel)
        //     .withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public TaskResult execute() throws Exception {
        long taskId = Thread.currentThread().threadId();
        long startTime = System.nanoTime();
        
        try {
            // In real implementation, make gRPC call:
            // YourRequest request = parseJsonToProto(requestJson);
            // YourResponse response = stub.yourMethod(request);
            
            // Example stub implementation that simulates gRPC call:
            Thread.sleep(10); // Simulate network latency
            
            long latency = System.nanoTime() - startTime;
            
            return SimpleTaskResult.success(taskId, latency, 100, 
                Map.of("target", target, "method", method, "type", "example"));
            
        } catch (Exception e) {
            long latency = System.nanoTime() - startTime;
            return SimpleTaskResult.failure(taskId, latency, 
                "gRPC call failed: " + e.getMessage(),
                Map.of("target", target, "method", method, "error", e.getClass().getSimpleName()));
        }
    }
    
    /**
     * Cleanup method to be called when the test is finished.
     * In real implementation, shutdown the gRPC channel:
     * 
     * <pre>
     * public void shutdown() {
     *     if (channel != null && !channel.isShutdown()) {
     *         channel.shutdown();
     *         try {
     *             channel.awaitTermination(5, TimeUnit.SECONDS);
     *         } catch (InterruptedException e) {
     *             channel.shutdownNow();
     *         }
     *     }
     * }
     * </pre>
     */
}
