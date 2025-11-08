# VajraEdge SDK

The VajraEdge SDK provides core interfaces and annotations for building custom load testing tasks and plugins.

## Overview

The SDK is a lightweight, zero-dependency Java 21 library (~9KB) that defines the contracts for:
- Task execution
- Plugin metadata and discovery
- Result reporting
- Parameter validation

## Key Components

### Task Interface

The fundamental interface for all executable tasks:

```java
@FunctionalInterface
public interface Task {
    TaskResult execute() throws Exception;
}
```

**Usage:**
```java
Task myTask = () -> {
    long startTime = System.nanoTime();
    // Perform work here
    long latency = System.nanoTime() - startTime;
    return SimpleTaskResult.success(1, latency);
};
```

### TaskResult Interface

Represents the outcome of task execution:

```java
public interface TaskResult {
    boolean isSuccess();
    long getLatencyNanos();
    long getLatencyMs();
    String getErrorMessage();
    Map<String, Object> getMetadata();
    int getResponseSize();
}
```

**Implementation:**

Use `SimpleTaskResult` factory methods:

```java
// Success
TaskResult success = SimpleTaskResult.success(
    taskId,
    latencyNanos,
    responseSize,
    Map.of("key", "value")
);

// Failure
TaskResult failure = SimpleTaskResult.failure(
    taskId,
    latencyNanos,
    "Error message",
    Map.of("errorCode", 500)
);
```

### TaskPlugin Interface

For discoverable, configurable task types:

```java
public interface TaskPlugin extends Task {
    TaskMetadata getMetadata();
    void validateParameters(Map<String, Object> parameters);
    void initialize(Map<String, Object> parameters);
}
```

**Example:**
```java
@VajraTask(
    name = "HTTP_GET",
    displayName = "HTTP GET Request",
    description = "Performs HTTP GET requests",
    category = "HTTP",
    version = "1.0.0",
    author = "VajraEdge"
)
public class HttpGetTask implements TaskPlugin {
    
    private String url;
    
    @Override
    public TaskMetadata getMetadata() {
        return TaskMetadata.builder()
            .name("HTTP_GET")
            .displayName("HTTP GET Request")
            .parameters(List.of(
                ParameterDef.requiredString("url", "Target URL")
            ))
            .build();
    }
    
    @Override
    public void validateParameters(Map<String, Object> parameters) {
        if (!parameters.containsKey("url")) {
            throw new IllegalArgumentException("url is required");
        }
    }
    
    @Override
    public void initialize(Map<String, Object> parameters) {
        this.url = parameters.get("url").toString();
    }
    
    @Override
    public TaskResult execute() throws Exception {
        long startTime = System.nanoTime();
        // Execute HTTP GET
        long latency = System.nanoTime() - startTime;
        return SimpleTaskResult.success(1, latency);
    }
}
```

### Annotations

#### @VajraTask

Marks a class as a discoverable task plugin:

```java
@VajraTask(
    name = "TASK_NAME",           // Required: Unique identifier
    displayName = "Display Name",  // Required: Human-readable name
    description = "Description",   // Required: What the task does
    category = "CATEGORY",         // Required: Grouping category
    version = "1.0.0",            // Required: Semantic version
    author = "Author Name"         // Required: Creator
)
public class MyTask implements TaskPlugin { }
```

#### @TaskParameter

Annotates plugin parameter fields (future use):

```java
public class MyTask implements TaskPlugin {
    @TaskParameter(
        name = "timeout",
        type = "integer",
        required = false,
        defaultValue = "5000",
        description = "Request timeout in milliseconds"
    )
    private int timeout;
}
```

### TaskMetadata

Describes plugin capabilities and parameters:

```java
TaskMetadata metadata = TaskMetadata.builder()
    .name("MY_TASK")
    .displayName("My Task")
    .description("Does something useful")
    .category("CUSTOM")
    .version("1.0.0")
    .author("Me")
    .parameters(List.of(
        ParameterDef.requiredString("param1", "First parameter"),
        ParameterDef.optionalInteger("param2", 100, 1, 1000, "Second parameter"),
        new ParameterDef(
            "param3",
            "boolean",
            false,
            true,
            "Third parameter",
            null, null, null
        )
    ))
    .metadata(Map.of("custom", "value"))
    .build();
```

### ParameterDef

Defines task parameters:

```java
// Required string parameter
ParameterDef.requiredString("name", "Description")

// Optional integer with range
ParameterDef.optionalInteger("count", 10, 1, 100, "Description")

// Optional string with default
ParameterDef.optionalString("mode", "default", "Description")

// Custom parameter
new ParameterDef(
    "name",
    "type",
    required,
    defaultValue,
    "description",
    minValue,
    maxValue,
    allowedValues
)
```

## Usage Patterns

### Simple Task

For one-off testing without plugin infrastructure:

```java
Task simpleTask = () -> {
    Thread.sleep(100);
    return SimpleTaskResult.success(1, 100_000_000L);
};
```

### Stateless Plugin

For tasks with no state:

