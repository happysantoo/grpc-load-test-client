# VajraEdge Plugins Module

This module contains example plugin implementations demonstrating how to create custom task plugins for VajraEdge load testing framework.

## Overview

VajraEdge plugins extend the framework with custom task types for load testing different protocols and systems. Each plugin implements the `TaskPlugin` interface from the `vajraedge-sdk` module.

## Available Plugins

### HTTP Plugins (`com.vajraedge.plugins.http`)

#### 1. HttpGetTask
Performs HTTP GET requests to test REST API endpoints.

**Features:**
- Custom headers support
- Configurable timeout (100-60000ms)
- Response validation
- HTTP/1.1 protocol

**Parameters:**
- `url` (required): Target URL
- `timeout` (optional, default: 5000ms): Request timeout
- `headers` (optional): Map of custom HTTP headers

**Usage Example:**
```java
TaskPlugin plugin = new HttpGetTask();
Map<String, Object> params = Map.of(
    "url", "https://api.example.com/users",
    "timeout", 3000,
    "headers", Map.of("Authorization", "Bearer token123")
);
plugin.initialize(params);
TaskResult result = plugin.execute();
```

#### 2. HttpPostTask
Performs HTTP POST requests with request bodies.

**Features:**
- Custom request body support
- Content-Type configuration
- Custom headers support
- Configurable timeout

**Parameters:**
- `url` (required): Target URL
- `body` (required): Request body content
- `contentType` (optional, default: application/json): Content-Type header
- `timeout` (optional, default: 5000ms): Request timeout
- `headers` (optional): Map of custom HTTP headers

**Usage Example:**
```java
TaskPlugin plugin = new HttpPostTask();
Map<String, Object> params = Map.of(
    "url", "https://api.example.com/users",
    "body", "{\"name\":\"John\",\"email\":\"john@example.com\"}",
    "contentType", "application/json",
    "timeout", 5000
);
plugin.initialize(params);
TaskResult result = plugin.execute();
```

#### 3. SleepTask
Pauses execution for specified duration to simulate I/O operations.

**Features:**
- Configurable sleep duration
- Useful for testing concurrency
- Minimal resource usage

**Parameters:**
- `duration` (optional, default: 100ms): Sleep duration in milliseconds (1-60000)

**Usage Example:**
```java
TaskPlugin plugin = new SleepTask();
Map<String, Object> params = Map.of("duration", 500);
plugin.initialize(params);
TaskResult result = plugin.execute();
```

### gRPC Plugins (`com.vajraedge.plugins.grpc`)

#### GrpcUnaryTask (Example Implementation)
Reference implementation showing how to create a gRPC plugin.

**Note:** This is a stub implementation. To create a functional gRPC plugin:

1. Add gRPC dependencies to your build:
```gradle
dependencies {
    implementation "io.grpc:grpc-netty-shaded:1.70.0"
    implementation "io.grpc:grpc-protobuf:1.70.0"
    implementation "io.grpc:grpc-stub:1.70.0"
}
```

2. Generate stubs from your .proto files:
```gradle
plugins {
    id 'com.google.protobuf' version '0.9.4'
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.1"
    }
    plugins {
        grpc {
            artifact = "io.grpc:protoc-gen-grpc-java:1.70.0"
        }
    }
    generateProtoTasks {
        all()*.plugins {
            grpc {}
        }
    }
}
```

3. Initialize ManagedChannel and stub in `initialize()`:
```java
this.channel = useTls 
    ? ManagedChannelBuilder.forTarget(target).useTransportSecurity().build()
    : ManagedChannelBuilder.forTarget(target).usePlaintext().build();
    
this.stub = YourServiceGrpc.newBlockingStub(channel)
    .withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS);
```

4. Make gRPC calls in `execute()`:
```java
YourRequest request = parseJsonToProto(requestJson);
YourResponse response = stub.yourMethod(request);
```

### Database Plugins (`com.vajraedge.plugins.database`)

#### PostgresQueryTask (Example Implementation)
Reference implementation showing how to create a database plugin.

**Note:** This is a stub implementation. To create a functional database plugin:

1. Add JDBC driver and connection pool dependencies:
```gradle
dependencies {
    implementation 'org.postgresql:postgresql:42.7.1'
    implementation 'com.zaxxer:HikariCP:5.1.0'
}
```

2. Initialize connection pool in `initialize()`:
```java
HikariConfig config = new HikariConfig();
config.setJdbcUrl(jdbcUrl);
config.setUsername(username);
config.setPassword(password);
config.setMaximumPoolSize(poolSize);
config.setConnectionTimeout(queryTimeoutSeconds * 1000);
this.dataSource = new HikariDataSource(config);
```

3. Execute queries in `execute()`:
```java
try (Connection conn = dataSource.getConnection();
     PreparedStatement stmt = conn.prepareStatement(query)) {
    
    stmt.setQueryTimeout(queryTimeoutSeconds);
    
    if (query.trim().toUpperCase().startsWith("SELECT")) {
        try (ResultSet rs = stmt.executeQuery()) {
            int rowCount = 0;
            while (rs.next()) {
                rowCount++;
            }
            return SimpleTaskResult.success(taskId, latency, rowCount,
                Map.of("rowCount", rowCount, "query", query));
        }
    } else {
        int updateCount = stmt.executeUpdate();
        return SimpleTaskResult.success(taskId, latency, updateCount,
            Map.of("updateCount", updateCount, "query", query));
    }
}
```

## Creating Custom Plugins

