package net.vajraedge.perftest.service;

import net.vajraedge.perftest.concurrency.ConcurrencyController;
import net.vajraedge.perftest.concurrency.LinearRampStrategy;
import net.vajraedge.perftest.concurrency.LoadTestMode;
import net.vajraedge.perftest.concurrency.RampStrategy;
import net.vajraedge.perftest.concurrency.RampStrategyType;
import net.vajraedge.perftest.concurrency.StepRampStrategy;
import net.vajraedge.sdk.SimpleTaskResult;
import net.vajraedge.sdk.Task;
import net.vajraedge.sdk.TaskFactory;
import net.vajraedge.perftest.dto.TestConfigRequest;
import net.vajraedge.perftest.dto.TestStatusResponse;
import net.vajraedge.sdk.metrics.MetricsCollector;
import net.vajraedge.perftest.runner.ConcurrencyBasedTestRunner;
import net.vajraedge.perftest.runner.PerformanceTestRunner;
import net.vajraedge.perftest.runner.TestResult;
import net.vajraedge.sdk.TaskPlugin;
import net.vajraedge.perftest.sdk.plugin.PluginRegistry;
import net.vajraedge.perftest.task.HttpTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing test execution lifecycle.
 */
@Service
public class TestExecutionService {
    
    private static final Logger logger = LoggerFactory.getLogger(TestExecutionService.class);
    private static final int MAX_CONCURRENT_TESTS = 10;
    
    private final PluginRegistry pluginRegistry;
    private final Map<String, TestExecution> activeTests = new ConcurrentHashMap<>();
    private final Map<String, TestResult> completedTests = new ConcurrentHashMap<>();
    
    public TestExecutionService(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }
    
    public Map<String, TestExecution> getActiveTests() {
        return activeTests;
    }
    
    /**
     * Start a new performance test.
     * 
     * @param config test configuration
     * @return test ID
     * @throws IllegalStateException if maximum concurrent tests limit is reached
     */
    public String startTest(TestConfigRequest config) {
        // DoS protection: limit number of concurrent tests
        if (activeTests.size() >= MAX_CONCURRENT_TESTS) {
            throw new IllegalStateException(
                String.format("Maximum concurrent tests limit reached (%d). Please wait for existing tests to complete.",
                    MAX_CONCURRENT_TESTS));
        }
        
        String testId = UUID.randomUUID().toString();
        logger.info("Starting test {} with config: {}", testId, config);
        
        TaskFactory taskFactory = createTaskFactory(config.getTaskType(), config.getTaskParameter());
        
        // Determine which runner to use based on mode
        LoadTestMode mode = config.getMode() != null ? config.getMode() : LoadTestMode.CONCURRENCY_BASED;
        
        TestExecution execution;
        if (mode == LoadTestMode.CONCURRENCY_BASED || mode == LoadTestMode.RATE_LIMITED) {
            // Use new concurrency-based runner
            ConcurrencyBasedTestRunner runner = createConcurrencyBasedRunner(config, taskFactory);
            execution = new TestExecution(testId, config, runner);
            activeTests.put(testId, execution);
            
            // Run test asynchronously
            CompletableFuture.runAsync(() -> {
                try {
                    TestResult result = runner.run(Duration.ofSeconds(config.getTestDurationSeconds()));
                    execution.setStatus("COMPLETED");
                    execution.setEndTime(LocalDateTime.now());
                    completedTests.put(testId, result);
                    logger.info("Test {} completed successfully", testId);
                } catch (Exception e) {
                    execution.setStatus("FAILED");
                    execution.setEndTime(LocalDateTime.now());
                    logger.error("Test {} failed", testId, e);
                } finally {
                    runner.close();
                    activeTests.remove(testId);
                }
            });
        } else {
            // Fallback to rate-based runner for backward compatibility
            PerformanceTestRunner runner = createRateBasedRunner(config, taskFactory);
            execution = new TestExecution(testId, config, runner);
            activeTests.put(testId, execution);
            
            // Run test asynchronously
            CompletableFuture.runAsync(() -> {
                try {
                    TestResult result = runner.run(Duration.ofSeconds(config.getTestDurationSeconds()));
                    execution.setStatus("COMPLETED");
                    execution.setEndTime(LocalDateTime.now());
                    completedTests.put(testId, result);
                    logger.info("Test {} completed successfully", testId);
                } catch (Exception e) {
                    execution.setStatus("FAILED");
                    execution.setEndTime(LocalDateTime.now());
                    logger.error("Test {} failed", testId, e);
                } finally {
                    runner.close();
                    activeTests.remove(testId);
                }
            });
        }
        
        return testId;
    }
    
