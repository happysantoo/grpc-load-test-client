package net.vajraedge.perftest.suite;

import net.vajraedge.perftest.dto.TestConfigRequest;
import net.vajraedge.perftest.dto.TestStatusResponse;
import net.vajraedge.perftest.metrics.MetricsCollector;
import net.vajraedge.perftest.metrics.MetricsSnapshot;
import net.vajraedge.perftest.service.MetricsService;
import net.vajraedge.perftest.service.TestExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Service responsible for executing test suites.
 * 
 * <p>Orchestrates scenario execution in sequential or parallel mode,
 * manages correlation context, handles task mix distribution, and
 * aggregates results.
 * 
 * @author Santhosh Kuppusamy
 * @since 1.0
 */
@Service
public class SuiteExecutor {
    private static final Logger log = LoggerFactory.getLogger(SuiteExecutor.class);
    
    private final TestExecutionService testExecutionService;
    private final MetricsService metricsService;
    private final Map<String, SuiteExecution> activeSuites;
    private final ExecutorService executorService;
    
    public SuiteExecutor(TestExecutionService testExecutionService, MetricsService metricsService) {
        this.testExecutionService = testExecutionService;
        this.metricsService = metricsService;
        this.activeSuites = new ConcurrentHashMap<>();
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }
    
