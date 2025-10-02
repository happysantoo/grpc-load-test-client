# Generic Performance Test Framework

A lightweight, extensible performance testing framework built on Java 21 Virtual Threads. This framework follows the Single Responsibility Principle and provides a clean abstraction for testing **any workload** - not just gRPC.

## 🎯 Key Features

- **Protocol-Agnostic**: Test gRPC, HTTP, databases, message queues, or any custom workload
- **Simple & Focused**: Each class has a single, clear responsibility
- **Java 21 Virtual Threads**: Efficiently handle thousands of concurrent operations
- **Extensible**: Easy to add new task types and reporters
- **Production-Ready**: Comprehensive error handling and metrics

## 🏗️ Architecture

### Core Abstractions

```
com.example.perftest.core
├── Task              - Interface for any unit of work
├── TaskResult        - Result of task execution
├── SimpleTaskResult  - Default implementation
├── TaskFactory       - Creates task instances
└── TaskExecutor      - Executes tasks with concurrency control
```

### Framework Components

```
com.example.perftest
├── executor/         - Task execution implementations
│   └── VirtualThreadTaskExecutor
├── metrics/          - Metrics collection and aggregation
│   ├── MetricsCollector
│   ├── MetricsSnapshot
│   └── PercentileStats
├── rate/             - Rate limiting and pacing
│   └── RateController
└── runner/           - Test orchestration
    ├── PerformanceTestRunner
    └── TestResult
```

## 🚀 Quick Start

### 1. Define Your Task

```java
// Example: HTTP Request Task
Task httpTask = () -> {
    long start = System.nanoTime();
    try {
        HttpResponse response = httpClient.send(request);
        long latency = System.nanoTime() - start;
        return SimpleTaskResult.success(taskId, latency, response.body().length());
    } catch (Exception e) {
        long latency = System.nanoTime() - start;
        return SimpleTaskResult.failure(taskId, latency, e.getMessage());
    }
};
```

### 2. Create a Task Factory

```java
TaskFactory factory = taskId -> createYourTask(taskId);
```

### 3. Run the Test

```java
PerformanceTestRunner runner = new PerformanceTestRunner(
    taskFactory,
    maxConcurrency,  // e.g., 100
    targetTps,       // e.g., 50
    rampUpDuration   // e.g., Duration.ofSeconds(5)
);

TestResult result = runner.run(Duration.ofSeconds(60));
runner.close();

System.out.println(result.getMetrics());
```

## 📊 Example Use Cases

### 1. Sleep Task (I/O Simulation)

```java
Task sleepTask = () -> {
    long start = System.nanoTime();
    Thread.sleep(10);  // Simulate I/O latency
    return SimpleTaskResult.success(taskId, System.nanoTime() - start);
};
```

### 2. CPU-Bound Task

```java
Task cpuTask = () -> {
    long start = System.nanoTime();
    int result = performComplexCalculation();
    return SimpleTaskResult.success(taskId, System.nanoTime() - start, result);
};
```

### 3. Database Query Task

```java
Task dbTask = () -> {
    long start = System.nanoTime();
    try {
        ResultSet rs = connection.executeQuery("SELECT * FROM users WHERE id = ?");
        int rowCount = countRows(rs);
        return SimpleTaskResult.success(taskId, System.nanoTime() - start, rowCount);
    } catch (SQLException e) {
        return SimpleTaskResult.failure(taskId, System.nanoTime() - start, e.getMessage());
    }
};
```

### 4. gRPC Call Task

```java
Task grpcTask = () -> {
    long start = System.nanoTime();
    try {
        YourResponse response = stub.yourMethod(request);
        return SimpleTaskResult.success(taskId, System.nanoTime() - start);
    } catch (StatusRuntimeException e) {
        return SimpleTaskResult.failure(taskId, System.nanoTime() - start, e.getStatus().toString());
    }
};
```

## 🎨 Design Principles

### Single Responsibility Principle

Each class has one clear purpose:

- `Task`: Defines what to execute
- `TaskExecutor`: Controls how tasks are executed
- `MetricsCollector`: Collects and aggregates metrics
- `RateController`: Controls execution rate
- `PerformanceTestRunner`: Orchestrates the test lifecycle

### Clean & Simple

- **No lengthy classes**: Each class is focused and easy to understand
- **No complex inheritance**: Composition over inheritance
- **Clear interfaces**: Simple contracts that are easy to implement

### Extensibility

Easy to extend for:
- Custom task types (HTTP, gRPC, DB, etc.)
- Custom metrics collectors
- Custom rate controllers
- Custom reporters

## 📈 Metrics

The framework collects:

- **Total tasks** executed
- **Success/failure rates**
- **Throughput** (TPS)
- **Latency**: Average, P50, P75, P90, P95, P99
- **Error counts** by message

## 🔧 Configuration

Configure your test with:

```java
int maxConcurrency = 100;      // Maximum concurrent tasks
int targetTps = 50;             // Target transactions per second
Duration rampUp = Duration.ofSeconds(5);  // Gradual ramp-up
Duration testDuration = Duration.ofSeconds(60);  // Test length
```

## 🧪 Testing Different Workloads

### HTTP Load Testing

```java
TaskFactory httpFactory = taskId -> () -> {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://api.example.com/endpoint"))
        .build();
    
    long start = System.nanoTime();
    HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
    long latency = System.nanoTime() - start;
    
    return SimpleTaskResult.success(taskId, latency, response.body().length());
};
```

### Message Queue Testing

```java
TaskFactory mqFactory = taskId -> () -> {
    long start = System.nanoTime();
    producer.send(new ProducerRecord<>("topic", "key", "message"));
    long latency = System.nanoTime() - start;
    
    return SimpleTaskResult.success(taskId, latency);
};
```

### Mixed Workload Testing

```java
TaskFactory mixedFactory = taskId -> {
    // Randomly choose task type
    if (random.nextDouble() < 0.7) {
        return createHttpTask(taskId);
    } else {
        return createDatabaseTask(taskId);
    }
};
```

## 🏁 Running the Framework

```bash
# Build the project
./gradlew build

# Run with default example
./gradlew run

# Run with custom configuration
java -jar build/libs/grpc-load-test-client-1.0.0.jar
```

## 🎓 Migration from Old gRPC-Specific Code

The framework was refactored from a gRPC-specific tool to a generic performance testing framework:

### Before (gRPC-Only)
```java
GrpcLoadTestClient client = new GrpcLoadTestClient(config);
client.executeEcho("message");
```

### After (Generic)
```java
Task yourTask = () -> {
    // Any workload: gRPC, HTTP, DB, etc.
    return SimpleTaskResult.success(taskId, latency);
};

TaskFactory factory = id -> yourTask;
PerformanceTestRunner runner = new PerformanceTestRunner(factory, ...);
```

## 📦 Dependencies

Minimal dependencies:
- Java 21 (for Virtual Threads)
- SLF4J (logging)
- Apache Commons Math (percentile calculations)

Optional (only if you use them):
- gRPC libraries (for gRPC tasks)
- HTTP client libraries (for HTTP tasks)
- Database drivers (for DB tasks)

## 🤝 Contributing

The framework is designed to be extended. To add a new task type:

1. Implement the `Task` interface
2. Return a `TaskResult` (success or failure)
3. Create a `TaskFactory` that produces your tasks
4. Run with `PerformanceTestRunner`

## 📝 License

[Your License Here]

## 🙋 Support

For questions and support, please [open an issue](https://github.com/your-repo/issues).
