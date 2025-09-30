package com.example.grpc.loadtest.client;

import com.example.grpc.loadtest.config.LoadTestConfig;
import com.example.grpc.loadtest.proto.*;
import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    
    public GrpcLoadTestClient(LoadTestConfig config) {
        this.config = config;
        this.channel = createChannel();
        this.blockingStub = LoadTestServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(config.getClient().getRequestTimeoutMs(), TimeUnit.MILLISECONDS);
        this.asyncStub = LoadTestServiceGrpc.newStub(channel)
                .withDeadlineAfter(config.getClient().getRequestTimeoutMs(), TimeUnit.MILLISECONDS);
        
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