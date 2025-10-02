package com.example.grpc.loadtest.client;

import com.example.grpc.loadtest.config.LoadTestConfig;
import com.example.grpc.loadtest.payload.DefaultPayloadTransformer;
import com.example.grpc.loadtest.payload.PayloadTransformer;
import com.example.grpc.loadtest.randomization.RandomizationManager;
import com.example.grpc.loadtest.proto.*;
import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * gRPC client wrapper for load testing
 */
public class GrpcLoadTestClient implements AutoCloseable {
    
    private static final Logger logger = LoggerFactory.getLogger(GrpcLoadTestClient.class);
    
    private final LoadTestConfig config;
    private final ManagedChannel channel;
    private final LoadTestServiceGrpc.LoadTestServiceBlockingStub blockingStub;
    private final LoadTestServiceGrpc.LoadTestServiceStub asyncStub;
    private final AtomicLong requestCounter = new AtomicLong(0);
    private final PayloadTransformer payloadTransformer;
    private final RandomizationManager randomizationManager;
    
    public GrpcLoadTestClient(LoadTestConfig config) {
        this.config = config;
        this.channel = createChannel();
        this.blockingStub = LoadTestServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(config.getClient().getRequestTimeoutMs(), TimeUnit.MILLISECONDS);
        this.asyncStub = LoadTestServiceGrpc.newStub(channel)
                .withDeadlineAfter(config.getClient().getRequestTimeoutMs(), TimeUnit.MILLISECONDS);
        
        // Initialize payload transformation and randomization
        this.payloadTransformer = new DefaultPayloadTransformer();
        this.randomizationManager = createRandomizationManager(config);
        
        logger.info("Created gRPC client for {}:{}", config.getTarget().getHost(), config.getTarget().getPort());
    }
    
    private ManagedChannel createChannel() {
        NettyChannelBuilder builder = NettyChannelBuilder
                .forAddress(config.getTarget().getHost(), config.getTarget().getPort())
                .keepAliveTime(config.getClient().getKeepAliveTimeMs(), TimeUnit.MILLISECONDS)
                .keepAliveTimeout(config.getClient().getKeepAliveTimeoutMs(), TimeUnit.MILLISECONDS)
                .keepAliveWithoutCalls(config.getClient().isKeepAliveWithoutCalls())
                .maxInboundMessageSize(config.getClient().getMaxInboundMessageSize())
                .userAgent(config.getClient().getUserAgent());
        
        if (!config.getTarget().isUseTls()) {
            builder.usePlaintext();
        }
        
        return builder.build();
    }
    
    private RandomizationManager createRandomizationManager(LoadTestConfig config) {
        LoadTestConfig.RandomizationConfig randConfig = config.getRandomization();
        if (randConfig == null) {
            // Create default randomization config if none provided
            randConfig = new LoadTestConfig.RandomizationConfig();
        }
        
        RandomizationManager.RandomizationConfig.Builder builder = 
            new RandomizationManager.RandomizationConfig.Builder();
        
        if (randConfig.isEnableMethodRandomization()) {
            builder.enableMethodRandomization(randConfig.getAvailableMethods(), randConfig.getMethodWeights());
        }
        
        if (randConfig.isEnablePayloadRandomization()) {
            Map<String, RandomizationManager.RandomFieldConfig> fieldConfigs = new HashMap<>();
            for (Map.Entry<String, LoadTestConfig.RandomizationConfig.RandomFieldConfig> entry : randConfig.getRandomFields().entrySet()) {
                LoadTestConfig.RandomizationConfig.RandomFieldConfig configField = entry.getValue();
                RandomizationManager.RandomFieldConfig randomField = convertToRandomFieldConfig(configField);
                fieldConfigs.put(entry.getKey(), randomField);
            }
            builder.enablePayloadRandomization(fieldConfigs);
        }
        
        if (randConfig.isEnableTimingRandomization()) {
            builder.enableTimingRandomization(randConfig.getMinDelayMs(), randConfig.getMaxDelayMs());
        }
        
        return new RandomizationManager(builder.build());
    }
    
