package net.vajraedge.plugins.http;

import net.vajraedge.sdk.SimpleTaskResult;
import net.vajraedge.sdk.TaskResult;
import net.vajraedge.sdk.TaskMetadata;
import net.vajraedge.sdk.TaskMetadata.ParameterDef;
import net.vajraedge.sdk.TaskPlugin;
import net.vajraedge.sdk.ParameterValidator;
import net.vajraedge.sdk.TaskExecutionHelper;
import net.vajraedge.sdk.annotations.VajraTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * HTTP POST request task plugin for load testing REST APIs with payloads.
 * Supports custom headers, request bodies, timeouts, and response validation.
 *
 * @since 1.1.0
 */
@VajraTask(
    name = "HTTP_POST",
    displayName = "HTTP POST Request",
    description = "Performs HTTP POST requests to test REST API endpoints with request bodies",
    category = "HTTP",
    version = "1.0.0",
    author = "VajraEdge"
)
public class HttpPostTask implements TaskPlugin {
    
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .build();
    
    private String url;
    private String body;
    private String contentType;
    private int timeoutMs;
    private Map<String, String> headers;
    
    public HttpPostTask() {
        this.url = "http://localhost:8080/api/test";
        this.body = "{}";
        this.contentType = "application/json";
        this.timeoutMs = 5000;
        this.headers = Map.of();
    }
    
    @Override
    public TaskMetadata getMetadata() {
        return TaskMetadata.builder()
            .name("HTTP_POST")
            .displayName("HTTP POST Request")
            .description("Performs HTTP POST requests to test REST API endpoints with request bodies")
            .category("HTTP")
            .parameters(List.of(
                ParameterDef.requiredString(
                    "url",
                    "Target URL for the HTTP POST request"
                ),
                ParameterDef.requiredString(
                    "body",
                    "Request body content"
                ),
                ParameterDef.optionalString(
                    "contentType",
                    "application/json",
                    "Content-Type header value"
                ),
                ParameterDef.optionalInteger(
                    "timeout",
                    5000,
                    100,
                    60000,
                    "Request timeout in milliseconds (100-60000)"
                ),
                new ParameterDef(
                    "headers",
                    "Map<String,String>",
                    false,
                    Map.of(),
                    "Custom HTTP headers (Content-Type will be added automatically)",
                    null,
                    null,
                    null
                )
            ))
            .metadata(Map.of(
                "protocol", "HTTP/1.1",
                "method", "POST",
                "blocking", "true"
            ))
            .build();
    }
    
    @Override
    public void validateParameters(Map<String, Object> parameters) {
        ParameterValidator.requireValidUrl(parameters, "url");
        ParameterValidator.requireString(parameters, "body");
        ParameterValidator.validateTimeout(parameters, "timeout");
    }
    
    @Override
    public void initialize(Map<String, Object> parameters) {
        this.url = parameters.get("url").toString();
        this.body = parameters.get("body").toString();
        this.contentType = ParameterValidator.getStringOrDefault(parameters, "contentType", "application/json");
        this.timeoutMs = ParameterValidator.getIntegerOrDefault(parameters, "timeout", 5000);
        
        if (parameters.containsKey("headers") && parameters.get("headers") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, String> headerMap = (Map<String, String>) parameters.get("headers");
            this.headers = headerMap;
        }
    }
    
    @Override
    public TaskResult execute() throws Exception {
        long startTime = System.nanoTime();
        
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body));
            
            // Add custom headers (will override Content-Type if provided)
            headers.forEach(requestBuilder::header);
            
            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            int responseSize = response.body() != null ? response.body().length() : 0;
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            
            if (success) {
                return TaskExecutionHelper.createSuccessResult(startTime, responseSize,
                    Map.of("statusCode", response.statusCode(), "url", url, "bodySize", body.length()));
            } else {
                return TaskExecutionHelper.createFailureResult(startTime,
                    "HTTP " + response.statusCode(),
                    Map.of("statusCode", response.statusCode(), "url", url));
            }
            
        } catch (Exception e) {
            return TaskExecutionHelper.createFailureResult(startTime,
                "Request failed: " + e.getMessage(),
                Map.of("url", url, "error", e.getClass().getSimpleName()));
        }
    }
}
