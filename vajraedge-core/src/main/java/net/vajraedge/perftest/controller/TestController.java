package net.vajraedge.perftest.controller;

import net.vajraedge.perftest.distributed.DistributedMetricsCollector;
import net.vajraedge.perftest.distributed.WorkerInfo;
import net.vajraedge.perftest.distributed.WorkerManager;
import net.vajraedge.perftest.dto.DistributedTestRequest;
import net.vajraedge.perftest.dto.TestConfigRequest;
import net.vajraedge.perftest.dto.TestStatusResponse;
import net.vajraedge.perftest.sdk.plugin.PluginInfo;
import net.vajraedge.perftest.sdk.plugin.PluginRegistry;
import net.vajraedge.perftest.service.DistributedTestService;
import net.vajraedge.perftest.service.TestExecutionService;
import net.vajraedge.perftest.validation.PreFlightValidator;
import net.vajraedge.perftest.validation.ValidationContext;
import net.vajraedge.perftest.validation.ValidationResult;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for performance test management.
 */
@RestController
@RequestMapping("/api/tests")
public class TestController {
    
    private static final Logger logger = LoggerFactory.getLogger(TestController.class);
    
    private final TestExecutionService testExecutionService;
    private final PreFlightValidator preFlightValidator;
    private final PluginRegistry pluginRegistry;
    private final DistributedTestService distributedTestService;
    private final WorkerManager workerManager;
    
    public TestController(TestExecutionService testExecutionService, 
                         PreFlightValidator preFlightValidator,
                         PluginRegistry pluginRegistry,
                         DistributedTestService distributedTestService,
                         WorkerManager workerManager) {
        this.testExecutionService = testExecutionService;
        this.preFlightValidator = preFlightValidator;
        this.pluginRegistry = pluginRegistry;
        this.distributedTestService = distributedTestService;
        this.workerManager = workerManager;
    }
    