    private RandomizationManager.RandomFieldConfig convertToRandomFieldConfig(LoadTestConfig.RandomizationConfig.RandomFieldConfig configField) {
        switch (configField.getType().toLowerCase()) {
            case "string":
                Object minVal = configField.getMinValue();
                Object maxVal = configField.getMaxValue();
                if (!(minVal instanceof Integer) || !(maxVal instanceof Integer)) {
                    throw new IllegalArgumentException("String randomization requires integer min/max values for field type: " + configField.getType());
                }
                return RandomizationManager.RandomFieldConfig.randomString(
                    (Integer) minVal, (Integer) maxVal);
            case "number":
                Object minNumVal = configField.getMinValue();
                Object maxNumVal = configField.getMaxValue();
                if (!(minNumVal instanceof Number) || !(maxNumVal instanceof Number)) {
                    throw new IllegalArgumentException("Number randomization requires numeric min/max values for field type: " + configField.getType());
                }
                return RandomizationManager.RandomFieldConfig.randomNumber(
                    (Number) minNumVal, (Number) maxNumVal);
            case "list":
                if (configField.getPossibleValues() == null || configField.getPossibleValues().isEmpty()) {
                    throw new IllegalArgumentException("List randomization requires non-empty possibleValues for field type: " + configField.getType());
                }
                return RandomizationManager.RandomFieldConfig.fromList(configField.getPossibleValues());
            case "pattern":
                if (configField.getPattern() == null || configField.getPattern().trim().isEmpty()) {
                    throw new IllegalArgumentException("Pattern randomization requires non-empty pattern for field type: " + configField.getType());
                }
                return RandomizationManager.RandomFieldConfig.pattern(configField.getPattern());
            default:
                logger.warn("Unknown randomization field type: {}, falling back to default string randomization", configField.getType());
                return RandomizationManager.RandomFieldConfig.randomString(5, 10);
        }
    }
    
    /**
     * Execute Echo method synchronously
     */
    public CallResult executeEcho(String message) {
        long startTime = System.nanoTime();
        long requestId = requestCounter.incrementAndGet();
        
        try {
            EchoRequest request = EchoRequest.newBuilder()
                    .setMessage(message)
                    .setTimestamp(System.currentTimeMillis())
                    .setClientId("load-test-client")
                    .build();
            
            EchoResponse response = blockingStub.echo(request);
            
            long endTime = System.nanoTime();
            long latencyNanos = endTime - startTime;
            
            return CallResult.success(requestId, latencyNanos, response.toString().length());
            
        } catch (StatusRuntimeException e) {
            long endTime = System.nanoTime();
            long latencyNanos = endTime - startTime;
            
            logger.debug("Request {} failed with status: {}", requestId, e.getStatus());
            return CallResult.failure(requestId, latencyNanos, e.getStatus().getCode().value(), e.getMessage());
        } catch (Exception e) {
            long endTime = System.nanoTime();
            long latencyNanos = endTime - startTime;
            
            logger.debug("Request {} failed with exception: {}", requestId, e.getMessage());
            return CallResult.failure(requestId, latencyNanos, -1, e.getMessage());
        }
    }
    
