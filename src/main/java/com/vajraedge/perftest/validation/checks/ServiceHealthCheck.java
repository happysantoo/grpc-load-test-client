package com.vajraedge.perftest.validation.checks;

import com.vajraedge.perftest.validation.CheckResult;
import com.vajraedge.perftest.validation.ValidationCheck;
import com.vajraedge.perftest.validation.ValidationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates that the target service is reachable and responding.
 */
@Component
public class ServiceHealthCheck implements ValidationCheck {
    
    private static final Logger log = LoggerFactory.getLogger(ServiceHealthCheck.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    
    @Override
    public String getName() {
        return "Service Health Check";
    }
    
    @Override
    public CheckResult execute(ValidationContext context) {
        String taskType = context.getTaskType();
        
        // Only validate HTTP tasks
        if (!"HTTP_GET".equals(taskType) && !"HTTP_POST".equals(taskType) && !"HTTP".equals(taskType)) {
            return CheckResult.skip(getName(), "Not applicable for task type: " + taskType);
        }
        
        Object taskParam = context.getTaskParameter();
        String url = taskParam instanceof String ? (String) taskParam : null;
        if (url == null || url.isBlank()) {
            return CheckResult.fail(getName(), "URL parameter is required for HTTP tasks", 
                List.of("Provide a valid URL in the task parameters"));
        }
        
        return validateHttpEndpoint(url);
    }
    
    private CheckResult validateHttpEndpoint(String url) {
        long startTime = System.nanoTime();
        List<String> details = new ArrayList<>();
        
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .GET()
                .build();
            
            log.debug("Validating HTTP endpoint: {}", url);
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            long durationMs = Duration.ofNanos(System.nanoTime() - startTime).toMillis();
            int statusCode = response.statusCode();
            
            details.add(String.format("Status code: %d", statusCode));
            details.add(String.format("Response time: %d ms", durationMs));
            details.add(String.format("Content length: %d bytes", response.body().length()));
            
            // Check status code
            if (statusCode >= 200 && statusCode < 300) {
                String message = String.format("Service is healthy (HTTP %d, %d ms)", statusCode, durationMs);
                
                // Warn if response is slow
                if (durationMs > 5000) {
                    details.add("⚠️ Warning: Baseline latency is high (>5s)");
                    return CheckResult.warn(getName(), message + " - but slow response time", details);
                }
                
                return CheckResult.pass(getName(), message, Duration.ofMillis(durationMs));
            } else if (statusCode >= 400 && statusCode < 500) {
                details.add("❌ Client error - check URL and authentication");
                return CheckResult.fail(getName(), 
                    String.format("Service returned client error: HTTP %d", statusCode), details);
            } else if (statusCode >= 500) {
                details.add("❌ Server error - target service may be down");
                return CheckResult.fail(getName(), 
                    String.format("Service returned server error: HTTP %d", statusCode), details);
            } else {
                details.add("⚠️ Unexpected status code");
                return CheckResult.warn(getName(), 
                    String.format("Service returned unexpected status: HTTP %d", statusCode), details);
            }
            
        } catch (IOException e) {
            log.error("Failed to connect to service: {}", url, e);
            details.add("❌ Connection failed: " + e.getMessage());
            details.add("Suggestions:");
            details.add("  - Verify the URL is correct");
            details.add("  - Check network connectivity");
            details.add("  - Verify firewall rules allow outbound connections");
            return CheckResult.fail(getName(), "Cannot reach service: " + url, details);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            details.add("❌ Validation interrupted");
            return CheckResult.fail(getName(), "Service health check was interrupted", details);
            
        } catch (Exception e) {
            log.error("Unexpected error during service validation: {}", url, e);
            details.add("❌ Unexpected error: " + e.getMessage());
            return CheckResult.fail(getName(), "Service validation failed with error", details);
        }
    }
}
