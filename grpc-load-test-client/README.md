# gRPC Load Test Client

A high-performance gRPC load testing client built with Java 21 virtual threads, designed to generate configurable throughput (TPS) and provide comprehensive latency statistics including P10, P25, P50, P75, P90, P95, and P99 percentiles.

## Features

- ⚡ **Java 21 Virtual Threads**: Leverages virtual threads for efficient handling of thousands of concurrent requests
- 📊 **Comprehensive Metrics**: Tracks latency percentiles (P10-P99), response codes, and error statistics  
- 🎯 **Precise TPS Control**: Accurate throughput control with ramp-up support
- 📈 **Real-time Reporting**: Live statistics with configurable reporting intervals
- 🔧 **Flexible Configuration**: Command-line options and YAML configuration support
- 📤 **Multiple Output Formats**: Console, JSON, and CSV output formats
- 🔐 **TLS Support**: Optional TLS/SSL connections
- 🧪 **Multiple Test Methods**: Echo, ComputeHash, and HealthCheck test methods

## Requirements

- Java 21 or higher
- Gradle 8.0 or higher

## Building

```bash
./gradlew build
```

## Usage

### Basic Usage

```bash
# Simple load test with default settings (100 TPS for 60 seconds)
./gradlew run --args="--host localhost --port 8080"

# Custom TPS and duration
./gradlew run --args="--host localhost --port 8080 --tps 500 --duration 120"

# High concurrency test
./gradlew run --args="--host example.com --port 443 --tls --tps 1000 --concurrency 2000 --duration 300"
```

### Command Line Options

| Option | Description | Default |
|--------|-------------|---------|
| `-h, --host` | Target gRPC server host | localhost |
| `-p, --port` | Target gRPC server port | 8080 |
| `-t, --tps` | Target transactions per second | 100 |
| `-d, --duration` | Test duration in seconds | 60 |
| `-w, --warmup` | Warmup duration in seconds | 10 |
| `-r, --ramp-up` | Ramp-up duration in seconds | 0 |
| `-c, --concurrency` | Maximum concurrent requests | 1000 |
| `-m, --method` | gRPC method to test (Echo, ComputeHash, HealthCheck) | Echo |
| `--tls` | Use TLS connection | false |
| `--timeout` | Request timeout in milliseconds | 5000 |
| `--report-interval` | Reporting interval in seconds | 10 |
| `--output-format` | Output format (console, json, csv) | console |
| `--output-file` | Output file path | - |
| `--config` | Configuration file path (YAML) | - |
| `--message` | Message payload for Echo requests | "Hello, World!" |
| `--verbose, -v` | Enable verbose logging | false |

### Configuration File

You can use a YAML configuration file for more complex setups:

```yaml
# config.yaml
target:
  host: "localhost"
  port: 8080
  method: "Echo"
  use_tls: false
  request_template:
    message: "Custom test message"

load:
  tps: 500
  duration: "PT2M"  # 2 minutes
  warmup_duration: "PT10S"  # 10 seconds
  ramp_up_duration: "PT30S"  # 30 seconds ramp-up
  max_concurrent_requests: 1000

client:
  request_timeout_ms: 5000
  keep_alive_time_ms: 30000
  keep_alive_timeout_ms: 5000
  max_inbound_message_size: 4194304  # 4MB

reporting:
  reporting_interval_seconds: 5
  enable_real_time_stats: true
  output_format: "console"
  percentiles: [0.1, 0.25, 0.5, 0.75, 0.9, 0.95, 0.99]
```

Use with: `./gradlew run --args="--config config.yaml"`

### Examples

#### Basic Load Test
```bash
./gradlew run --args="--host myservice.example.com --port 443 --tls --tps 200 --duration 300"
```

#### High-Throughput Test with Ramp-up
```bash
./gradlew run --args="--host localhost --port 8080 --tps 2000 --duration 600 --ramp-up 60 --concurrency 5000"
```

#### JSON Output to File
```bash
./gradlew run --args="--host localhost --port 8080 --tps 100 --duration 120 --output-format json --output-file results.json"
```

#### CSV Output for Analysis
```bash
./gradlew run --args="--host localhost --port 8080 --tps 100 --duration 300 --output-format csv --output-file results.csv --report-interval 5"
```

#### CPU-Intensive Test
```bash
./gradlew run --args="--host localhost --port 8080 --method ComputeHash --tps 50 --duration 120"
```

## Sample Output

### Console Output
```
================================================================================
 REAL-TIME STATS - 14:30:15
================================================================================
Duration: 1m 30s | Total Requests: 9,000 | TPS: 100.0
Success Rate: 99.89% (8,990/9,000) | Avg Response Size: 1,234 bytes

Latency Statistics:
  Average: 45.23 ms
  P10: 12.34 ms | P25: 23.45 ms | P50: 34.56 ms | P75: 56.78 ms
  P90: 78.90 ms | P95: 89.01 ms | P99: 123.45 ms

Response Codes:
  SUCCESS: 8,990 (99.9%)
  ERROR_14: 10 (0.1%)

Throughput: Target 100 TPS | Actual 100.0 TPS
Virtual Threads: Active 45/1000 (4.5% utilization) | Submitted: 9,000 | Completed: 8,955
================================================================================
```

### JSON Output
```json
{
  "timestamp" : "2025-09-29T14:30:15.123Z",
  "isFinalReport" : true,
  "metrics" : {
    "totalRequests" : 9000,
    "successfulRequests" : 8990,
    "failedRequests" : 10,
    "tps" : 100.0,
    "avgLatencyMs" : 45.23,
    "successRate" : 99.89,
    "percentiles" : {
      "P10" : 12.34,
      "P25" : 23.45,
      "P50" : 34.56,
      "P75" : 56.78,
      "P90" : 78.90,
      "P95" : 89.01,
      "P99" : 123.45
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