    /**
     * Execute Echo method asynchronously
     */
    public void executeEchoAsync(String message, Consumer<CallResult> callback) {
        long startTime = System.nanoTime();
        long requestId = requestCounter.incrementAndGet();
        
        EchoRequest request = EchoRequest.newBuilder()
                .setMessage(message)
                .setTimestamp(System.currentTimeMillis())
                .setClientId("load-test-client")
                .build();
        
        asyncStub.echo(request, new StreamObserver<EchoResponse>() {
            @Override
            public void onNext(EchoResponse response) {
                long endTime = System.nanoTime();
                long latencyNanos = endTime - startTime;
                callback.accept(CallResult.success(requestId, latencyNanos, response.toString().length()));
            }
            
            @Override
            public void onError(Throwable t) {
                long endTime = System.nanoTime();
                long latencyNanos = endTime - startTime;
                
                if (t instanceof StatusRuntimeException) {
                    StatusRuntimeException sre = (StatusRuntimeException) t;
                    callback.accept(CallResult.failure(requestId, latencyNanos, 
                            sre.getStatus().getCode().value(), sre.getMessage()));
                } else {
                    callback.accept(CallResult.failure(requestId, latencyNanos, -1, t.getMessage()));
                }
            }
            
            @Override
            public void onCompleted() {
                // Response already handled in onNext
            }
        });
    }
    
    /**
     * Execute ComputeHash method for CPU-intensive testing
     */
    public CallResult executeComputeHash(String input, int iterations, String algorithm) {
        long startTime = System.nanoTime();
        long requestId = requestCounter.incrementAndGet();
        
        try {
            ComputeRequest request = ComputeRequest.newBuilder()
                    .setInput(input)
                    .setIterations(iterations)
                    .setAlgorithm(algorithm)
                    .build();
            
            ComputeResponse response = blockingStub.computeHash(request);
            
            long endTime = System.nanoTime();
            long latencyNanos = endTime - startTime;
            
            return CallResult.success(requestId, latencyNanos, response.toString().length());
            
        } catch (StatusRuntimeException e) {
            long endTime = System.nanoTime();
            long latencyNanos = endTime - startTime;
            
            logger.debug("ComputeHash request {} failed with status: {}", requestId, e.getStatus());
            return CallResult.failure(requestId, latencyNanos, e.getStatus().getCode().value(), e.getMessage());
        } catch (Exception e) {
            long endTime = System.nanoTime();
            long latencyNanos = endTime - startTime;
            
            logger.debug("ComputeHash request {} failed with exception: {}", requestId, e.getMessage());
            return CallResult.failure(requestId, latencyNanos, -1, e.getMessage());
        }
    }
    
    /**
     * Execute health check
     */
    public CallResult executeHealthCheck() {
        long startTime = System.nanoTime();
        long requestId = requestCounter.incrementAndGet();
        
        try {
            HealthCheckRequest request = HealthCheckRequest.newBuilder()
                    .setService("LoadTestService")
                    .build();
            
            HealthCheckResponse response = blockingStub.healthCheck(request);
            
            long endTime = System.nanoTime();
            long latencyNanos = endTime - startTime;
            
            return CallResult.success(requestId, latencyNanos, response.toString().length());
            
        } catch (StatusRuntimeException e) {
            long endTime = System.nanoTime();
            long latencyNanos = endTime - startTime;
            
            logger.debug("HealthCheck request {} failed with status: {}", requestId, e.getStatus());
            return CallResult.failure(requestId, latencyNanos, e.getStatus().getCode().value(), e.getMessage());
        } catch (Exception e) {
            long endTime = System.nanoTime();
            long latencyNanos = endTime - startTime;
            
            logger.debug("HealthCheck request {} failed with exception: {}", requestId, e.getMessage());
            return CallResult.failure(requestId, latencyNanos, -1, e.getMessage());
        }
    }
    
    /**
     * Execute a random method with enhanced payload transformation and randomization
     */
    public CallResult executeRandomRequest() {
        // Get random method
        String method = randomizationManager.getRandomMethod();
        
        // Apply randomization delay if enabled
        long delay = randomizationManager.getRandomDelay();
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Execute based on selected method
        switch (method) {
            case "ComputeHash":
                return executeRandomComputeHash();
            case "HealthCheck":
                return executeHealthCheck();
            case "StreamingEcho":
                // For now, fallback to Echo for streaming
                return executeRandomEcho();
            default:
                return executeRandomEcho();
        }
    }
    
