package com.vajraedge.perftest.task;

import com.vajraedge.sdk.SimpleTaskResult;
import com.vajraedge.sdk.Task;
import com.vajraedge.sdk.TaskResult;
import com.vajraedge.sdk.TaskExecutionHelper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP task for testing HTTP endpoints.
 * Sends GET requests to a specified URL and measures response time.
 */
public class HttpTask implements Task {
    
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    
    private final String url;
    
    /**
     * Creates a new HTTP task.
     *
     * @param url The URL to send GET requests to
     */
    public HttpTask(String url) {
        this.url = url;
    }
    
    @Override
    public TaskResult execute() throws Exception {
        long startTime = System.nanoTime();
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();
            
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            
            if (success) {
                return TaskExecutionHelper.createSuccessResult(startTime, response.body().length());
            } else {
                return TaskExecutionHelper.createFailureResult(startTime, "HTTP " + response.statusCode());
            }
            
        } catch (Exception e) {
            return TaskExecutionHelper.createFailureResult(startTime, e.getMessage());
        }
    }
}
