package net.vajraedge.perftest.validation.checks;

import net.vajraedge.perftest.constants.PerformanceTestConstants;
import net.vajraedge.perftest.constants.TaskType;
import net.vajraedge.perftest.validation.CheckResult;
import net.vajraedge.perftest.validation.ValidationCheck;
import net.vajraedge.perftest.validation.ValidationContext;
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
import java.util.Map;

/**
 * Validates that the target HTTP service is reachable and responding correctly.
 * 
 * <p>This check sends a test HTTP GET request to the configured endpoint to verify:
 * <ul>
 *   <li>Service is accessible from the test environment</li>
 *   <li>Service responds with valid HTTP status code</li>
 *   <li>Response time is within acceptable limits</li>
 * </ul>
 * 
 * <p>Only applies to HTTP-based task types. Other task types are skipped.</p>
 * 
 * @see TaskType
 * @see ValidationCheck
 */
@Component
public class ServiceHealthCheck implements ValidationCheck {
    
    private static final Logger log = LoggerFactory.getLogger(ServiceHealthCheck.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(PerformanceTestConstants.VALIDATION_TIMEOUT_SECONDS);
    private static final long SLOW_RESPONSE_THRESHOLD_MS = 5000;
    
    @Override
    public String getName() {
        return "Service Health Check";
    }
    
    @Override
    public CheckResult execute(ValidationContext context) {
        String taskTypeStr = context.getTaskType();
        TaskType taskType = TaskType.fromString(taskTypeStr);
        
        // Only validate HTTP tasks
        if (taskType == null || !taskType.isHttpTask()) {
            log.debug("Skipping service health check for non-HTTP task type: {}", taskTypeStr);
            return CheckResult.skip(getName(), "Not applicable for task type: " + taskTypeStr);
        }
        
        Object taskParam = context.getTaskParameter();
        String url = extractUrlFromTaskParameter(taskParam);
        
        if (url == null || url.isBlank()) {
            log.warn("Service health check failed: URL parameter is missing");
            return CheckResult.fail(getName(), "URL parameter is required for HTTP tasks", 
                List.of("Provide a valid URL in the task parameters"));
        }
        
        log.info("Performing service health check for URL: {}", url);
        return validateHttpEndpoint(url);
    }
    
    @SuppressWarnings("unchecked")
    private String extractUrlFromTaskParameter(Object taskParam) {
        // Handle both string URL and object with parameters
        if (taskParam instanceof String) {
            return (String) taskParam;
        } else if (taskParam instanceof Map) {
            Map<String, Object> params = (Map<String, Object>) taskParam;
            Object urlObj = params.get("url");
            return urlObj instanceof String ? (String) urlObj : null;
        }
        return null;
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
            
            log.debug("Sending validation request to: {}", url);
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            long durationMs = Duration.ofNanos(System.nanoTime() - startTime).toMillis();
            int statusCode = response.statusCode();
            
            details.add(String.format("Status code: %d", statusCode));
            details.add(String.format("Response time: %d ms", durationMs));
            details.add(String.format("Content length: %d bytes", response.body().length()));
            
            log.info("Service responded: HTTP {} in {} ms", statusCode, durationMs);
            
            // Check status code
            if (statusCode >= 200 && statusCode < 300) {
                String message = String.format("Service is healthy (HTTP %d, %d ms)", statusCode, durationMs);
                
                // Warn if response is slow
                if (durationMs > SLOW_RESPONSE_THRESHOLD_MS) {
                    log.warn("Service responded slowly: {} ms (threshold: {} ms)", durationMs, SLOW_RESPONSE_THRESHOLD_MS);
                    details.add("⚠️ Warning: Baseline latency is high (>5s)");
                    return CheckResult.warn(getName(), message + " - but slow response time", details);
                }
                
                log.info("Service health check passed");
                return CheckResult.pass(getName(), message, Duration.ofMillis(durationMs));
            } else if (statusCode >= 400 && statusCode < 500) {
                log.warn("Service returned client error: HTTP {}", statusCode);
                details.add("❌ Client error - check URL and authentication");
                return CheckResult.fail(getName(), 
                    String.format("Service returned client error: HTTP %d", statusCode), details);
            } else if (statusCode >= 500) {
                log.error("Service returned server error: HTTP {}", statusCode);
                details.add("❌ Server error - target service may be down");
                return CheckResult.fail(getName(), 
                    String.format("Service returned server error: HTTP %d", statusCode), details);
            } else {
                log.warn("Service returned unexpected status code: HTTP {}", statusCode);
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