    /**
     * Execute Echo method with randomized and transformed payload
     */
    public CallResult executeRandomEcho() {
        long startTime = System.nanoTime();
        long requestId = requestCounter.incrementAndGet();
        
        try {
            // Start with base payload or defaults
            Map<String, Object> payload = new HashMap<>();
            payload.put("message", config.getPayload().getDefaultValues().getOrDefault("message", "Hello World"));
            payload.put("timestamp", System.currentTimeMillis());
            payload.put("clientId", "load-test-client");
            
            // Apply base payload overrides
            payload.putAll(config.getPayload().getBasePayload());
            
            // Apply randomization
            Map<String, Object> randomFields = randomizationManager.generateRandomFields();
            payload.putAll(randomFields);
            
            // Apply payload transformations
            if (config.getPayload().isEnableTransformation()) {
                Map<String, PayloadTransformer.TransformationRule> transformationRules = convertTransformationRules();
                payload = payloadTransformer.transform(payload, transformationRules);
            }
            
            // Build the gRPC request
            EchoRequest.Builder requestBuilder = EchoRequest.newBuilder()
                    .setMessage(String.valueOf(payload.get("message")))
                    .setTimestamp(((Number) payload.get("timestamp")).longValue())
                    .setClientId(String.valueOf(payload.get("clientId")));
            
            // Add metadata if present
            if (payload.containsKey("metadata") && payload.get("metadata") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> metadata = (Map<String, String>) payload.get("metadata");
                requestBuilder.putAllMetadata(metadata);
            }
            
            EchoRequest request = requestBuilder.build();
            EchoResponse response = blockingStub.echo(request);
            
            long endTime = System.nanoTime();
            long latencyNanos = endTime - startTime;
            
            return CallResult.success(requestId, latencyNanos, response.toString().length());
            
        } catch (StatusRuntimeException e) {
            long endTime = System.nanoTime();
            long latencyNanos = endTime - startTime;
            
            logger.debug("Random Echo request {} failed with status: {}", requestId, e.getStatus());
            return CallResult.failure(requestId, latencyNanos, e.getStatus().getCode().value(), e.getMessage());
        } catch (Exception e) {
            long endTime = System.nanoTime();
            long latencyNanos = endTime - startTime;
            
            logger.debug("Random Echo request {} failed with exception: {}", requestId, e.getMessage());
            return CallResult.failure(requestId, latencyNanos, -1, e.getMessage());
        }
    }
    
    /**
     * Execute ComputeHash method with randomized payload
     */
    public CallResult executeRandomComputeHash() {
        long startTime = System.nanoTime();
        long requestId = requestCounter.incrementAndGet();
        
        try {
            // Start with default payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("input", "default-input");
            payload.put("iterations", 1000);
            payload.put("algorithm", "sha256");
            
            // Apply base payload and randomization
            payload.putAll(config.getPayload().getBasePayload());
            payload.putAll(randomizationManager.generateRandomFields());
            
            // Apply transformations
            if (config.getPayload().isEnableTransformation()) {
                Map<String, PayloadTransformer.TransformationRule> transformationRules = convertTransformationRules();
                payload = payloadTransformer.transform(payload, transformationRules);
            }
            
            ComputeRequest request = ComputeRequest.newBuilder()
                    .setInput(String.valueOf(payload.get("input")))
                    .setIterations(((Number) payload.getOrDefault("iterations", 1000)).intValue())
                    .setAlgorithm(String.valueOf(payload.getOrDefault("algorithm", "sha256")))
                    .build();
            
            ComputeResponse response = blockingStub.computeHash(request);
            
            long endTime = System.nanoTime();
            long latencyNanos = endTime - startTime;
            
            return CallResult.success(requestId, latencyNanos, response.toString().length());
            
        } catch (StatusRuntimeException e) {
            long endTime = System.nanoTime();
            long latencyNanos = endTime - startTime;
            
            logger.debug("Random ComputeHash request {} failed with status: {}", requestId, e.getStatus());
            return CallResult.failure(requestId, latencyNanos, e.getStatus().getCode().value(), e.getMessage());
        } catch (Exception e) {
            long endTime = System.nanoTime();
            long latencyNanos = endTime - startTime;
            
            logger.debug("Random ComputeHash request {} failed with exception: {}", requestId, e.getMessage());
            return CallResult.failure(requestId, latencyNanos, -1, e.getMessage());
        }
    }
    