    /**
     * Get status of a test.
     */
    public TestStatusResponse getTestStatus(String testId) {
        TestExecution execution = activeTests.get(testId);
        
        if (execution != null) {
            return buildStatusResponse(execution);
        }
        
        // Check completed tests
        if (completedTests.containsKey(testId)) {
            TestStatusResponse response = new TestStatusResponse(testId, "COMPLETED");
            // Could add more details from completed test result
            return response;
        }
        
        return null;
    }
    
    /**
     * Get pending tasks count for a specific test.
     * 
     * @param testId test ID
     * @return number of pending tasks, or 0 if test not found
     */
    public int getPendingTasks(String testId) {
        TestExecution execution = activeTests.get(testId);
        if (execution != null) {
            return execution.getPendingTasks();
        }
        return 0;
    }
    
    /**
     * Stop a running test.
     */
    public boolean stopTest(String testId) {
        TestExecution execution = activeTests.get(testId);
        if (execution != null) {
            execution.closeRunner();
            execution.setStatus("STOPPED");
            execution.setEndTime(LocalDateTime.now());
            activeTests.remove(testId);
            logger.info("Test {} stopped", testId);
            return true;
        }
        return false;
    }
    
    /**
     * Get all active test IDs with their status.
     */
    public Map<String, String> getActiveTestsStatus() {
        Map<String, String> result = new ConcurrentHashMap<>();
        activeTests.forEach((id, exec) -> result.put(id, exec.getStatus()));
        return result;
    }
    
    /**
     * Get current metrics for a specific test (debug).
     */
    public Object getCurrentMetrics(String testId) {
        TestExecution execution = activeTests.get(testId);
        if (execution == null) {
            return null;
        }
        
        var snapshot = execution.getMetricsCollector().getSnapshot();
        var metricsService = new MetricsService();
        return metricsService.convertToResponse(testId, snapshot);
    }
    
    /**
     * Get metrics snapshot for a specific test.
     * 
     * @param testId test identifier
     * @return MetricsSnapshot or null if test not found
     */
    public net.vajraedge.sdk.metrics.MetricsSnapshot getTestMetrics(String testId) {
        TestExecution execution = activeTests.get(testId);
        if (execution == null) {
            return null;
        }
        
        MetricsCollector collector = execution.getMetricsCollector();
        return collector != null ? collector.getSnapshot() : null;
    }
    
    private TestStatusResponse buildStatusResponse(TestExecution execution) {
        TestStatusResponse response = new TestStatusResponse();
        response.setTestId(execution.getTestId());
        response.setStatus(execution.getStatus());
        response.setStartTime(execution.getStartTime());
        response.setEndTime(execution.getEndTime());
        response.setConfiguration(execution.getConfig());
        
        // Use endTime if test is completed, otherwise use current time
        LocalDateTime timeToUse = execution.getEndTime() != null ? 
                execution.getEndTime() : LocalDateTime.now();
        long elapsed = Duration.between(execution.getStartTime(), timeToUse).getSeconds();
        response.setElapsedSeconds(elapsed);
        
        // Build current metrics
        TestStatusResponse.CurrentMetrics metrics = new TestStatusResponse.CurrentMetrics();
        
        metrics.setActiveTasks(execution.getActiveTasks());
        metrics.setTotalRequests(execution.getSubmittedTasks());
        metrics.setSuccessfulRequests(execution.getCompletedTasks());
        metrics.setFailedRequests(0L); // Would need to track failures
        
        response.setCurrentMetrics(metrics);
        
        return response;
    }
    
    private TaskFactory createTaskFactory(String taskType, Object taskParameter) {
        String taskTypeName = taskType.toUpperCase();
        
        // First, try to find a plugin with this name
        if (pluginRegistry.hasPlugin(taskTypeName)) {
            logger.debug("Using plugin for task type: {}", taskTypeName);
            return taskId -> createPluginTask(taskTypeName, taskParameter);
        }
        
        // Fall back to hard-coded tasks for backward compatibility
        logger.debug("Using hard-coded implementation for task type: {}", taskTypeName);
        return switch (taskTypeName) {
            case "SLEEP" -> taskId -> createSleepTask(taskId, getIntParameter(taskParameter));
            case "CPU" -> this::createCpuTask;
            case "HTTP" -> taskId -> createHttpTask(getStringParameter(taskParameter));
            default -> {
                logger.warn("Unknown task type: {}, defaulting to SLEEP", taskTypeName);
                yield taskId -> createSleepTask(taskId, getIntParameter(taskParameter));
            }
        };
    }
    
