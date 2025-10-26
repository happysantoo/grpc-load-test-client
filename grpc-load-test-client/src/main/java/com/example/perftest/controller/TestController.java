package com.example.perftest.controller;

import com.example.perftest.dto.TestConfigRequest;
import com.example.perftest.dto.TestStatusResponse;
import com.example.perftest.service.TestExecutionService;
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
    
    public TestController(TestExecutionService testExecutionService) {
        this.testExecutionService = testExecutionService;
    }
    
    /**
     * Start a new performance test.
     * 
     * POST /api/tests
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> startTest(@Valid @RequestBody TestConfigRequest config) {
        logger.info("Received request to start test: {}", config);
        
        try {
            String testId = testExecutionService.startTest(config);
            Map<String, String> response = new HashMap<>();
            response.put("testId", testId);
            response.put("status", "RUNNING");
            response.put("message", "Test started successfully");
            
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
}
