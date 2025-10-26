package com.vajraedge.perftest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot application entry point for the Performance Test Framework.
 * 
 * Provides:
 * - REST API for test configuration and control
 * - WebSocket support for real-time metrics streaming
 * - Web UI for monitoring and management
 * - Actuator endpoints for health checks
 */
@SpringBootApplication
@EnableScheduling
public class Application {
    
    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    
    public static void main(String[] args) {
        logger.info("Starting Performance Test Framework Application");
        SpringApplication.run(Application.class, args);
        logger.info("Application started successfully. Access dashboard at http://localhost:8080");
    }
}
