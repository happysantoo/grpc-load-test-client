# gRPC Load Test Client

A high-performance gRPC load testing tool built with **Java 21 virtual threads** for efficient concurrent request handling and precise throughput control.

## 🚀 Quick Start

### Prerequisites
- **Java 21+** (virtual threads support)
- **Gradle 8.0+**

### Build & Run
```bash
# Build the project
./gradlew build

# Simple load test (100 TPS for 60 seconds)
./gradlew run --args="--host localhost --port 8080"

# Custom load test
./gradlew run --args="--host myservice.com --port 9090 --tps 500 --duration 120"

# Using the convenience script
./run-loadtest.sh --host localhost --port 8080 --tps 200 --duration 300
```

## ✨ Key Features

### Core Features
- 🧵 **Virtual Threads**: Handle 10,000+ concurrent requests efficiently
- 📊 **Detailed Metrics**: P10, P25, P50, P75, P90, P95, P99 latency percentiles
- 🎯 **Precise TPS Control**: Accurate rate limiting with optional ramp-up
- 📈 **Real-time Stats**: Live reporting with configurable intervals
- 📤 **Multiple Formats**: Console, JSON, and CSV output
- 🔐 **TLS Support**: Secure connections
- ⚙️ **Flexible Config**: CLI arguments or YAML configuration

### 🆕 Advanced Features
- 🎲 **Request Randomization**: Random method selection with configurable weights
- 🔄 **Payload Transformation**: Dynamic request modification with transformation rules
- 🎯 **Field Randomization**: Random values for specific fields (strings, numbers, patterns)
- ⏱️ **Timing Variation**: Random delays between requests for realistic load patterns
- 🎨 **Template System**: Use templates with variables for dynamic content generation
- 🎛️ **Configurable Rules**: YAML-based configuration for complex scenarios

## 📋 Command Line Options

| Option | Description | Default |
|--------|-------------|---------|
| `-h, --host` | Target gRPC server host | `localhost` |
| `-p, --port` | Target gRPC server port | `8080` |
| `-t, --tps` | Target transactions per second | `100` |
| `-d, --duration` | Test duration in seconds | `60` |
| `-w, --warmup` | Warmup duration in seconds | `10` |
| `-r, --ramp-up` | Ramp-up duration in seconds | `0` |
| `-c, --concurrency` | Maximum concurrent requests | `1000` |
| `-m, --method` | gRPC method (Echo, ComputeHash, HealthCheck) | `Echo` |
| `--tls` | Use TLS connection | `false` |
| `--timeout` | Request timeout in milliseconds | `5000` |
| `--output-format` | Output format (console, json, csv) | `console` |
| `--enable-randomization` | Enable randomized request execution | `false` |
| `--enable-payload-transformation` | Enable payload transformation | `false` |
| `--output-file` | Output file path | - |
| `--config` | Configuration file path (YAML) | - |
| `--verbose, -v` | Enable verbose logging | `false` |

## ⚙️ Configuration

### YAML Configuration
Create a `config.yaml` file for advanced settings:

```yaml
target:
  host: "localhost"
  port: 8080
  method: "Echo"
  use_tls: false

load:
  tps: 500
  duration: "PT2M"  # 2 minutes
  warmup_duration: "PT10S"
  ramp_up_duration: "PT30S"
  max_concurrent_requests: 1000

reporting:
  reporting_interval_seconds: 5
  output_format: "console"
  percentiles: [0.1, 0.25, 0.5, 0.75, 0.9, 0.95, 0.99]
```

Run with: `./gradlew run --args="--config config.yaml"`

### 🎲 Advanced Randomization Configuration

```yaml
# Enable request randomization and payload transformation
randomization:
  enable_method_randomization: true
  available_methods: ["Echo", "ComputeHash", "HealthCheck"]
  method_weights:
    Echo: 0.6        # 60% probability
    ComputeHash: 0.3  # 30% probability
    HealthCheck: 0.1  # 10% probability
  
  enable_payload_randomization: true
  random_fields:
    user_id:
      type: "PATTERN"
      pattern: "user_{d}{d}{d}{d}"  # Generates: user_1234
    session_token:
      type: "STRING"
      min_value: 16
      max_value: 32
    priority:
      type: "LIST"
      possible_values: ["low", "medium", "high", "critical"]
    score:
      type: "NUMBER"
      min_value: 1.0
      max_value: 100.0

  enable_timing_randomization: true
  min_delay_ms: 10
  max_delay_ms: 500

# Payload transformation rules
payload:
  enable_transformation: true
  base_payload:
    message: "Load test from client"
    metadata:
      test_type: "randomized"
  transformation_rules:
    message:
      type: "TEMPLATE" 
      parameters:
        template: "Test-${timestamp}-${random}-${uuid}"
    client_id:
      type: "PREFIX"
      parameters:
        prefix: "client-"
    iterations:
      type: "RANDOM_NUMBER"
      parameters:
        min: 100
        max: 2000
```

