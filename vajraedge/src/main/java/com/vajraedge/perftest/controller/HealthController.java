package com.vajraedge.perftest.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Health check controller to verify the application is running.
 */
@RestController
@RequestMapping("/api")
public class HealthController {
    
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now());
        response.put("application", "Performance Test Framework");
        response.put("version", "1.0.0");
        return response;
    }
    
    @GetMapping("/")
    public Map<String, String> root() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Performance Test Framework API");
        response.put("documentation", "/api/health");
        return response;
    }
}