### Step 1: Add Dependency

Add the SDK dependency to your build.gradle:

```gradle
dependencies {
    implementation project(':vajraedge-sdk')
    // Add other dependencies (HTTP client, gRPC, JDBC, etc.)
}
```

### Step 2: Implement TaskPlugin Interface

```java
package com.yourcompany.plugins;

import com.vajraedge.sdk.TaskPlugin;
import com.vajraedge.sdk.TaskResult;
import com.vajraedge.sdk.TaskMetadata;
import com.vajraedge.sdk.SimpleTaskResult;
import com.vajraedge.sdk.annotations.VajraTask;
import java.util.Map;

@VajraTask(
    name = "YOUR_TASK",
    displayName = "Your Task Name",
    description = "Description of what your task does",
    category = "CATEGORY",
    version = "1.0.0",
    author = "Your Name"
)
public class YourCustomTask implements TaskPlugin {
    
    // Configuration fields
    private String someParameter;
    
    @Override
    public TaskMetadata getMetadata() {
        return TaskMetadata.builder()
            .name("YOUR_TASK")
            .displayName("Your Task Name")
            .description("Task description")
            .category("CATEGORY")
            .parameters(List.of(
                TaskMetadata.ParameterDef.requiredString(
                    "someParameter",
                    "Description of parameter"
                )
            ))
            .build();
    }
    
    @Override
    public void validateParameters(Map<String, Object> parameters) {
        if (!parameters.containsKey("someParameter")) {
            throw new IllegalArgumentException("someParameter is required");
        }
    }
    
    @Override
    public void initialize(Map<String, Object> parameters) {
        this.someParameter = parameters.get("someParameter").toString();
        // Initialize any resources (connections, clients, etc.)
    }
    
    @Override
    public TaskResult execute() throws Exception {
        long taskId = Thread.currentThread().threadId();
        long startTime = System.nanoTime();
        
        try {
            // Perform your task logic here
            
            long latency = System.nanoTime() - startTime;
            return SimpleTaskResult.success(taskId, latency);
            
        } catch (Exception e) {
            long latency = System.nanoTime() - startTime;
            return SimpleTaskResult.failure(taskId, latency, e.getMessage());
        }
    }
}
```

### Step 3: Package and Distribute

Build your plugin module:
```bash
./gradlew :vajraedge-plugins:build
```

The JAR will be created in `vajraedge-plugins/build/libs/`.

### Step 4: Use in Tests

Load your plugin in the VajraEdge controller or worker:

```java
// Plugin discovery will automatically find @VajraTask annotated classes
// Or register manually:
PluginRegistry.getInstance().registerPlugin(new YourCustomTask());
```

## Plugin Development Best Practices

### 1. Thread Safety
Plugins may be executed concurrently by multiple virtual threads. Ensure your plugin is thread-safe:
- Use immutable configuration fields
- Don't share mutable state between execute() calls
- Use thread-safe data structures if needed

### 2. Resource Management
Properly manage resources to avoid leaks:
- Initialize expensive resources once in `initialize()`
- Consider implementing a cleanup/shutdown method
- Use try-with-resources for connections

### 3. Error Handling
Handle errors gracefully:
- Catch specific exceptions in `execute()`
- Return meaningful error messages in TaskResult
- Log important errors using SLF4J

### 4. Performance
Optimize for high throughput:
- Minimize object allocation
- Reuse connections/clients
- Use connection pools for databases
- Avoid blocking calls when possible

### 5. Testing
Test your plugins thoroughly:
- Write unit tests with Spock
- Test parameter validation
- Test error scenarios
- Test concurrent execution

### 6. Documentation
Document your plugins:
- Clear JavaDoc for class and methods
- Parameter descriptions in TaskMetadata
- Usage examples in README
- Known limitations

## Module Structure

```
vajraedge-plugins/
├── build.gradle                    # Plugin module build configuration
├── README.md                        # This file
└── src/
    └── main/
        └── java/
            └── com/
                └── vajraedge/
                    └── plugins/
                        ├── http/             # HTTP protocol plugins
                        │   ├── HttpGetTask.java
                        │   ├── HttpPostTask.java
                        │   └── SleepTask.java
                        ├── grpc/             # gRPC protocol plugins (examples)
                        │   └── GrpcUnaryTask.java
                        └── database/         # Database plugins (examples)
                            └── PostgresQueryTask.java
```

## Building the Module

Build just the plugins module:
```bash
./gradlew :vajraedge-plugins:build
```

Build all modules including plugins:
```bash
./gradlew build
```

Run tests:
```bash
./gradlew :vajraedge-plugins:test
```

## Dependencies

The plugins module depends on:
- `vajraedge-sdk`: Core interfaces and annotations
- Java 21 SDK: Virtual threads, records, pattern matching

Optional dependencies (add as needed):
- gRPC libraries: For gRPC plugins
- JDBC drivers: For database plugins
- HTTP clients: For advanced HTTP plugins
- Messaging libraries: For Kafka/RabbitMQ plugins

## Contributing

When contributing new plugins:

1. Follow the existing package structure
2. Add comprehensive JavaDoc
3. Include usage examples
4. Write unit tests
5. Update this README
6. Submit a pull request

## License

Same as VajraEdge project license.

## See Also

- [VajraEdge SDK](../vajraedge-sdk/README.md): Core SDK documentation
- [VajraEdge Worker](../vajraedge-worker/README.md): Worker template documentation
- [Main README](../README.md): Project overview