    /**
     * Start a new performance test.
     * Performs pre-flight validation before starting the test.
     * 
     * POST /api/tests
     */
    @PostMapping
    public ResponseEntity<?> startTest(@Valid @RequestBody TestConfigRequest config) {
        logger.info("Received request to start test: {}", config);
        
        try {
            // Perform pre-flight validation
            ValidationContext validationContext = ValidationContext.builder()
                .config(config)
                .build();
            
            ValidationResult validationResult = preFlightValidator.validate(validationContext);
            logger.info("Pre-flight validation completed: {} - {}", 
                validationResult.getStatus(), validationResult.getSummary());
            
            // If validation failed, return validation results and block test
            if (validationResult.getStatus() == ValidationResult.Status.FAIL) {
                logger.warn("Pre-flight validation failed, blocking test execution");
                Map<String, Object> response = new HashMap<>();
                response.put("status", "VALIDATION_FAILED");
                response.put("message", "Pre-flight validation failed - test blocked");
                response.put("validation", validationResult);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            
            // Validation passed or has warnings - proceed with test start
            String testId = testExecutionService.startTest(config);
            Map<String, Object> response = new HashMap<>();
            response.put("testId", testId);
            response.put("status", "RUNNING");
            response.put("message", "Test started successfully");
            
            // Include validation result if there were warnings
            if (validationResult.getStatus() == ValidationResult.Status.WARN) {
                response.put("validation", validationResult);
                logger.info("Test started with validation warnings: {}", testId);
            }
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            logger.error("Failed to start test", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to start test: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * Get status of a specific test.
     * 
     * GET /api/tests/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getTestStatus(@PathVariable String id) {
        logger.debug("Getting status for test: {}", id);
        
        TestStatusResponse status = testExecutionService.getTestStatus(id);
        if (status != null) {
            return ResponseEntity.ok(status);
        } else {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Test not found");
            error.put("testId", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }
    
    /**
     * Stop a running test.
     * 
     * DELETE /api/tests/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> stopTest(@PathVariable String id) {
        logger.info("Received request to stop test: {}", id);
        
        boolean stopped = testExecutionService.stopTest(id);
        Map<String, String> response = new HashMap<>();
        
        if (stopped) {
            response.put("testId", id);
            response.put("status", "STOPPED");
            response.put("message", "Test stopped successfully");
            return ResponseEntity.ok(response);
        } else {
            response.put("error", "Test not found or already completed");
            response.put("testId", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }
    
    /**
     * List all active tests.
     * 
     * GET /api/tests
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listTests() {
        logger.debug("Listing all active tests");
        
        Map<String, String> activeTests = testExecutionService.getActiveTestsStatus();
        Map<String, Object> response = new HashMap<>();
        response.put("activeTests", activeTests);
        response.put("count", activeTests.size());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get current metrics for a specific test (debug endpoint).
     * 
     * GET /api/tests/{id}/metrics
     */
    @GetMapping("/{id}/metrics")
    public ResponseEntity<?> getTestMetrics(@PathVariable String id) {
        logger.debug("Getting metrics for test: {}", id);
        
        var metrics = testExecutionService.getCurrentMetrics(id);
        if (metrics != null) {
            return ResponseEntity.ok(metrics);
        } else {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Test not found or no metrics available");
            error.put("testId", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }
    
    /**
     * List all available task plugins.
     * 
     * GET /api/tests/plugins
     */
    @GetMapping("/plugins")
    public ResponseEntity<Map<String, Object>> listPlugins() {
        logger.debug("Listing all available plugins");
        
        var allPlugins = pluginRegistry.getAllPlugins();
        
        // Group plugins by category
        Map<String, java.util.List<Map<String, Object>>> pluginsByCategory = new HashMap<>();
        
        for (PluginInfo plugin : allPlugins.values()) {
            String category = plugin.getCategory();
            
            Map<String, Object> pluginData = new HashMap<>();
            pluginData.put("name", plugin.getName());
            pluginData.put("displayName", plugin.getDisplayName());
            pluginData.put("description", plugin.metadata().description());
            pluginData.put("version", plugin.version());
            pluginData.put("author", plugin.author());
            pluginData.put("metadata", plugin.metadata().metadata());
            
            pluginsByCategory.computeIfAbsent(category, k -> new java.util.ArrayList<>()).add(pluginData);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("plugins", pluginsByCategory);
        response.put("totalCount", allPlugins.size());
        
        return ResponseEntity.ok(response);
    }
    
    // ============================================================================
    // Distributed Testing Endpoints
    // ============================================================================
    
    /**
     * Get all active distributed tests.
     * 
     * GET /api/tests/distributed
     */
    @GetMapping("/distributed")
    public ResponseEntity<?> getActiveDistributedTests() {
        logger.info("Fetching all active distributed tests");
        
        Map<String, DistributedTestService.DistributedTestInfo> activeTests = 
            distributedTestService.getActiveTests();
        
        Map<String, Object> response = new HashMap<>();
        response.put("activeTests", activeTests);
        response.put("count", activeTests.size());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Start a distributed performance test across multiple workers.
     * 
     * POST /api/tests/distributed
     */
    @PostMapping("/distributed")
    public ResponseEntity<?> startDistributedTest(@Valid @RequestBody DistributedTestRequest request) {
        logger.info("Received request to start distributed test: taskType={}, targetTps={}, duration={}s",
                request.getTaskType(), request.getTargetTps(), request.getDurationSeconds());
        
        try {
            DistributedTestService.DistributedTestResponse response = 
                distributedTestService.startDistributedTest(request);
            
            if (response.success()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            
        } catch (Exception e) {
            logger.error("Failed to start distributed test", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", "Failed to start distributed test: " + e.getMessage()
                    ));
        }
    }
    
    /**
     * Get status of a distributed test.
     * 
     * GET /api/tests/distributed/{testId}
     */
    @GetMapping("/distributed/{testId}")
    public ResponseEntity<?> getDistributedTestStatus(@PathVariable String testId) {
        logger.info("Fetching status for distributed test: {}", testId);
        
        DistributedTestService.DistributedTestInfo testInfo = distributedTestService.getTestInfo(testId);
        
        if (testInfo == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Test not found: " + testId));
        }
        
        DistributedMetricsCollector.AggregatedMetrics metrics = 
            distributedTestService.getTestMetrics(testId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("testInfo", testInfo);
        response.put("metrics", metrics);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Stop a distributed test.
     * 
     * DELETE /api/tests/distributed/{testId}
     */
    @DeleteMapping("/distributed/{testId}")
    public ResponseEntity<?> stopDistributedTest(@PathVariable String testId,
                                                  @RequestParam(defaultValue = "true") boolean graceful) {
        logger.info("Stopping distributed test: testId={}, graceful={}", testId, graceful);
        
        boolean stopped = distributedTestService.stopDistributedTest(testId, graceful);
        
        if (stopped) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Test stopped successfully"
            ));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "success", false,
                            "message", "Test not found: " + testId
                    ));
        }
    }
}
