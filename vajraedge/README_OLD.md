# Generic Performance Test Framework# gRPC Load Test Client



A flexible, high-performance framework for load testing **any type of workload** using Java 21's virtual threads. Test HTTP APIs, databases, gRPC services, message queues, or any custom task with a simple, clean API.A high-performance gRPC load testing tool built with **Java 21 virtual threads** for efficient concurrent request handling and precise throughput control.



## 🚀 Quick Start## 🚀 Quick Start



### Prerequisites### Prerequisites

- **Java 21+** (virtual threads support)- **Java 21+** (virtual threads support)

- **Gradle 8.0+**- **Gradle 8.0+**



### Build & Run### Build & Run

```bash```bash

# Build the project# Build the project

./gradlew build./gradlew build



# Run the example# Simple load test (100 TPS for 60 seconds)

./gradlew run./gradlew run --args="--host localhost --port 8080"

```

# Custom load test

## ✨ Key Features./gradlew run --args="--host myservice.com --port 9090 --tps 500 --duration 120"



- 🧵 **Virtual Threads**: Handle 10,000+ concurrent tasks efficiently with Java 21# Using the convenience script

- 🎯 **Protocol Agnostic**: Test ANY workload - HTTP, gRPC, database, message queues, custom tasks./run-loadtest.sh --host localhost --port 8080 --tps 200 --duration 300

- 📊 **Detailed Metrics**: P50, P75, P90, P95, P99 latency percentiles, TPS, success rates```

- 🎯 **Precise Rate Control**: Accurate TPS limiting with optional ramp-up

- 🏗️ **SOLID Design**: Single Responsibility Principle, small focused classes (<150 lines)## ✨ Key Features

- 🔌 **Extensible**: Simple Task interface - implement once, test anywhere

- 📈 **Real-time Stats**: Live metrics collection and reporting### Core Features

- 🧩 **Composable**: Mix different task types in a single test- 🧵 **Virtual Threads**: Handle 10,000+ concurrent requests efficiently

- 📊 **Detailed Metrics**: P10, P25, P50, P75, P90, P95, P99 latency percentiles

## 🎯 Core Concepts- 🎯 **Precise TPS Control**: Accurate rate limiting with optional ramp-up

- 📈 **Real-time Stats**: Live reporting with configurable intervals

### Task Interface- 📤 **Multiple Formats**: Console, JSON, and CSV output

The heart of the framework - a simple functional interface:- 🔐 **TLS Support**: Secure connections

- ⚙️ **Flexible Config**: CLI arguments or YAML configuration

```java

@FunctionalInterface### 🆕 Advanced Features

public interface Task {- 🎲 **Request Randomization**: Random method selection with configurable weights

    TaskResult execute() throws Exception;- 🔄 **Payload Transformation**: Dynamic request modification with transformation rules

}- 🎯 **Field Randomization**: Random values for specific fields (strings, numbers, patterns)

```- ⏱️ **Timing Variation**: Random delays between requests for realistic load patterns

- 🎨 **Template System**: Use templates with variables for dynamic content generation

### Example: Sleep Task (I/O Simulation)- 🎛️ **Configurable Rules**: YAML-based configuration for complex scenarios

```java

public class Main {## 📋 Command Line Options

    public static void main(String[] args) throws Exception {

        TaskFactory sleepTaskFactory = taskId -> () -> {| Option | Description | Default |

            long startNanos = System.nanoTime();|--------|-------------|---------|

            try {| `-h, --host` | Target gRPC server host | `localhost` |

                Thread.sleep(10); // Simulate I/O operation| `-p, --port` | Target gRPC server port | `8080` |

                long latencyNanos = System.nanoTime() - startNanos;| `-t, --tps` | Target transactions per second | `100` |

                return SimpleTaskResult.success(taskId, latencyNanos);| `-d, --duration` | Test duration in seconds | `60` |

            } catch (Exception e) {| `-w, --warmup` | Warmup duration in seconds | `10` |

                return SimpleTaskResult.failure(taskId, e.getMessage());| `-r, --ramp-up` | Ramp-up duration in seconds | `0` |

            }| `-c, --concurrency` | Maximum concurrent requests | `1000` |

        };| `-m, --method` | gRPC method (Echo, ComputeHash, HealthCheck) | `Echo` |

| `--tls` | Use TLS connection | `false` |

        TestResult result = PerformanceTestRunner.builder()| `--timeout` | Request timeout in milliseconds | `5000` |

            .taskFactory(sleepTaskFactory)| `--output-format` | Output format (console, json, csv) | `console` |

            .targetTps(50)| `--enable-randomization` | Enable randomized request execution | `false` |

            .maxConcurrency(100)| `--enable-payload-transformation` | Enable payload transformation | `false` |

            .duration(Duration.ofSeconds(10))| `--output-file` | Output file path | - |

            .build()| `--config` | Configuration file path (YAML) | - |

            .run();| `--verbose, -v` | Enable verbose logging | `false` |



        System.out.println("Test completed: " + result);## ⚙️ Configuration

    }

}### YAML Configuration

```Create a `config.yaml` file for advanced settings:



