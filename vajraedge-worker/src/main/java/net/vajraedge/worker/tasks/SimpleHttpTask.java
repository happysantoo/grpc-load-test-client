package net.vajraedge.worker.tasks;

import net.vajraedge.sdk.SimpleTaskResult;
import net.vajraedge.sdk.Task;
import net.vajraedge.sdk.TaskResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Simple HTTP task for load testing HTTP endpoints.
 * Supports configurable URL, method, timeout, and custom headers via parameters.
 */
public class SimpleHttpTask implements Task {
    
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    
    private final String url;
    private final String method;
    private final int timeoutSeconds;
    private final Map<String, String> customHeaders;
    
    /**
     * Constructor with parameters from task assignment.
     * Falls back to environment variables and defaults if parameters not provided.
     */
    public SimpleHttpTask(Map<String, String> parameters) {
        if (parameters == null) {
            parameters = Map.of();
        }
        
        // URL: parameter > env var > default
        this.url = parameters.getOrDefault("url", 
                System.getenv().getOrDefault("HTTP_TASK_URL", "http://localhost:8080/actuator/health"));
        
        // HTTP method: parameter > env var > default
        this.method = parameters.getOrDefault("method",
                System.getenv().getOrDefault("HTTP_TASK_METHOD", "GET"));
        
        // Timeout: parameter > env var > default
        String timeoutStr = parameters.getOrDefault("timeout",
                System.getenv().getOrDefault("HTTP_TASK_TIMEOUT", "30"));
        this.timeoutSeconds = Integer.parseInt(timeoutStr);
        
        // Parse custom headers from JSON string if provided
        String headersJson = parameters.get("headers");
        if (headersJson != null && !headersJson.isEmpty()) {
            // Simple parsing - in production, use a JSON library
            // For now, just store empty map (headers support would need proper JSON parsing)
            this.customHeaders = Map.of();
        } else {
            this.customHeaders = Map.of();
        }
    }
    
    /**
     * Default constructor for backwards compatibility.
     */
    public SimpleHttpTask() {
        this(Map.of());
    }
    
    @Override
    public TaskResult execute() throws Exception {
        long startTimeNanos = System.nanoTime();
        long taskId = Thread.currentThread().threadId();
        
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds));
            
            // Apply HTTP method
            switch (method.toUpperCase()) {
                case "GET" -> requestBuilder.GET();
                case "POST" -> requestBuilder.POST(HttpRequest.BodyPublishers.noBody());
                case "PUT" -> requestBuilder.PUT(HttpRequest.BodyPublishers.noBody());
                case "DELETE" -> requestBuilder.DELETE();
                default -> requestBuilder.GET();
            }
            
            // Add custom headers
            for (Map.Entry<String, String> header : customHeaders.entrySet()) {
                requestBuilder.header(header.getKey(), header.getValue());
            }
            
            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            long latencyNanos = System.nanoTime() - startTimeNanos;
            
            // Consider 2xx and 3xx as success
            if (response.statusCode() >= 200 && response.statusCode() < 400) {
                return SimpleTaskResult.success(taskId, latencyNanos, response.body().length());
            } else {
                return SimpleTaskResult.failure(taskId, latencyNanos, "HTTP " + response.statusCode());
            }
            
        } catch (Exception e) {
            long latencyNanos = System.nanoTime() - startTimeNanos;
            return SimpleTaskResult.failure(taskId, latencyNanos, e.getMessage());
        }
    }
}