    private Task createPluginTask(String pluginName, Object taskParameter) {
        try {
            TaskPlugin plugin = pluginRegistry.createPluginInstance(pluginName);
            
            // Initialize plugin with parameters if provided
            if (taskParameter != null) {
                Map<String, Object> parameters = convertToParameterMap(taskParameter);
                plugin.validateParameters(parameters);
                plugin.initialize(parameters);
            }
            
            return plugin;
        } catch (Exception e) {
            logger.error("Failed to create plugin instance: {}", pluginName, e);
            throw new IllegalStateException("Failed to create plugin: " + pluginName, e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertToParameterMap(Object taskParameter) {
        if (taskParameter instanceof Map) {
            return (Map<String, Object>) taskParameter;
        }
        
        // For simple parameters, create a default parameter map
        // This handles backward compatibility with single-value parameters
        return Map.of("value", taskParameter);
    }
    
    private int getIntParameter(Object param) {
        if (param instanceof Integer) {
            return (Integer) param;
        } else if (param instanceof String) {
            return Integer.parseInt((String) param);
        }
        return 100; // default
    }
    
    private String getStringParameter(Object param) {
        if (param instanceof String) {
            return (String) param;
        }
        return "http://localhost:8081/api/products"; // default
    }
    
    private Task createSleepTask(long taskId, int sleepMs) {
        return () -> {
            long start = System.nanoTime();
            try {
                Thread.sleep(sleepMs);
                long latency = System.nanoTime() - start;
                return SimpleTaskResult.success(taskId, latency);
            } catch (Exception e) {
                long latency = System.nanoTime() - start;
                return SimpleTaskResult.failure(taskId, latency, e.getMessage());
            }
        };
    }
    
    private Task createCpuTask(long taskId) {
        return () -> {
            long start = System.nanoTime();
            try {
                int sum = 0;
                for (int i = 0; i < 10000; i++) {
                    sum += i;
                }
                long latency = System.nanoTime() - start;
                return SimpleTaskResult.success(taskId, latency, sum);
            } catch (Exception e) {
                long latency = System.nanoTime() - start;
                return SimpleTaskResult.failure(taskId, latency, e.getMessage());
            }
        };
    }
    
    private Task createHttpTask(String url) {
        return new HttpTask(url);
    }
    
    /**
     * Create a concurrency-based test runner.
     */
    private ConcurrencyBasedTestRunner createConcurrencyBasedRunner(TestConfigRequest config, TaskFactory taskFactory) {
        // Build ramp strategy based on config
        RampStrategy rampStrategy;
        RampStrategyType strategyType = config.getRampStrategyType() != null ? 
            config.getRampStrategyType() : RampStrategyType.STEP;
        
        long sustainDuration = config.getSustainDurationSeconds() != null ? 
            config.getSustainDurationSeconds() : 0L;
        
        if (strategyType == RampStrategyType.LINEAR) {
            int startConcurrency = config.getStartingConcurrency() != null ? config.getStartingConcurrency() : 10;
            int maxConcurrency = config.getMaxConcurrency() != null ? config.getMaxConcurrency() : 100;
            long rampDuration = config.getRampDurationSeconds() != null ? config.getRampDurationSeconds() : 60L;
            
            rampStrategy = new LinearRampStrategy(startConcurrency, maxConcurrency, rampDuration, sustainDuration);
        } else {
            int startConcurrency = config.getStartingConcurrency() != null ? config.getStartingConcurrency() : 10;
            int rampStep = config.getRampStep() != null ? config.getRampStep() : 10;
            long rampInterval = config.getRampIntervalSeconds() != null ? config.getRampIntervalSeconds() : 30L;
            int maxConcurrency = config.getMaxConcurrency() != null ? config.getMaxConcurrency() : 100;
            
            rampStrategy = new StepRampStrategy(startConcurrency, rampStep, rampInterval, maxConcurrency, sustainDuration);
        }
        
        // Build concurrency controller
        LoadTestMode mode = config.getMode() != null ? config.getMode() : LoadTestMode.CONCURRENCY_BASED;
        Integer maxTpsLimit = config.getMaxTpsLimit();
        
        ConcurrencyController controller = new ConcurrencyController(rampStrategy, mode, maxTpsLimit);
        
        return new ConcurrencyBasedTestRunner(taskFactory, controller);
    }
    
    /**
     * Create a rate-based test runner (backward compatibility).
     * This is only used if mode is not CONCURRENCY_BASED or RATE_LIMITED.
     */
    private PerformanceTestRunner createRateBasedRunner(TestConfigRequest config, TaskFactory taskFactory) {
        // For backward compatibility, use maxConcurrency as both limit and target TPS if targetTps not set
        int maxConcurrency = config.getMaxConcurrency() != null ? config.getMaxConcurrency() : 100;
        int targetTps = maxConcurrency / 10; // Default: assume 10 users per TPS
        long rampUpSeconds = 0L;
        
        return new PerformanceTestRunner(
            taskFactory,
            maxConcurrency,
            targetTps,
            Duration.ofSeconds(rampUpSeconds)
        );
    }
    
    /**
     * Internal class to track test execution state.
     * Supports both PerformanceTestRunner and ConcurrencyBasedTestRunner.
     */
    public static class TestExecution {
        private final String testId;
        private final TestConfigRequest config;
        private final Object runner; // Can be PerformanceTestRunner or ConcurrencyBasedTestRunner
        private final LocalDateTime startTime;
        private String status;
        private LocalDateTime endTime;
        
        public TestExecution(String testId, TestConfigRequest config, PerformanceTestRunner runner) {
            this.testId = testId;
            this.config = config;
            this.runner = runner;
            this.startTime = LocalDateTime.now();
            this.status = "RUNNING";
        }
        
        public TestExecution(String testId, TestConfigRequest config, ConcurrencyBasedTestRunner runner) {
            this.testId = testId;
            this.config = config;
            this.runner = runner;
            this.startTime = LocalDateTime.now();
            this.status = "RUNNING";
        }
        
        public String getTestId() {
            return testId;
        }
        
        public TestConfigRequest getConfig() {
            return config;
        }
        
        public PerformanceTestRunner getRunner() {
            if (runner instanceof PerformanceTestRunner) {
                return (PerformanceTestRunner) runner;
            }
            throw new IllegalStateException("Runner is not a PerformanceTestRunner");
        }
        
        public ConcurrencyBasedTestRunner getConcurrencyRunner() {
            if (runner instanceof ConcurrencyBasedTestRunner) {
                return (ConcurrencyBasedTestRunner) runner;
            }
            throw new IllegalStateException("Runner is not a ConcurrencyBasedTestRunner");
        }
        
        public MetricsCollector getMetricsCollector() {
            if (runner instanceof PerformanceTestRunner) {
                return ((PerformanceTestRunner) runner).getMetricsCollector();
            } else if (runner instanceof ConcurrencyBasedTestRunner) {
                return ((ConcurrencyBasedTestRunner) runner).getMetricsCollector();
            }
            throw new IllegalStateException("Unknown runner type");
        }
        
        public long getActiveTasks() {
            if (runner instanceof PerformanceTestRunner) {
                return ((PerformanceTestRunner) runner).getExecutor().getActiveTasks();
            } else if (runner instanceof ConcurrencyBasedTestRunner) {
                // For concurrency-based tests, active tasks = active virtual users
                return ((ConcurrencyBasedTestRunner) runner).getActiveVirtualUsers();
            }
            return 0;
        }
        
        public long getSubmittedTasks() {
            if (runner instanceof PerformanceTestRunner) {
                return ((PerformanceTestRunner) runner).getExecutor().getSubmittedTasks();
            } else if (runner instanceof ConcurrencyBasedTestRunner) {
                return ((ConcurrencyBasedTestRunner) runner).getExecutor().getSubmittedTasks();
            }
            return 0;
        }
        
        public long getCompletedTasks() {
            if (runner instanceof PerformanceTestRunner) {
                return ((PerformanceTestRunner) runner).getExecutor().getCompletedTasks();
            } else if (runner instanceof ConcurrencyBasedTestRunner) {
                return ((ConcurrencyBasedTestRunner) runner).getExecutor().getCompletedTasks();
            }
            return 0;
        }
        
        public int getPendingTasks() {
            if (runner instanceof PerformanceTestRunner) {
                return ((PerformanceTestRunner) runner).getExecutor().getPendingTasks();
            } else if (runner instanceof ConcurrencyBasedTestRunner) {
                return ((ConcurrencyBasedTestRunner) runner).getExecutor().getPendingTasks();
            }
            return 0;
        }
        
        public net.vajraedge.perftest.executor.VirtualThreadTaskExecutor getExecutor() {
            if (runner instanceof PerformanceTestRunner) {
                return ((PerformanceTestRunner) runner).getExecutor();
            } else if (runner instanceof ConcurrencyBasedTestRunner) {
                return ((ConcurrencyBasedTestRunner) runner).getExecutor();
            }
            return null;
        }
        
        public void closeRunner() {
            if (runner instanceof PerformanceTestRunner) {
                ((PerformanceTestRunner) runner).close();
            } else if (runner instanceof ConcurrencyBasedTestRunner) {
                ((ConcurrencyBasedTestRunner) runner).close();
            }
        }
        
        public LocalDateTime getStartTime() {
            return startTime;
        }
        
        public String getStatus() {
            return status;
        }
        
        public void setStatus(String status) {
            this.status = status;
        }
        
        public LocalDateTime getEndTime() {
            return endTime;
        }
        
        public void setEndTime(LocalDateTime endTime) {
            this.endTime = endTime;
        }
    }
}
