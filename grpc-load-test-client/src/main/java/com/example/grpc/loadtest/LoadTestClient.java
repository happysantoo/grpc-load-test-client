package com.example.grpc.loadtest;

import com.example.grpc.loadtest.client.GrpcLoadTestClient;
import com.example.grpc.loadtest.config.LoadTestConfig;
import com.example.grpc.loadtest.controller.ThroughputController;
import com.example.grpc.loadtest.executor.VirtualThreadExecutor;
import com.example.grpc.loadtest.metrics.MetricsCollector;
import com.example.grpc.loadtest.reporting.StatisticsReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main application class for the gRPC load test client
 * Supports Java 21 virtual threads and provides comprehensive load testing capabilities
 */
@Command(
    name = "grpc-load-test-client",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    description = "High-performance gRPC load testing client using Java 21 virtual threads"
)
public class LoadTestClient implements Callable<Integer> {
    
    private static final Logger logger = LoggerFactory.getLogger(LoadTestClient.class);
    
    @Option(names = {"-h", "--host"}, description = "Target gRPC server host (default: localhost)")
    private String host = "localhost";
    
    @Option(names = {"-p", "--port"}, description = "Target gRPC server port (default: 8080)")
    private int port = 8080;
    
    @Option(names = {"-t", "--tps"}, description = "Target transactions per second (default: 100)")
    private int tps = 100;
    
    @Option(names = {"-d", "--duration"}, description = "Test duration in seconds (default: 60)")
    private int durationSeconds = 60;
    
    @Option(names = {"-w", "--warmup"}, description = "Warmup duration in seconds (default: 10)")
    private int warmupSeconds = 10;
    
    @Option(names = {"-r", "--ramp-up"}, description = "Ramp-up duration in seconds (default: 0)")
    private int rampUpSeconds = 0;
    
    @Option(names = {"-c", "--concurrency"}, description = "Maximum concurrent requests (default: 1000)")
    private int maxConcurrency = 1000;
    
    @Option(names = {"-m", "--method"}, description = "gRPC method to test (default: Echo)")
    private String method = "Echo";
    
    @Option(names = {"--tls"}, description = "Use TLS connection (default: false)")
    private boolean useTls = false;
    
    @Option(names = {"--timeout"}, description = "Request timeout in milliseconds (default: 5000)")
    private long timeoutMs = 5000;
    
    @Option(names = {"--report-interval"}, description = "Reporting interval in seconds (default: 10)")
    private int reportIntervalSeconds = 10;
    
    @Option(names = {"--output-format"}, description = "Output format: console, json, csv (default: console)")
    private String outputFormat = "console";
    
    @Option(names = {"--output-file"}, description = "Output file path (optional)")
    private String outputFile;
    
    @Option(names = {"--config"}, description = "Configuration file path (YAML)")
    private String configFile;
    
    @Option(names = {"--message"}, description = "Message payload for Echo requests (default: 'Hello, World!')")
    private String message = "Hello, World!";
    
    @Option(names = {"--verbose", "-v"}, description = "Enable verbose logging")
    private boolean verbose = false;
    
    @Parameters(description = "Additional arguments (unused)")
    private String[] args;
    
    public static void main(String[] args) {
        // Enable virtual threads for the main application
        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "1000");
        