**Output:**```yaml

```target:

18:49:41.144 [main] INFO  - Starting Generic Performance Test Framework  host: "localhost"

18:49:41.143 [main] INFO  - Test Configuration: TPS=50, Concurrency=100, Duration=PT10S  port: 8080

18:49:51.273 [main] INFO  - Test completed: TestResult{duration=PT10.109S,   method: "Echo"

    metrics=Tasks: 402 (100.0% success), TPS: 39.8, Avg Latency: 11.80ms,   use_tls: false

    P95: 12.71ms, P99: 12.80ms}

```load:

  tps: 500

## 💡 Real-World Examples  duration: "PT2M"  # 2 minutes

  warmup_duration: "PT10S"

### HTTP API Testing  ramp_up_duration: "PT30S"

```java  max_concurrent_requests: 1000

TaskFactory httpTaskFactory = taskId -> () -> {

    long startNanos = System.nanoTime();reporting:

    try {  reporting_interval_seconds: 5

        HttpClient client = HttpClient.newHttpClient();  output_format: "console"

        HttpRequest request = HttpRequest.newBuilder()  percentiles: [0.1, 0.25, 0.5, 0.75, 0.9, 0.95, 0.99]

            .uri(URI.create("https://api.example.com/users"))```

            .GET()

            .build();Run with: `./gradlew run --args="--config config.yaml"`

        

        HttpResponse<String> response = client.send(request, ### 🎲 Advanced Randomization Configuration

            HttpResponse.BodyHandlers.ofString());

        ```yaml

        long latencyNanos = System.nanoTime() - startNanos;# Enable request randomization and payload transformation

        randomization:

        if (response.statusCode() == 200) {  enable_method_randomization: true

            return SimpleTaskResult.success(taskId, latencyNanos,   available_methods: ["Echo", "ComputeHash", "HealthCheck"]

                response.body().length());  method_weights:

        } else {    Echo: 0.6        # 60% probability

            return SimpleTaskResult.failure(taskId,     ComputeHash: 0.3  # 30% probability

                "HTTP " + response.statusCode());    HealthCheck: 0.1  # 10% probability

        }  

    } catch (Exception e) {  enable_payload_randomization: true

        return SimpleTaskResult.failure(taskId, e.getMessage());  random_fields:

    }    user_id:

};      type: "PATTERN"

```      pattern: "user_{d}{d}{d}{d}"  # Generates: user_1234

    session_token:

### Database Query Testing      type: "STRING"

```java      min_value: 16

TaskFactory dbTaskFactory = taskId -> () -> {      max_value: 32

    long startNanos = System.nanoTime();    priority:

    try (Connection conn = dataSource.getConnection();      type: "LIST"

         PreparedStatement stmt = conn.prepareStatement(      possible_values: ["low", "medium", "high", "critical"]

             "SELECT * FROM users WHERE id = ?")) {    score:

              type: "NUMBER"

        stmt.setLong(1, taskId % 10000);      min_value: 1.0

        ResultSet rs = stmt.executeQuery();      max_value: 100.0

        

        int rowCount = 0;  enable_timing_randomization: true

        while (rs.next()) {  min_delay_ms: 10

            rowCount++;  max_delay_ms: 500

        }

        # Payload transformation rules

        long latencyNanos = System.nanoTime() - startNanos;payload:

        return SimpleTaskResult.success(taskId, latencyNanos, rowCount);  enable_transformation: true

    } catch (SQLException e) {  base_payload:

        return SimpleTaskResult.failure(taskId, e.getMessage());    message: "Load test from client"

    }    metadata:

};      test_type: "randomized"