Run with: `./gradlew run --args="--config config-randomized.yaml"`

## 💡 Usage Examples

```bash
# Basic load test
./gradlew run --args="--host myservice.com --port 443 --tls --tps 200 --duration 300"

# High-throughput with ramp-up
./gradlew run --args="--tps 2000 --duration 600 --ramp-up 60 --concurrency 5000"

# Randomized load testing
./gradlew run --args="--host localhost --port 8080 --enable-randomization --config config-randomized.yaml"

# Simple payload transformation
./gradlew run --args="--host localhost --port 8080 --enable-payload-transformation --tps 100"

# Combined randomization and transformation
./gradlew run --args="--enable-randomization --enable-payload-transformation --config advanced-config.yaml"

# JSON output to file
./gradlew run --args="--tps 100 --output-format json --output-file results.json"

# CPU-intensive test
./gradlew run --args="--method ComputeHash --tps 50 --duration 120"

# Using the convenience script
./run-loadtest.sh --host myservice.com --port 443 --tls --tps 500
```

## 📊 Sample Output

### Console Report
```
================================================================================
 FINAL REPORT - 14:30:15
================================================================================
Duration: 1m 30s | Total Requests: 9,000 | TPS: 100.0
Success Rate: 99.89% (8,990/9,000) | Avg Response Size: 1,234 bytes

Latency Statistics:
  Average: 45.23 ms
  P10: 12.34 ms | P25: 23.45 ms | P50: 34.56 ms | P75: 56.78 ms
  P90: 78.90 ms | P95: 89.01 ms | P99: 123.45 ms

Response Codes:
  SUCCESS: 8,990 (99.9%) | ERROR_14: 10 (0.1%)

Throughput: Target 100 TPS | Actual 100.0 TPS
Virtual Threads: Active 0/1000 (0.0% utilization) | Completed: 9,000
================================================================================
```

### JSON Output
```json
{
  "timestamp": "2025-09-30T14:30:15.123Z",
  "isFinalReport": true,
  "metrics": {
    "totalRequests": 9000,
    "successfulRequests": 8990,
    "tps": 100.0,
    "avgLatencyMs": 45.23,
    "successRate": 99.89,
    "percentiles": {
      "P10": 12.34, "P50": 34.56, "P95": 89.01, "P99": 123.45
    }
  }
}
```

## Protocol Buffer Definition

The client includes a sample protobuf definition with multiple test methods:

- **Echo**: Simple request/response for basic load testing
- **ComputeHash**: CPU-intensive operations for testing under computational load
- **HealthCheck**: Service health verification
- **StreamingEcho**: Streaming operations (future enhancement)

## Performance Characteristics

- **Virtual Threads**: Can handle 10,000+ concurrent requests with minimal memory overhead
- **Precise Timing**: Accurate TPS control using nanosecond-precision timing
- **Low Latency**: Minimal overhead measurement using System.nanoTime()
- **Memory Efficient**: Bounded latency history to prevent memory leaks during long tests
- **Thread Safe**: All metrics collection is thread-safe for accurate concurrent measurements

## Troubleshooting

### Common Issues

1. **"Connection refused"**: Ensure the target gRPC server is running and accessible
2. **"DEADLINE_EXCEEDED"**: Increase timeout or check server performance
3. **High latency**: Verify network connectivity and server capacity
4. **Memory issues**: Reduce concurrency or increase JVM heap size

### Debugging

Enable verbose logging for detailed information:
```bash
./gradlew run --args="--host localhost --port 8080 --verbose"
```

### JVM Options

For high-throughput tests, consider these JVM options:
```bash
export JAVA_OPTS="-Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=100"
./gradlew run --args="--tps 5000 --concurrency 10000"
```

## Architecture

The application is structured with the following key components:

- **LoadTestClient**: Main application class with CLI interface
- **GrpcLoadTestClient**: gRPC client wrapper with connection management
- **VirtualThreadExecutor**: Java 21 virtual threads-based executor
- **ThroughputController**: Precise TPS rate limiting with ramp-up support
- **MetricsCollector**: Thread-safe metrics collection and percentile calculation
- **StatisticsReporter**: Real-time and final reporting in multiple formats

## Contributing

1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Ensure all tests pass
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Related Projects

- [gRPC Java](https://github.com/grpc/grpc-java) - Official gRPC Java implementation
- [Apache Commons Math](https://commons.apache.org/proper/commons-math/) - Statistical calculations
- [PicoCLI](https://picocli.info/) - Command line interface framework