        int exitCode = new CommandLine(new LoadTestClient()).execute(args);
        System.exit(exitCode);
    }
    
    @Override
    public Integer call() throws Exception {
        configureLogging();
        
        logger.info("Starting gRPC Load Test Client v1.0.0");
        logger.info("Java version: {} | Virtual threads supported: {}", 
                   System.getProperty("java.version"), 
                   Thread.class.getMethod("ofVirtual") != null);
        
        // Load configuration
        LoadTestConfig config = loadConfiguration();
        validateConfiguration(config);
        
        logger.info("Configuration: {}:{}, TPS: {}, Duration: {}s, Concurrency: {}", 
                   config.getTarget().getHost(), config.getTarget().getPort(),
                   config.getLoad().getTps(), config.getLoad().getDuration().getSeconds(),
                   config.getLoad().getMaxConcurrentRequests());
        
        // Initialize components
        MetricsCollector metricsCollector = new MetricsCollector();
        VirtualThreadExecutor executor = new VirtualThreadExecutor(config.getLoad().getMaxConcurrentRequests());
        ThroughputController throughputController = new ThroughputController(
                config.getLoad().getTps(), config.getLoad().getRampUpDuration());
        
        try (GrpcLoadTestClient grpcClient = new GrpcLoadTestClient(config);
             StatisticsReporter reporter = new StatisticsReporter(config, metricsCollector, 
                     throughputController, executor)) {
            
            // Execute load test
            return executeLoadTest(config, grpcClient, executor, metricsCollector, reporter, throughputController);
            
        } catch (Exception e) {
            logger.error("Load test execution failed", e);
            return 1;
        } finally {
            // Clean up
            if (metricsCollector != null) {
                metricsCollector.close();
            }
            if (executor != null) {
                executor.close();
            }
        }
    }
    
    private LoadTestConfig loadConfiguration() throws IOException {
        LoadTestConfig config;
        
        if (configFile != null) {
            logger.info("Loading configuration from: {}", configFile);
            config = LoadTestConfig.fromYaml(configFile);
        } else {
            config = LoadTestConfig.createDefault();
        }
        
        // Override with command line parameters
        config.getTarget().setHost(host);
        config.getTarget().setPort(port);
        config.getTarget().setMethod(method);
        config.getTarget().setUseTls(useTls);
        
        config.getLoad().setTps(tps);
        config.getLoad().setDuration(Duration.ofSeconds(durationSeconds));
        config.getLoad().setWarmupDuration(Duration.ofSeconds(warmupSeconds));
        config.getLoad().setRampUpDuration(Duration.ofSeconds(rampUpSeconds));
        config.getLoad().setMaxConcurrentRequests(maxConcurrency);
        
        config.getClient().setRequestTimeoutMs(timeoutMs);
        
        config.getReporting().setReportingIntervalSeconds(reportIntervalSeconds);
        config.getReporting().setOutputFormat(outputFormat);
        config.getReporting().setOutputFile(outputFile);
        
        return config;
    }
    
    private void validateConfiguration(LoadTestConfig config) {
        if (config.getLoad().getTps() <= 0) {
            throw new IllegalArgumentException("TPS must be positive");
        }
        if (config.getLoad().getDuration().isNegative() || config.getLoad().getDuration().isZero()) {
            throw new IllegalArgumentException("Duration must be positive");
        }
        if (config.getLoad().getMaxConcurrentRequests() <= 0) {
            throw new IllegalArgumentException("Max concurrency must be positive");
        }
        
        logger.info("Configuration validated successfully");
    }
    
    private int executeLoadTest(LoadTestConfig config, GrpcLoadTestClient grpcClient,
                               VirtualThreadExecutor executor, MetricsCollector metricsCollector,
                               StatisticsReporter reporter, ThroughputController throughputController) throws InterruptedException {
        
        AtomicBoolean testRunning = new AtomicBoolean(true);
        AtomicLong requestCounter = new AtomicLong(0);
        
        logger.info("Starting load test execution...");
        
        // Start real-time reporting
        reporter.startRealTimeReporting();
        
        // Warmup phase
        if (!config.getLoad().getWarmupDuration().isZero()) {
            logger.info("Starting warmup phase for {}s...", config.getLoad().getWarmupDuration().getSeconds());
            executePhase(config, grpcClient, executor, metricsCollector, throughputController, 
                        testRunning, requestCounter, config.getLoad().getWarmupDuration(), true);
            
            // Reset metrics after warmup
            metricsCollector.reset();
            throughputController.reset();
            logger.info("Warmup phase completed, metrics reset");
        }
        
        // Main test phase
        logger.info("Starting main test phase for {}s...", config.getLoad().getDuration().getSeconds());
        long startTime = System.currentTimeMillis();
        
        executePhase(config, grpcClient, executor, metricsCollector, throughputController,
                    testRunning, requestCounter, config.getLoad().getDuration(), false);
        
        long endTime = System.currentTimeMillis();
        long actualDurationMs = endTime - startTime;
        
        // Wait for remaining requests to complete
        logger.info("Waiting for remaining requests to complete...");
        executor.awaitCompletion(30, java.util.concurrent.TimeUnit.SECONDS);
        
        // Stop reporting and generate final report
        reporter.stopRealTimeReporting();
        Thread.sleep(1000); // Give time for final metrics to be recorded
        
        logger.info("Load test completed. Actual duration: {}ms", actualDurationMs);
        reporter.generateFinalReport();
        
        return 0;
    }
    
    private void executePhase(LoadTestConfig config, GrpcLoadTestClient grpcClient,
                             VirtualThreadExecutor executor, MetricsCollector metricsCollector,
                             ThroughputController throughputController, AtomicBoolean testRunning,
                             AtomicLong requestCounter, Duration phaseDuration, boolean isWarmup) 
                             throws InterruptedException {
        
        long phaseEndTime = System.currentTimeMillis() + phaseDuration.toMillis();
        
        while (System.currentTimeMillis() < phaseEndTime && testRunning.get()) {
            // Acquire permit from throughput controller
            if (!throughputController.acquirePermit()) {
                break; // Interrupted
            }
            
            // Submit request to virtual thread executor
            CompletableFuture<Void> future = executor.trySubmit(() -> {
                long requestId = requestCounter.incrementAndGet();
                
                try {
                    GrpcLoadTestClient.CallResult result = executeRequest(config, grpcClient, requestId);
                    
                    if (!isWarmup) {
                        metricsCollector.recordResult(result);
                    }
                    
                } catch (Exception e) {
                    logger.debug("Request {} failed with exception", requestId, e);
                    if (!isWarmup) {
                        // Record as failed request
                        GrpcLoadTestClient.CallResult failedResult = GrpcLoadTestClient.CallResult.failure(
                                requestId, 0, -1, e.getMessage());
                        metricsCollector.recordResult(failedResult);
                    }
                }
            });
            
            if (future == null) {
                // Executor is saturated, wait a bit
                Thread.sleep(1);
            }
        }
    }
    
    private GrpcLoadTestClient.CallResult executeRequest(LoadTestConfig config, 
                                                        GrpcLoadTestClient grpcClient, 
                                                        long requestId) {
        String method = config.getTarget().getMethod();
        
        switch (method.toLowerCase()) {
            case "echo":
                return grpcClient.executeEcho(message + " #" + requestId);
            
            case "computehash":
                return grpcClient.executeComputeHash("data-" + requestId, 1000, "sha256");
            
            case "healthcheck":
                return grpcClient.executeHealthCheck();
            
            default:
                logger.warn("Unknown method: {}, falling back to Echo", method);
                return grpcClient.executeEcho(message + " #" + requestId);
        }
    }
    
    private void configureLogging() {
        if (verbose) {
            // Enable debug logging
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
        } else {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");
        }
        
        // Reduce gRPC logging
        System.setProperty("org.slf4j.simpleLogger.log.io.grpc", "warn");
        System.setProperty("org.slf4j.simpleLogger.log.io.netty", "warn");
    }
}