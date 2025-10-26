package com.vajraedge.perftest.service;

import com.vajraedge.perftest.core.SimpleTaskResult;
import com.vajraedge.perftest.core.Task;
import com.vajraedge.perftest.core.TaskFactory;
import com.vajraedge.perftest.dto.TestConfigRequest;
import com.vajraedge.perftest.dto.TestStatusResponse;
import com.vajraedge.perftest.runner.PerformanceTestRunner;
import com.vajraedge.perftest.runner.TestResult;
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
    
    private final Map<String, TestExecution> activeTests = new ConcurrentHashMap<>();
    private final Map<String, TestResult> completedTests = new ConcurrentHashMap<>();
    
    public Map<String, TestExecution> getActiveTests() {
        return activeTests;
    }
    
    /**
     * Start a new performance test.
     */
    public String startTest(TestConfigRequest config) {
        String testId = UUID.randomUUID().toString();
        logger.info("Starting test {} with config: {}", testId, config);
        
        TaskFactory taskFactory = createTaskFactory(config.getTaskType(), config.getTaskParameter());
        
        PerformanceTestRunner runner = new PerformanceTestRunner(
                taskFactory,
                config.getMaxConcurrency(),
                config.getTargetTps(),
                Duration.ofSeconds(config.getRampUpDurationSeconds())
        );
        
        TestExecution execution = new TestExecution(testId, config, runner);
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
     * Stop a running test.
     */
    public boolean stopTest(String testId) {
        TestExecution execution = activeTests.get(testId);
        if (execution != null) {
            execution.getRunner().close();
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
    
    private TestStatusResponse buildStatusResponse(TestExecution execution) {
        TestStatusResponse response = new TestStatusResponse();
        response.setTestId(execution.getTestId());
        response.setStatus(execution.getStatus());
        response.setStartTime(execution.getStartTime());
        response.setEndTime(execution.getEndTime());
        response.setConfiguration(execution.getConfig());
        
        long elapsed = Duration.between(execution.getStartTime(), LocalDateTime.now()).getSeconds();
        response.setElapsedSeconds(elapsed);
        
        // Build current metrics
        TestStatusResponse.CurrentMetrics metrics = new TestStatusResponse.CurrentMetrics();
        PerformanceTestRunner runner = execution.getRunner();
        
        metrics.setActiveTasks(runner.getExecutor().getActiveTasks());
        metrics.setTotalRequests(runner.getExecutor().getSubmittedTasks());
        metrics.setSuccessfulRequests(runner.getExecutor().getCompletedTasks());
        metrics.setFailedRequests(0L); // Would need to track failures
        
        response.setCurrentMetrics(metrics);
        
        return response;
    }
    
    private TaskFactory createTaskFactory(String taskType, Integer taskParameter) {
        return switch (taskType.toUpperCase()) {
            case "SLEEP" -> taskId -> createSleepTask(taskId, taskParameter);
            case "CPU" -> this::createCpuTask;
            default -> taskId -> createSleepTask(taskId, taskParameter);
        };
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
    
    /**
     * Internal class to track test execution state.
     */
    public static class TestExecution {
        private final String testId;
        private final TestConfigRequest config;
        private final PerformanceTestRunner runner;
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
        
        public String getTestId() {
            return testId;
        }
        
        public TestConfigRequest getConfig() {
            return config;
        }
        
        public PerformanceTestRunner getRunner() {
            return runner;
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