```java
@VajraTask(name = "PING", ...)
public class PingTask implements TaskPlugin {
    @Override
    public TaskResult execute() {
        // No state needed
        return SimpleTaskResult.success(1, 1_000_000L);
    }
}
```

### Stateful Plugin

For tasks that need configuration:

```java
@VajraTask(name = "HTTP_GET", ...)
public class HttpGetTask implements TaskPlugin {
    
    private String url;
    private int timeout;
    private HttpClient client;
    
    @Override
    public void initialize(Map<String, Object> parameters) {
        this.url = parameters.get("url").toString();
        this.timeout = (int) parameters.getOrDefault("timeout", 5000);
        this.client = HttpClient.newHttpClient();
    }
    
    @Override
    public TaskResult execute() throws Exception {
        // Use initialized state
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMillis(timeout))
            .build();
        // Execute...
    }
}
```

### Resource Cleanup

For tasks with resources:

```java
@VajraTask(name = "DATABASE", ...)
public class DatabaseTask implements TaskPlugin {
    
    private HikariDataSource dataSource;
    
    @Override
    public void initialize(Map<String, Object> parameters) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(parameters.get("jdbcUrl").toString());
        this.dataSource = new HikariDataSource(config);
    }
    
    @Override
    public TaskResult execute() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            // Use connection
        }
    }
    
    // Implement cleanup (called by framework)
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
```

## Best Practices

### 1. Thread Safety

Tasks may execute concurrently:
- Use immutable state
- Avoid shared mutable state
- Use thread-safe collections if needed

```java
// Good: Immutable state
private final String url;

// Bad: Mutable state
private int counter;
```

### 2. Error Handling

Handle errors gracefully:

```java
@Override
public TaskResult execute() throws Exception {
    long startTime = System.nanoTime();
    try {
        // Task logic
        return SimpleTaskResult.success(id, latency);
    } catch (IOException e) {
        long latency = System.nanoTime() - startTime;
        return SimpleTaskResult.failure(id, latency, 
            "Connection failed: " + e.getMessage());
    }
}
```

### 3. Parameter Validation

Validate early and fail fast:

```java
@Override
public void validateParameters(Map<String, Object> parameters) {
    if (!parameters.containsKey("url")) {
        throw new IllegalArgumentException("url is required");
    }
    
    String url = parameters.get("url").toString();
    if (url.isBlank()) {
        throw new IllegalArgumentException("url cannot be empty");
    }
    
    try {
        URI.create(url);
    } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Invalid URL format");
    }
}
```

### 4. Resource Management

Initialize once, reuse:

```java
@Override
public void initialize(Map<String, Object> parameters) {
    // Create expensive resources once
    this.httpClient = HttpClient.newHttpClient();
}

@Override
public TaskResult execute() {
    // Reuse client across executions
    httpClient.send(request, handler);
}
```

### 5. Metadata

Provide comprehensive metadata:

```java
@Override
public TaskMetadata getMetadata() {
    return TaskMetadata.builder()
        .name("MY_TASK")
        .displayName("Human Readable Name")
        .description("Clear description of what the task does")
        .category("CATEGORY")
        .parameters(List.of(
            // Document all parameters
        ))
        .metadata(Map.of(
            "protocol", "HTTP",
            "blocking", "true",
            "version", "1.0.0"
        ))
        .build();
}
```

## Module Dependencies

The SDK has **zero external dependencies**:

```gradle
dependencies {
    // None! Pure Java 21
}
```

This keeps the SDK lightweight and avoids dependency conflicts.

## Integration

### As a Library

Add to your Gradle project:

```gradle
dependencies {
    implementation 'net.vajraedge:vajraedge-sdk:1.0.0'
}
```

Or as a project dependency:

```gradle
dependencies {
    implementation project(':vajraedge-sdk')
}
```

### With Spring Boot

The SDK works with any Java framework:

```java
@Component
public class MyPlugin implements TaskPlugin {
    
    @Autowired
    private SomeService service;
    
    @Override
    public TaskResult execute() {
        // Use Spring beans
        service.doSomething();
    }
}
```

### Standalone

Use without any framework:

```java
public class Main {
    public static void main(String[] args) {
        TaskPlugin plugin = new MyTask();
        plugin.initialize(Map.of("param", "value"));
        TaskResult result = plugin.execute();
        System.out.println("Success: " + result.isSuccess());
    }
}
```

## Examples

See the [vajraedge-plugins](../vajraedge-plugins/README.md) module for complete examples:
- HTTP GET/POST tasks
- Sleep task
- gRPC task (stub)
- Database task (stub)

## Building

Build the SDK module:

```bash
./gradlew :vajraedge-sdk:build
```

Output: `vajraedge-sdk/build/libs/vajraedge-sdk-1.0.0.jar` (~9KB)

## API Javadoc

Generate Javadoc:

```bash
./gradlew :vajraedge-sdk:javadoc
```

View: `vajraedge-sdk/build/docs/javadoc/index.html`

## License

Same as VajraEdge project license.