```  transformation_rules:

    message:

### gRPC Service Testing      type: "TEMPLATE" 

```java      parameters:

TaskFactory grpcTaskFactory = taskId -> () -> {        template: "Test-${timestamp}-${random}-${uuid}"

    long startNanos = System.nanoTime();    client_id:

    try {      type: "PREFIX"

        EchoRequest request = EchoRequest.newBuilder()      parameters:

            .setMessage("test-" + taskId)        prefix: "client-"

            .build();    iterations:

              type: "RANDOM_NUMBER"

        EchoResponse response = grpcStub.echo(request);      parameters:

                min: 100

        long latencyNanos = System.nanoTime() - startNanos;        max: 2000

        return SimpleTaskResult.success(taskId, latencyNanos, ```

            response.getSerializedSize());

    } catch (StatusRuntimeException e) {Run with: `./gradlew run --args="--config config-randomized.yaml"`

        return SimpleTaskResult.failure(taskId, 

            e.getStatus().toString());## 💡 Usage Examples

    }

};```bash

```# Basic load test

./gradlew run --args="--host myservice.com --port 443 --tls --tps 200 --duration 300"

### Message Queue Testing

```java# High-throughput with ramp-up

TaskFactory mqTaskFactory = taskId -> () -> {./gradlew run --args="--tps 2000 --duration 600 --ramp-up 60 --concurrency 5000"

    long startNanos = System.nanoTime();

    try {# Randomized load testing

        String message = "message-" + taskId;./gradlew run --args="--host localhost --port 8080 --enable-randomization --config config-randomized.yaml"

        producer.send(new ProducerRecord<>("test-topic", message));

        # Simple payload transformation

        long latencyNanos = System.nanoTime() - startNanos;./gradlew run --args="--host localhost --port 8080 --enable-payload-transformation --tps 100"

        return SimpleTaskResult.success(taskId, latencyNanos, 

            message.length());# Combined randomization and transformation

    } catch (Exception e) {./gradlew run --args="--enable-randomization --enable-payload-transformation --config advanced-config.yaml"

        return SimpleTaskResult.failure(taskId, e.getMessage());

    }# JSON output to file

};./gradlew run --args="--tps 100 --output-format json --output-file results.json"

```

# CPU-intensive test

## 🏗️ Architecture./gradlew run --args="--method ComputeHash --tps 50 --duration 120"



### Package Structure# Using the convenience script

```./run-loadtest.sh --host myservice.com --port 443 --tls --tps 500

com.example.perftest/```

├── core/                          # Core abstractions

│   ├── Task.java                  # @FunctionalInterface for workload## 📊 Sample Output

│   ├── TaskResult.java            # Result interface

│   ├── SimpleTaskResult.java     # Immutable implementation### Console Report

│   ├── TaskFactory.java           # Task instance factory```

│   └── TaskExecutor.java          # Execution interface================================================================================

├── executor/                      # Execution implementations FINAL REPORT - 14:30:15

│   └── VirtualThreadTaskExecutor.java  # Virtual thread implementation================================================================================

├── metrics/                       # Metrics collectionDuration: 1m 30s | Total Requests: 9,000 | TPS: 100.0

│   ├── MetricsCollector.java      # Collects and aggregates metricsSuccess Rate: 99.89% (8,990/9,000) | Avg Response Size: 1,234 bytes

│   ├── MetricsSnapshot.java       # Immutable snapshot

│   └── PercentileStats.java       # Percentile calculationsLatency Statistics:

├── rate/                          # Rate control  Average: 45.23 ms

│   └── RateController.java        # TPS limiting with ramp-up  P10: 12.34 ms | P25: 23.45 ms | P50: 34.56 ms | P75: 56.78 ms

└── runner/                        # Test orchestration  P90: 78.90 ms | P95: 89.01 ms | P99: 123.45 ms

    ├── PerformanceTestRunner.java # Main test orchestrator

    └── TestResult.java            # Result wrapperResponse Codes:

```  SUCCESS: 8,990 (99.9%) | ERROR_14: 10 (0.1%)



### Design PrinciplesThroughput: Target 100 TPS | Actual 100.0 TPS

Virtual Threads: Active 0/1000 (0.0% utilization) | Completed: 9,000

1. **Single Responsibility**: Each class has one clear purpose (~50-150 lines)================================================================================

2. **Interface-Based**: All core components are interfaces for extensibility```

3. **Immutability**: Results and snapshots are immutable for thread safety

4. **Composability**: Mix and match components as needed### JSON Output

5. **Testability**: Small, focused classes are easy to test```json

{

## 📊 Metrics  "timestamp": "2025-09-30T14:30:15.123Z",

  "isFinalReport": true,

The framework automatically collects:  "metrics": {

    "totalRequests": 9000,

- **Throughput**: Transactions per second (TPS)    "successfulRequests": 8990,

- **Latency**: Average, P50, P75, P90, P95, P99 percentiles    "tps": 100.0,

- **Success Rate**: Percentage of successful tasks    "avgLatencyMs": 45.23,

- **Task Counts**: Submitted, completed, successful, failed    "successRate": 99.89,

- **Error Summary**: Top error messages with counts    "percentiles": {

- **Response Sizes**: Min, max, average sizes (if provided)      "P10": 12.34, "P50": 34.56, "P95": 89.01, "P99": 123.45

    }

### Example Metrics Output  }

```}

MetricsSnapshot{```

  duration=10.109s,

  totalTasks=402,## Protocol Buffer Definition

  successfulTasks=402,

  successRate=100.0%,The client includes a sample protobuf definition with multiple test methods:

  tps=39.8,

  avgLatency=11.80ms,- **Echo**: Simple request/response for basic load testing

  percentiles=Percentiles{P50=11.97ms, P75=12.66ms, P90=12.70ms, P95=12.71ms, P99=12.80ms}- **ComputeHash**: CPU-intensive operations for testing under computational load

}- **HealthCheck**: Service health verification

```- **StreamingEcho**: Streaming operations (future enhancement)



## 🎯 Rate Control## Performance Characteristics



### Basic Rate Control- **Virtual Threads**: Can handle 10,000+ concurrent requests with minimal memory overhead

```java- **Precise Timing**: Accurate TPS control using nanosecond-precision timing

TestResult result = PerformanceTestRunner.builder()- **Low Latency**: Minimal overhead measurement using System.nanoTime()

    .taskFactory(myTaskFactory)- **Memory Efficient**: Bounded latency history to prevent memory leaks during long tests

    .targetTps(100)              // 100 transactions per second- **Thread Safe**: All metrics collection is thread-safe for accurate concurrent measurements

    .maxConcurrency(500)         // Max 500 concurrent tasks

    .duration(Duration.ofMinutes(5))## Troubleshooting

    .build()

    .run();### Common Issues

```

1. **"Connection refused"**: Ensure the target gRPC server is running and accessible

### Ramp-Up Pattern2. **"DEADLINE_EXCEEDED"**: Increase timeout or check server performance

```java3. **High latency**: Verify network connectivity and server capacity

TestResult result = PerformanceTestRunner.builder()4. **Memory issues**: Reduce concurrency or increase JVM heap size

    .taskFactory(myTaskFactory)

    .targetTps(1000)             // Target: 1000 TPS### Debugging

    .rampUpDuration(Duration.ofSeconds(30))  // Gradual increase over 30s

    .maxConcurrency(2000)Enable verbose logging for detailed information:

    .duration(Duration.ofMinutes(10))```bash

    .build()./gradlew run --args="--host localhost --port 8080 --verbose"

    .run();```

```

### JVM Options

The ramp-up feature gradually increases from 0 to target TPS over the specified duration, preventing sudden load spikes.

For high-throughput tests, consider these JVM options:

## 🔧 Configuration```bash

export JAVA_OPTS="-Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=100"

### Builder Pattern./gradlew run --args="--tps 5000 --concurrency 10000"

```java```

PerformanceTestRunner runner = PerformanceTestRunner.builder()

    .taskFactory(myTaskFactory)## Architecture

    .targetTps(500)

    .maxConcurrency(1000)The application is structured with the following key components:

    .duration(Duration.ofMinutes(5))

    .rampUpDuration(Duration.ofSeconds(30))- **LoadTestClient**: Main application class with CLI interface

    .reportingInterval(Duration.ofSeconds(10))- **GrpcLoadTestClient**: gRPC client wrapper with connection management

    .build();- **VirtualThreadExecutor**: Java 21 virtual threads-based executor

- **ThroughputController**: Precise TPS rate limiting with ramp-up support

TestResult result = runner.run();- **MetricsCollector**: Thread-safe metrics collection and percentile calculation

```- **StatisticsReporter**: Real-time and final reporting in multiple formats



### Programmatic Access to Metrics## Contributing

```java

TestResult result = runner.run();1. Fork the repository

MetricsSnapshot metrics = result.getMetrics();2. Create a feature branch

3. Add tests for new functionality

System.out.println("TPS: " + metrics.getTps());4. Ensure all tests pass

System.out.println("Success Rate: " + metrics.getSuccessRate() + "%");5. Submit a pull request

System.out.println("P95 Latency: " + metrics.getPercentiles().getP95() + "ms");

System.out.println("Total Tasks: " + metrics.getTotalTasks());## License

```

This project is licensed under the MIT License - see the LICENSE file for details.

## 🧪 Testing

## Related Projects

Run the example tests:

```bash- [gRPC Java](https://github.com/grpc/grpc-java) - Official gRPC Java implementation

./gradlew test- [Apache Commons Math](https://commons.apache.org/proper/commons-math/) - Statistical calculations

```- [PicoCLI](https://picocli.info/) - Command line interface framework

The framework includes:
- Unit tests for all core components
- Edge case coverage
- Thread safety validations
- Performance benchmarks

## 📈 Performance Characteristics

- **Virtual Threads**: Efficiently handle 10,000+ concurrent tasks
- **Memory Efficient**: Bounded latency history (configurable, default 10,000)
- **Precise Timing**: Nanosecond precision using `System.nanoTime()`
- **Thread Safe**: All metrics collection is lock-free or properly synchronized
- **Low Overhead**: Minimal framework overhead (~microseconds per task)

## 🎓 Advanced Usage

### Custom TaskResult
```java
public class CustomTaskResult implements TaskResult {
    private final long taskId;
    private final long latencyNanos;
    private final boolean success;
    private final String errorMessage;
    private final Map<String, Object> customMetrics;
    
    // Implement interface methods + custom logic
}
```

### Custom Executor
```java
public class CustomExecutor implements TaskExecutor {
    // Implement using thread pools, reactive streams, etc.
    
    @Override
    public CompletableFuture<TaskResult> submit(Task task) {
        // Custom submission logic
    }
    
    // Implement other methods...
}
```

### Mixed Workload Testing
```java
TaskFactory mixedFactory = taskId -> {
    if (taskId % 3 == 0) {
        return httpTask();
    } else if (taskId % 3 == 1) {
        return dbTask();
    } else {
        return grpcTask();
    }
};
```

## 🔍 Troubleshooting

### High Latency
- Check network connectivity
- Verify target service capacity
- Reduce concurrency or TPS
- Enable verbose logging

### Memory Issues
- Reduce `maxLatencyHistory` in MetricsCollector
- Lower concurrency
- Increase JVM heap: `export JAVA_OPTS="-Xmx4g"`

### Debugging
```java
// Enable detailed logging
System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
```

## 📚 Documentation

- [Framework README](FRAMEWORK_README.md) - Detailed framework documentation
- [Refactoring Summary](REFACTORING_SUMMARY.md) - Migration guide from gRPC-specific to generic
- [Code Review Fixes](CODE_REVIEW_FIXES_SUMMARY.md) - Code quality improvements

## 🎯 Use Cases

- **API Load Testing**: Test REST/gRPC/GraphQL APIs under load
- **Database Performance**: Measure query performance at scale
- **Message Queue Testing**: Test Kafka, RabbitMQ, SQS throughput
- **Cache Performance**: Benchmark Redis, Memcached operations
- **Custom Workloads**: Any Java code can be wrapped as a Task
- **CI/CD Integration**: Automated performance regression testing
- **Capacity Planning**: Determine system limits and bottlenecks

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Follow Single Responsibility Principle
4. Keep classes small (<150 lines)
5. Add tests for new functionality
6. Submit a pull request

## 📄 License

This project is licensed under the MIT License.

## 🙏 Acknowledgments

- Java 21 Virtual Threads for efficient concurrency
- Apache Commons Math for percentile calculations
- SLF4J for logging abstraction