    private Map<String, PayloadTransformer.TransformationRule> convertTransformationRules() {
        Map<String, PayloadTransformer.TransformationRule> rules = new HashMap<>();
        
        for (Map.Entry<String, LoadTestConfig.PayloadConfig.TransformationRuleConfig> entry : 
             config.getPayload().getTransformationRules().entrySet()) {
            
            String fieldName = entry.getKey();
            LoadTestConfig.PayloadConfig.TransformationRuleConfig ruleConfig = entry.getValue();
            
            PayloadTransformer.TransformationType type;
            try {
                type = PayloadTransformer.TransformationType.valueOf(ruleConfig.getType().toUpperCase());
            } catch (IllegalArgumentException e) {
                type = PayloadTransformer.TransformationType.CUSTOM;
            }
            
            PayloadTransformer.TransformationRule rule = new PayloadTransformer.TransformationRule(
                type, null, ruleConfig.getParameters());
            
            rules.put(fieldName, rule);
        }
        
        return rules;
    }
    
    /**
     * Get the current request count
     */
    public long getRequestCount() {
        return requestCounter.get();
    }
    
    /**
     * Check if the channel is ready
     */
    public boolean isReady() {
        ConnectivityState state = channel.getState(false);
        return state == ConnectivityState.READY;
    }
    
    @Override
    public void close() {
        if (channel != null && !channel.isShutdown()) {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                logger.info("gRPC channel closed gracefully");
            } catch (InterruptedException e) {
                logger.warn("Interrupted while closing channel", e);
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Result of a gRPC call
     */
    public static class CallResult {
        private final long requestId;
        private final long latencyNanos;
        private final boolean success;
        private final int statusCode;
        private final String errorMessage;
        private final int responseSize;
        
        private CallResult(long requestId, long latencyNanos, boolean success, 
                          int statusCode, String errorMessage, int responseSize) {
            this.requestId = requestId;
            this.latencyNanos = latencyNanos;
            this.success = success;
            this.statusCode = statusCode;
            this.errorMessage = errorMessage;
            this.responseSize = responseSize;
        }
        
        public static CallResult success(long requestId, long latencyNanos, int responseSize) {
            return new CallResult(requestId, latencyNanos, true, 0, null, responseSize);
        }
        
        public static CallResult failure(long requestId, long latencyNanos, int statusCode, String errorMessage) {
            return new CallResult(requestId, latencyNanos, false, statusCode, errorMessage, 0);
        }
        
        // Getters
        public long getRequestId() { return requestId; }
        public long getLatencyNanos() { return latencyNanos; }
        public double getLatencyMs() { return latencyNanos / 1_000_000.0; }
        public boolean isSuccess() { return success; }
        public int getStatusCode() { return statusCode; }
        public String getErrorMessage() { return errorMessage; }
        public int getResponseSize() { return responseSize; }
        
        @Override
        public String toString() {
            if (success) {
                return String.format("CallResult{id=%d, latency=%.2fms, success=true, size=%d}", 
                        requestId, getLatencyMs(), responseSize);
            } else {
                return String.format("CallResult{id=%d, latency=%.2fms, success=false, code=%d, error='%s'}", 
                        requestId, getLatencyMs(), statusCode, errorMessage);
            }
        }
    }
}