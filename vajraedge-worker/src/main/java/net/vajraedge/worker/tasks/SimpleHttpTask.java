package net.vajraedge.worker.tasks;

import net.vajraedge.sdk.SimpleTaskResult;
import net.vajraedge.sdk.Task;
import net.vajraedge.sdk.TaskResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Simple HTTP task for load testing HTTP endpoints.
 */
public class SimpleHttpTask implements Task {
    
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    
    private final String url;
    
    public SimpleHttpTask() {
        // Default URL - can be overridden via environment variable
        this.url = System.getenv().getOrDefault("HTTP_TASK_URL", "http://localhost:8080/actuator/health");
    }
    
    @Override
    public TaskResult execute() throws Exception {
        long startTimeNanos = System.nanoTime();
        long taskId = Thread.currentThread().threadId();
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            
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
