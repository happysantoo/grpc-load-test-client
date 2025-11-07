package net.vajraedge.perftest.controller;

import jakarta.validation.Valid;
import net.vajraedge.perftest.dto.*;
import net.vajraedge.perftest.service.MetricsService;
import net.vajraedge.perftest.suite.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * REST controller for test suite operations.
 * 
 * <p>Provides endpoints for:
 * <ul>
 *   <li>Starting test suites with multiple scenarios</li>
 *   <li>Monitoring suite execution progress</li>
 *   <li>Retrieving suite results</li>
 *   <li>Stopping running suites</li>
 * </ul>
 * 
 * @author Santhosh Kuppusamy
 * @since 1.0
 */
@RestController
@RequestMapping("/api/suites")
public class SuiteController {
    private static final Logger log = LoggerFactory.getLogger(SuiteController.class);
    
    private final SuiteExecutor suiteExecutor;
    private final MetricsService metricsService;
    private final Map<String, CompletableFuture<SuiteResult>> suiteResults;
    
    public SuiteController(SuiteExecutor suiteExecutor, MetricsService metricsService) {
        this.suiteExecutor = suiteExecutor;
        this.metricsService = metricsService;
        this.suiteResults = new ConcurrentHashMap<>();
    }
    
    /**
     * Start a new test suite.
     * 
     * @param request suite configuration
     * @return suite status response
     */
    @PostMapping("/start")
    public ResponseEntity<SuiteStatusResponse> startSuite(@Valid @RequestBody SuiteConfigRequest request) {
        try {
            log.info("Starting test suite: suiteId={}, name={}, scenarios={}", 
                request.getSuiteId(), request.getName(), request.getScenarios().size());
            
            // Convert request to domain model
            TestSuite suite = convertToSuite(request);
            
            // Execute suite asynchronously
            CompletableFuture<SuiteResult> future = suiteExecutor.executeSuite(suite);
            suiteResults.put(suite.getSuiteId(), future);
            
            // Return immediate status
            SuiteExecutor.SuiteExecutionStatus status = suiteExecutor.getStatus(suite.getSuiteId());
            if (status != null) {
                SuiteStatusResponse response = convertToStatusResponse(status);
                return ResponseEntity.accepted().body(response);
            }
            
            // Fallback response
            SuiteStatusResponse response = new SuiteStatusResponse();
            response.setSuiteId(suite.getSuiteId());
            response.setSuiteName(suite.getName());
            response.setStatus("PENDING");
            response.setTotalScenarios(suite.getScenarioCount());
            response.setCompletedScenarios(0);
            response.setProgress(0.0);
            
            return ResponseEntity.accepted().body(response);
            
        } catch (Exception e) {
            log.error("Failed to start suite", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get status of a running suite.
     * 
     * @param suiteId suite ID
     * @return suite status
     */
    @GetMapping("/{suiteId}/status")
    public ResponseEntity<SuiteStatusResponse> getSuiteStatus(@PathVariable String suiteId) {
        SuiteExecutor.SuiteExecutionStatus status = suiteExecutor.getStatus(suiteId);
        
        if (status != null) {
            // Suite is still running
            return ResponseEntity.ok(convertToStatusResponse(status));
        }
        
        // Check if suite completed
        CompletableFuture<SuiteResult> future = suiteResults.get(suiteId);
        if (future != null && future.isDone()) {
            try {
                SuiteResult result = future.get();
                return ResponseEntity.ok(SuiteStatusResponse.fromResult(result));
            } catch (Exception e) {
                log.error("Failed to get suite result: suiteId={}", suiteId, e);
            }
        }
        
        return ResponseEntity.notFound().build();
    }
    
    /**
     * Get final results of a completed suite.
     * 
     * @param suiteId suite ID
     * @return suite results
     */
    @GetMapping("/{suiteId}/results")
    public ResponseEntity<SuiteStatusResponse> getSuiteResults(@PathVariable String suiteId) {
        CompletableFuture<SuiteResult> future = suiteResults.get(suiteId);
        
        if (future == null) {
            return ResponseEntity.notFound().build();
        }
        
        if (!future.isDone()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("Retry-After", "5")
                .build();
        }
        
        try {
            SuiteResult result = future.get();
            return ResponseEntity.ok(SuiteStatusResponse.fromResult(result));
            
        } catch (Exception e) {
            log.error("Failed to get suite results: suiteId={}", suiteId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Stop a running suite.
     * 
     * @param suiteId suite ID
     * @return response indicating success or failure
     */
    @DeleteMapping("/{suiteId}/stop")
    public ResponseEntity<Void> stopSuite(@PathVariable String suiteId) {
        boolean stopped = suiteExecutor.stopSuite(suiteId);
        
        if (stopped) {
            log.info("Suite stopped: suiteId={}", suiteId);
            return ResponseEntity.ok().build();
        } else {
            log.warn("Suite not found or already stopped: suiteId={}", suiteId);
            return ResponseEntity.notFound().build();
        }
    }
    
    private TestSuite convertToSuite(SuiteConfigRequest request) {
        TestSuite.Builder suiteBuilder = TestSuite.builder()
            .suiteId(request.getSuiteId())
            .name(request.getName())
            .description(request.getDescription())
            .executionMode(ExecutionMode.valueOf(request.getExecutionMode()))
            .useCorrelation(request.isUseCorrelation());
        
        if (request.getMetadata() != null) {
            request.getMetadata().forEach(suiteBuilder::metadata);
        }
        
        // Convert scenarios
        for (ScenarioConfigRequest scenarioReq : request.getScenarios()) {
            TestScenario.Builder scenarioBuilder = TestScenario.builder()
                .scenarioId(scenarioReq.getScenarioId())
                .name(scenarioReq.getName())
                .description(scenarioReq.getDescription())
                .config(scenarioReq.getConfig());
            
            // Add task mix if present
            if (scenarioReq.getTaskMix() != null && !scenarioReq.getTaskMix().getWeights().isEmpty()) {
                TaskMix taskMix = new TaskMix();
                scenarioReq.getTaskMix().getWeights().forEach(taskMix::addTask);
                scenarioBuilder.taskMix(taskMix);
            }
            
            if (scenarioReq.getMetadata() != null) {
                scenarioBuilder.metadata(scenarioReq.getMetadata());
            }
            
            suiteBuilder.addScenario(scenarioBuilder.build());
        }
        
        return suiteBuilder.build();
    }
    
    private SuiteStatusResponse convertToStatusResponse(SuiteExecutor.SuiteExecutionStatus status) {
        SuiteStatusResponse response = new SuiteStatusResponse();
        response.setSuiteId(status.suiteId());
        response.setSuiteName(status.suiteName());
        response.setStatus(status.status().name());
        response.setStartTime(status.startTime());
        response.setTotalScenarios(status.totalScenarios());
        response.setCompletedScenarios(status.getCompletedCount());
        response.setProgress(status.getProgress());
        
        // Add completed scenario details
        for (ScenarioResult scenarioResult : status.completedScenarios()) {
            response.getScenarios().add(
                SuiteStatusResponse.ScenarioStatusDto.fromResult(scenarioResult, metricsService)
            );
        }
        
        // Count successful and failed
        int successful = 0;
        int failed = 0;
        for (ScenarioResult result : status.completedScenarios()) {
            if (result.isSuccessful()) {
                successful++;
            } else if (result.getStatus() == ScenarioResult.ScenarioStatus.FAILED) {
                failed++;
            }
        }
        response.setSuccessfulScenarios(successful);
        response.setFailedScenarios(failed);
        
        return response;
    }
}
