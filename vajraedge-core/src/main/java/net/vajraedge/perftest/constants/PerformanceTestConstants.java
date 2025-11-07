package net.vajraedge.perftest.constants;

/**
 * Central constants file for VajraEdge performance testing framework.
 * 
 * Consolidates all magic numbers and configuration defaults for easier maintenance
 * and consistency across the codebase.
 */
public final class PerformanceTestConstants {
    
    private PerformanceTestConstants() {
        // Prevent instantiation
    }
    
    // ==================== Concurrency Limits ====================
    
    /**
     * Minimum allowed concurrent virtual threads
     */
    public static final int MIN_CONCURRENCY = 1;
    
    /**
     * Maximum allowed concurrent virtual threads
     */
    public static final int MAX_CONCURRENCY = 50_000;
    
    /**
     * Default starting concurrency for tests
     */
    public static final int DEFAULT_STARTING_CONCURRENCY = 10;
    
    /**
     * Default maximum concurrency for tests
     */
    public static final int DEFAULT_MAX_CONCURRENCY = 100;
    
    // ==================== TPS Limits ====================
    
    /**
     * Minimum transactions per second
     */
    public static final int MIN_TPS = 1;
    
    /**
     * Maximum transactions per second (realistic limit)
     */
    public static final int MAX_TPS = 100_000;
    
    // ==================== Duration Limits ====================
    
    /**
     * Minimum test duration in seconds
     */
    public static final long MIN_DURATION_SECONDS = 1;
    
    /**
     * Maximum test duration in seconds (24 hours)
     */
    public static final long MAX_DURATION_SECONDS = 86_400;
    
    /**
     * Default test duration in seconds (5 minutes)
     */
    public static final long DEFAULT_TEST_DURATION_SECONDS = 300;
    
    /**
     * Default ramp interval in seconds
     */
    public static final long DEFAULT_RAMP_INTERVAL_SECONDS = 30;
    
    // ==================== Ramp Strategy ====================
    
    /**
     * Default ramp step increment
     */
    public static final int DEFAULT_RAMP_STEP = 10;
    
    /**
     * Minimum ramp step
     */
    public static final int MIN_RAMP_STEP = 1;
    
    // ==================== Metrics Collection ====================
    
    /**
     * Default maximum number of latency samples to retain in memory
     */
    public static final int DEFAULT_MAX_LATENCY_HISTORY = 10_000;
    
    /**
     * Maximum timestamp history for TPS calculation
     */
    public static final int MAX_TIMESTAMP_HISTORY = 100_000;
    
    /**
     * Time window for current TPS calculation (5 seconds)
     */
    public static final long TPS_WINDOW_MS = 5_000;
    
    /**
     * Metrics update interval in milliseconds (500ms for real-time feel)
     */
    public static final int METRICS_UPDATE_INTERVAL_MS = 500;
    
    /**
     * Window retention time in milliseconds (1 hour)
     */
    public static final long WINDOW_RETENTION_MS = 3_600_000;
    
    // ==================== Executor Configuration ====================
    
    /**
     * Executor shutdown timeout in seconds
     */
    public static final int EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 5;
    
    /**
     * Executor forced shutdown timeout in seconds
     */
    public static final int EXECUTOR_FORCE_SHUTDOWN_TIMEOUT_SECONDS = 5;
    
    /**
     * Completion check interval in milliseconds
     */
    public static final int COMPLETION_CHECK_INTERVAL_MS = 100;
    
    // ==================== HTTP Task Defaults ====================
    
    /**
     * Default HTTP request timeout in seconds
     */
    public static final int DEFAULT_HTTP_TIMEOUT_SECONDS = 30;
    
    /**
     * Default URL for HTTP tasks
     */
    public static final String DEFAULT_HTTP_URL = "http://localhost:8081/api/products";
    
    // ==================== Sleep Task Defaults ====================
    
    /**
     * Default sleep duration in milliseconds
     */
    public static final int DEFAULT_SLEEP_MS = 10;
    
    /**
     * Minimum sleep duration in milliseconds
     */
    public static final int MIN_SLEEP_MS = 1;
    
    /**
     * Maximum sleep duration in milliseconds (1 minute)
     */
    public static final int MAX_SLEEP_MS = 60_000;
    
    // ==================== Validation ====================
    
    /**
     * Minimum number of CPUs recommended for performance testing
     */
    public static final int MIN_RECOMMENDED_CPUS = 2;
    
    /**
     * Maximum error message length before truncation
     */
    public static final int MAX_ERROR_MESSAGE_LENGTH = 100;
    
    /**
     * Validation timeout in seconds
     */
    public static final int VALIDATION_TIMEOUT_SECONDS = 30;
    
    // ==================== WebSocket Configuration ====================
    
    /**
     * WebSocket message broker prefix
     */
    public static final String WEBSOCKET_BROKER_PREFIX = "/topic";
    
    /**
     * WebSocket application destination prefix
     */
    public static final String WEBSOCKET_APP_PREFIX = "/app";
    
    /**
     * WebSocket endpoint for metrics streaming
     */
    public static final String WEBSOCKET_METRICS_ENDPOINT = "/metrics";
    
    // ==================== Test Configuration ====================
    
    /**
     * Maximum starting concurrency for validation
     */
    public static final int MAX_STARTING_CONCURRENCY = 10_000;
    
    /**
     * Minimum concurrency for all test modes
     */
    public static final int MIN_CONCURRENT_THREADS = 1;
    
    // ==================== Resource Limits ====================
    
    /**
     * Estimated memory per virtual thread (1 MB)
     */
    public static final long MEMORY_PER_THREAD_BYTES = 1_000_000;
    
    /**
     * Overhead memory requirement (100 MB)
     */
    public static final long OVERHEAD_MEMORY_BYTES = 100_000_000;
}