    /**
     * Start executing a test suite.
     * 
     * @param suite the test suite to execute
     * @return suite result future
     */
    public CompletableFuture<SuiteResult> executeSuite(TestSuite suite) {
        String suiteId = suite.getSuiteId();
        log.info("Starting suite execution: suiteId={}, mode={}, scenarios={}", 
            suiteId, suite.getExecutionMode(), suite.getScenarioCount());
        
        SuiteExecution execution = new SuiteExecution(suite);
        activeSuites.put(suiteId, execution);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                execution.startTime = Instant.now();
                execution.status = SuiteResult.SuiteStatus.RUNNING;
                
                if (suite.getExecutionMode() == ExecutionMode.SEQUENTIAL) {
                    executeSequential(suite, execution);
                } else {
                    executeParallel(suite, execution);
                }
                
                execution.endTime = Instant.now();
                execution.status = hasFailedScenarios(execution) 
                    ? SuiteResult.SuiteStatus.FAILED 
                    : SuiteResult.SuiteStatus.COMPLETED;
                
                SuiteResult result = buildSuiteResult(suite, execution);
                log.info("Suite completed: suiteId={}, status={}, duration={}ms, successful={}/{}", 
                    suiteId, result.getStatus(), result.getDurationMillis(), 
                    result.getSuccessfulScenarios(), result.getTotalScenarios());
                
                return result;
                
            } catch (Exception e) {
                log.error("Suite execution failed: suiteId={}", suiteId, e);
                execution.status = SuiteResult.SuiteStatus.FAILED;
                execution.endTime = Instant.now();
                execution.errorMessage = e.getMessage();
                
                return buildSuiteResult(suite, execution);
                
            } finally {
                activeSuites.remove(suiteId);
            }
        }, executorService);
    }
    
    /**
     * Stop a running suite.
     * 
     * @param suiteId the suite ID
     * @return true if suite was stopped
     */
    public boolean stopSuite(String suiteId) {
        SuiteExecution execution = activeSuites.get(suiteId);
        if (execution == null) {
            log.warn("Suite not found: suiteId={}", suiteId);
            return false;
        }
        
        log.info("Stopping suite: suiteId={}", suiteId);
        execution.stopped = true;
        execution.status = SuiteResult.SuiteStatus.STOPPED;
        
        // Stop all running scenarios
        execution.runningScenarios.forEach(scenarioId -> {
            try {
                testExecutionService.stopTest(scenarioId);
            } catch (Exception e) {
                log.error("Failed to stop scenario: scenarioId={}", scenarioId, e);
            }
        });
        
        return true;
    }
    
    /**
     * Get status of a running suite.
     * 
     * @param suiteId the suite ID
     * @return suite execution status, or null if not found
     */
    public SuiteExecutionStatus getStatus(String suiteId) {
        SuiteExecution execution = activeSuites.get(suiteId);
        if (execution == null) {
            return null;
        }
        
        return new SuiteExecutionStatus(
            suiteId,
            execution.suite.getName(),
            execution.status,
            execution.startTime,
            new ArrayList<>(execution.scenarioResults),
            execution.suite.getScenarioCount()
        );
    }
    
    private void executeSequential(TestSuite suite, SuiteExecution execution) {
        CorrelationContext correlationContext = suite.isUseCorrelation() ? new CorrelationContext() : null;
        
        for (TestScenario scenario : suite.getScenarios()) {
            if (execution.stopped) {
                log.info("Suite stopped, skipping remaining scenarios: suiteId={}", suite.getSuiteId());
                break;
            }
            
            ScenarioResult result = executeScenario(scenario, correlationContext, execution);
            execution.scenarioResults.add(result);
            
            log.info("Scenario completed: scenarioId={}, status={}, duration={}ms",
                scenario.getScenarioId(), result.getStatus(), result.getDurationMillis());
        }
        
        if (correlationContext != null) {
            execution.correlationStats.put("variableCount", correlationContext.getVariableKeys().size());
            execution.correlationStats.put("poolCount", correlationContext.getPoolKeys().size());
        }
    }
    
    private void executeParallel(TestSuite suite, SuiteExecution execution) {
        CorrelationContext correlationContext = suite.isUseCorrelation() ? new CorrelationContext() : null;
        
        List<CompletableFuture<ScenarioResult>> futures = new ArrayList<>();
        
        for (TestScenario scenario : suite.getScenarios()) {
            CompletableFuture<ScenarioResult> future = CompletableFuture.supplyAsync(
                () -> executeScenario(scenario, correlationContext, execution),
                executorService
            );
            futures.add(future);
        }
        
        // Wait for all scenarios to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        // Collect results
        for (CompletableFuture<ScenarioResult> future : futures) {
            try {
                ScenarioResult result = future.get();
                execution.scenarioResults.add(result);
                
                log.info("Scenario completed: scenarioId={}, status={}, duration={}ms",
                    result.getScenarioId(), result.getStatus(), result.getDurationMillis());
                    
            } catch (Exception e) {
                log.error("Failed to get scenario result", e);
            }
        }
        
        if (correlationContext != null) {
            execution.correlationStats.put("variableCount", correlationContext.getVariableKeys().size());
            execution.correlationStats.put("poolCount", correlationContext.getPoolKeys().size());
        }
    }
    
    private ScenarioResult executeScenario(TestScenario scenario, CorrelationContext correlationContext, SuiteExecution execution) {
        String scenarioId = scenario.getScenarioId();
        log.info("Starting scenario: scenarioId={}, name={}", scenarioId, scenario.getName());
        
        execution.runningScenarios.add(scenarioId);
        
        Instant startTime = Instant.now();
        ScenarioResult.ScenarioStatus status = ScenarioResult.ScenarioStatus.RUNNING;
        MetricsSnapshot metrics = null;
        Map<String, Long> taskMixDistribution = new HashMap<>();
        String errorMessage = null;
        
        try {
            TestConfigRequest config = scenario.getConfig();
            
            // Apply task mix if configured
            if (scenario.hasTaskMix()) {
                // Task mix will be handled by creating appropriate task instances
                taskMixDistribution = trackTaskMix(scenario.getTaskMix(), config);
            }
            
            // Start the test (reuse existing test execution)
            String testId = testExecutionService.startTest(config);
            
            // Wait for test to complete
            TestStatusResponse testStatus;
            do {
                if (execution.stopped) {
                    testExecutionService.stopTest(testId);
                    status = ScenarioResult.ScenarioStatus.STOPPED;
                    break;
                }
                Thread.sleep(500);
                testStatus = testExecutionService.getTestStatus(testId);
            } while (testStatus != null && !"COMPLETED".equals(testStatus.getStatus()) 
                    && !"FAILED".equals(testStatus.getStatus()) 
                    && !"STOPPED".equals(testStatus.getStatus()));
            
            // Get metrics from the test execution
            TestExecutionService.TestExecution testExec = testExecutionService.getActiveTests().get(testId);
            if (testExec != null && testExec.getMetricsCollector() != null) {
                MetricsSnapshot snapshot = testExec.getMetricsCollector().getSnapshot();
                metrics = snapshot;
            }
            
            if (status != ScenarioResult.ScenarioStatus.STOPPED) {
                status = ScenarioResult.ScenarioStatus.COMPLETED;
            }
            
        } catch (Exception e) {
            log.error("Scenario execution failed: scenarioId={}", scenarioId, e);
            status = ScenarioResult.ScenarioStatus.FAILED;
            errorMessage = e.getMessage();
        } finally {
            execution.runningScenarios.remove(scenarioId);
        }
        
        Instant endTime = Instant.now();
        
        return ScenarioResult.builder()
            .scenarioId(scenarioId)
            .scenarioName(scenario.getName())
            .status(status)
            .startTime(startTime)
            .endTime(endTime)
            .metrics(metrics)
            .taskMixDistribution(taskMixDistribution)
            .errorMessage(errorMessage)
            .build();
    }
    
    private Map<String, Long> trackTaskMix(TaskMix taskMix, TestConfigRequest config) {
        Map<String, Long> distribution = new HashMap<>();
        
        // Calculate expected distribution based on weights
        // Using maxConcurrency as a proxy for total operations
        long totalOps = config.getMaxConcurrency() * config.getTestDurationSeconds();
        
        for (Map.Entry<String, Integer> entry : taskMix.getWeights().entrySet()) {
            String taskType = entry.getKey();
            double percentage = taskMix.getPercentage(taskType);
            long expectedCount = (long) (totalOps * percentage / 100.0);
            distribution.put(taskType, expectedCount);
        }
        
        return distribution;
    }
    
    private boolean hasFailedScenarios(SuiteExecution execution) {
        return execution.scenarioResults.stream()
            .anyMatch(r -> r.getStatus() == ScenarioResult.ScenarioStatus.FAILED);
    }
    
    private SuiteResult buildSuiteResult(TestSuite suite, SuiteExecution execution) {
        SuiteResult.Builder builder = SuiteResult.builder()
            .suiteId(suite.getSuiteId())
            .suiteName(suite.getName())
            .status(execution.status)
            .startTime(execution.startTime)
            .endTime(execution.endTime)
            .scenarioResults(execution.scenarioResults)
            .executionMode(suite.getExecutionMode());
        
        if (execution.errorMessage != null) {
            builder.errorMessage(execution.errorMessage);
        }
        
        // Add suite-level metrics
        if (!execution.correlationStats.isEmpty()) {
            execution.correlationStats.forEach(builder::suiteMetric);
        }
        
        return builder.build();
    }
    
    /**
     * Internal class to track suite execution state.
     */
    private static class SuiteExecution {
        final TestSuite suite;
        Instant startTime;
        Instant endTime;
        SuiteResult.SuiteStatus status = SuiteResult.SuiteStatus.PENDING;
        final List<ScenarioResult> scenarioResults = new CopyOnWriteArrayList<>();
        final Set<String> runningScenarios = ConcurrentHashMap.newKeySet();
        final Map<String, Object> correlationStats = new ConcurrentHashMap<>();
        volatile boolean stopped = false;
        String errorMessage;
        
        SuiteExecution(TestSuite suite) {
            this.suite = suite;
        }
    }
    
    /**
     * Status snapshot of a running suite.
     */
    public record SuiteExecutionStatus(
        String suiteId,
        String suiteName,
        SuiteResult.SuiteStatus status,
        Instant startTime,
        List<ScenarioResult> completedScenarios,
        int totalScenarios
    ) {
        public int getCompletedCount() {
            return completedScenarios.size();
        }
        
        public int getPendingCount() {
            return totalScenarios - completedScenarios.size();
        }
        
        public double getProgress() {
            if (totalScenarios == 0) return 0.0;
            return (completedScenarios.size() * 100.0) / totalScenarios;
        }
    }
}
