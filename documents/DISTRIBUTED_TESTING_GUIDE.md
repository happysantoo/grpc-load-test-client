# Distributed Testing Guide

## Overview

VajraEdge supports distributed load testing, allowing you to coordinate multiple worker nodes to generate significantly higher load than a single machine could produce. The controller orchestrates test execution across workers, aggregates metrics in real-time, and provides a unified view of test results.

## Architecture

```
┌──────────────────────────────────────┐
│         Controller Node              │
│  ┌────────────────────────────────┐  │
│  │   VajraEdge Core               │  │
│  │  - DistributedTestService      │  │
│  │  - TaskDistributor             │  │
│  │  - WorkerManager               │  │
│  │  - MetricsAggregator           │  │
│  └────────────────────────────────┘  │
│              │ gRPC                   │
└──────────────┼────────────────────────┘
               │
       ┌───────┴────────┬──────────┐
       │                │          │
┌──────▼─────┐   ┌─────▼──────┐  ┌▼──────────┐
│  Worker 1  │   │  Worker 2  │  │ Worker N  │
│            │   │            │  │           │
│ - TaskExec │   │ - TaskExec │  │ -TaskExec │
│ - Metrics  │   │ - Metrics  │  │ -Metrics  │
│ - gRPC Svc │   │ - gRPC Svc │  │ -gRPC Svc │
└────────────┘   └────────────┘  └───────────┘
       │                │              │
       └────────────────┴──────────────┘
                    │
            Target System/API
```

### Components

#### Controller
- **DistributedTestService**: Orchestrates test lifecycle
- **TaskDistributor**: Load balancing and task assignment
- **WorkerManager**: Worker registration and health monitoring
- **DistributedMetricsCollector**: Aggregates metrics from all workers

#### Worker
- **Worker**: Main worker application
- **GrpcClient**: Connects to controller, receives assignments
- **TaskAssignmentHandler**: Executes assigned tasks
- **TaskRegistry**: Maps task types to implementations
- **TaskExecutorService**: Executes tasks with concurrency control

## Configuration

### Controller Configuration

Edit `vajraedge-core/src/main/resources/application.properties`:

```properties
# Server port for REST API and dashboard
server.port=8080

# gRPC server port for worker registration
grpc.server.port=9090

# Worker health check interval (seconds)
vajraedge.worker.health-check-interval=10

# Worker timeout (seconds) - unregister if no heartbeat
vajraedge.worker.timeout=30

# Metrics aggregation interval (milliseconds)
vajraedge.metrics.aggregation-interval=500
```

### Worker Configuration

Workers are configured via command-line arguments:

```bash
java -jar vajraedge-worker.jar [OPTIONS]

Options:
  --worker-id=<id>              Unique worker identifier (required)
  --controller-address=<addr>   Controller host:port (default: localhost:9090)
  --max-concurrency=<num>       Maximum concurrent tasks (default: 10000)
  --grpc-port=<port>           Worker gRPC server port (default: 9091)
  --log-level=<level>          Logging level: DEBUG, INFO, WARN, ERROR (default: INFO)
  --help                        Show help message
```

**Example:**
```bash
java -jar vajraedge-worker.jar \
  --worker-id=worker-001 \
  --controller-address=controller.example.com:9090 \
  --max-concurrency=50000 \
  --grpc-port=9091 \
  --log-level=INFO
```

## Deployment

### Option 1: Local Testing (Multiple Processes)

**Terminal 1 - Start Controller:**
```bash
cd vajraedge-core
./gradlew bootRun
```

**Terminal 2 - Start Worker 1:**
```bash
cd vajraedge-worker
./gradlew build
java -jar build/libs/vajraedge-worker-1.0.0.jar \
  --worker-id=worker-001 \
  --controller-address=localhost:9090 \
  --max-concurrency=10000 \
  --grpc-port=9091
```

**Terminal 3 - Start Worker 2:**
```bash
cd vajraedge-worker
java -jar build/libs/vajraedge-worker-1.0.0.jar \
  --worker-id=worker-002 \
  --controller-address=localhost:9090 \
  --max-concurrency=10000 \
  --grpc-port=9092
```

### Option 2: Docker Deployment

**Build Images:**
```bash
# Build controller
cd vajraedge-core
./gradlew bootBuildImage --imageName=vajraedge/controller:latest

# Build worker
cd vajraedge-worker
./gradlew bootBuildImage --imageName=vajraedge/worker:latest
```

**Docker Compose (docker-compose.yml):**
```yaml
version: '3.8'

services:
  controller:
    image: vajraedge/controller:latest
    ports:
      - "8080:8080"  # REST API & Dashboard
      - "9090:9090"  # gRPC server
    environment:
      - GRPC_SERVER_PORT=9090
    networks:
      - vajraedge-net

  worker1:
    image: vajraedge/worker:latest
    environment:
      - VAJRAEDGE_WORKER_ID=worker-001
      - VAJRAEDGE_CONTROLLER_HOST=controller
      - VAJRAEDGE_CONTROLLER_PORT=9090
      - GRPC_SERVER_PORT=9091
      - VAJRAEDGE_WORKER_MAX_CAPACITY=10000
    depends_on:
      - controller
    networks:
      - vajraedge-net

  worker2:
    image: vajraedge/worker:latest
    environment:
      - VAJRAEDGE_WORKER_ID=worker-002
      - VAJRAEDGE_CONTROLLER_HOST=controller
      - VAJRAEDGE_CONTROLLER_PORT=9090
      - GRPC_SERVER_PORT=9092
      - VAJRAEDGE_WORKER_MAX_CAPACITY=10000
    depends_on:
      - controller
    networks:
      - vajraedge-net

networks:
  vajraedge-net:
    driver: bridge
```

**Start Services:**
```bash
docker-compose up -d

# Scale workers
docker-compose up -d --scale worker=5
```

### Option 3: Kubernetes Deployment

**controller-deployment.yaml:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: vajraedge-controller
spec:
  replicas: 1
  selector:
    matchLabels:
      app: vajraedge-controller
  template:
    metadata:
      labels:
        app: vajraedge-controller
    spec:
      containers:
      - name: controller
        image: vajraedge/controller:latest
        ports:
        - containerPort: 8080
          name: http
        - containerPort: 9090
          name: grpc
        env:
        - name: GRPC_SERVER_PORT
          value: "9090"
---
apiVersion: v1
kind: Service
metadata:
  name: vajraedge-controller
spec:
  type: LoadBalancer
  ports:
  - port: 8080
    targetPort: 8080
    name: http
  - port: 9090
    targetPort: 9090
    name: grpc
  selector:
    app: vajraedge-controller
```

**worker-deployment.yaml:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: vajraedge-worker
spec:
  replicas: 3
  selector:
    matchLabels:
      app: vajraedge-worker
  template:
    metadata:
      labels:
        app: vajraedge-worker
    spec:
      containers:
      - name: worker
        image: vajraedge/worker:latest
        env:
        - name: VAJRAEDGE_CONTROLLER_HOST
          value: "vajraedge-controller"
        - name: VAJRAEDGE_CONTROLLER_PORT
          value: "9090"
        - name: VAJRAEDGE_WORKER_MAX_CAPACITY
          value: "10000"
        - name: VAJRAEDGE_WORKER_ID
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
```

**Deploy:**
```bash
kubectl apply -f controller-deployment.yaml
kubectl apply -f worker-deployment.yaml

# Scale workers
kubectl scale deployment vajraedge-worker --replicas=10
```

## Usage

### Via Dashboard UI

1. **Access Dashboard:**
   - Open `http://controller-host:8080`
   - Click the "Distributed" tab

2. **Check Worker Status:**
   - Click "Refresh Workers" button
   - Verify workers are registered and healthy
   - Check worker capacity and current load

3. **Configure Test:**
   - **Task Type**: HTTP, CUSTOM, or SLEEP
   - **Target TPS**: Total TPS across all workers (e.g., 10000)
   - **Duration**: Test duration in seconds (e.g., 60)
   - **Ramp-Up**: Gradual TPS increase period in seconds (e.g., 10)
   - **Max Concurrency**: Maximum concurrent tasks per worker (e.g., 10000)
   - **Min Workers**: Minimum workers required to start test (e.g., 2)

4. **Start Test:**
   - Click "Start Distributed Test"
   - Monitor real-time metrics:
     - Total Requests
     - Current TPS
     - Success/Failure Rate
     - Latency Percentiles (P50, P95, P99)
   - View per-worker metrics in Worker Status panel

5. **Stop Test:**
   - Click "Stop Distributed Test" for graceful shutdown
   - Workers complete in-flight requests before stopping

### Via REST API

**Start Distributed Test:**
```bash
curl -X POST http://localhost:8080/api/tests/distributed \
  -H "Content-Type: application/json" \
  -d '{
    "taskType": "HTTP",
    "targetTps": 10000,
    "durationSeconds": 60,
    "rampUpSeconds": 10,
    "maxConcurrency": 10000,
    "minWorkers": 2
  }'

# Response:
# {
#   "testId": "abc123-...",
#   "workersAssigned": 3,
#   "status": "RUNNING"
# }
```

**Get Test Status:**
```bash
curl http://localhost:8080/api/tests/distributed/abc123-...

# Response:
# {
#   "testId": "abc123-...",
#   "status": "RUNNING",
#   "metrics": {
#     "totalRequests": 150000,
#     "successfulRequests": 148500,
#     "failedRequests": 1500,
#     "currentTps": 2500,
#     "p50": 45,
#     "p95": 120,
#     "p99": 200
#   }
# }
```

**Stop Test:**
```bash
curl -X DELETE http://localhost:8080/api/tests/distributed/abc123-...?graceful=true
```

**List Workers:**
```bash
curl http://localhost:8080/api/workers

# Response:
# [
#   {
#     "workerId": "worker-001",
#     "host": "10.0.1.5",
#     "port": 9091,
#     "status": "HEALTHY",
#     "maxCapacity": 10000,
#     "currentLoad": 3000,
#     "supportedTaskTypes": ["HTTP", "CUSTOM"]
#   }
# ]
```

## Load Balancing Strategy

VajraEdge uses **capacity-based weighted distribution**:

1. **Calculate Available Capacity:**
   ```
   availableCapacity = maxCapacity - currentLoad
   ```

2. **Proportional TPS Distribution:**
   ```
   workerTps = targetTps × (workerCapacity / totalCapacity)
   ```

3. **Example:**
   - Target TPS: 10,000
   - Worker 1: 10,000 capacity, 2,000 load → 8,000 available (53.3%)
   - Worker 2: 10,000 capacity, 5,000 load → 5,000 available (33.3%)
   - Worker 3: 10,000 capacity, 8,000 load → 2,000 available (13.3%)
   
   **Distribution:**
   - Worker 1: 5,333 TPS
   - Worker 2: 3,333 TPS
   - Worker 3: 1,333 TPS

## Metrics Aggregation

### Real-Time Aggregation
- **Interval**: 500ms (configurable)
- **Method**: Pull from each worker's MetricsCollector
- **Aggregated Metrics**:
  - Total Requests (sum)
  - Successful Requests (sum)
  - Failed Requests (sum)
  - Current TPS (sum)
  - Latency Percentiles (weighted average based on request count)

### Percentile Calculation
```java
// Weighted average of percentiles
p95 = Σ(worker_p95 × worker_requests) / total_requests
```

## Creating Custom Task Types

### 1. Implement Task Interface

**MyCustomTask.java:**
```java
package com.example.tasks;

import com.vajraedge.perftest.core.Task;
import com.vajraedge.perftest.core.SimpleTaskResult;
import java.util.Map;

public class MyCustomTask implements Task {
    
    @Override
    public SimpleTaskResult execute() {
        long startTime = System.currentTimeMillis();
        boolean success = false;
        
        try {
            // Your custom logic here
            performCustomOperation();
            success = true;
        } catch (Exception e) {
            // Handle error
        }
        
        long latency = System.currentTimeMillis() - startTime;
        return new SimpleTaskResult(success, latency);
    }
    
    private void performCustomOperation() {
        // Implementation
    }
}
```

### 2. Register in Worker

**Worker.java:**
```java
@PostConstruct
public void init() {
    taskRegistry.registerTask("MYCUSTOM", MyCustomTask.class);
}
```

### 3. Use in Tests

**UI or API:**
```json
{
  "taskType": "MYCUSTOM",
  "targetTps": 1000,
  "durationSeconds": 60
}
```

## Troubleshooting

### Workers Not Registering

**Symptoms:**
- Worker panel shows "No workers registered"
- Worker logs show connection errors

**Solutions:**
1. Verify network connectivity:
   ```bash
   telnet controller-host 9090
   ```

2. Check controller logs:
   ```bash
   grep "Worker registration" vajraedge-core.log
   ```

3. Verify gRPC port:
   ```bash
   netstat -an | grep 9090
   ```

4. Check firewall rules:
   ```bash
   # Allow gRPC port
   sudo ufw allow 9090/tcp
   ```

### Worker Disconnections

**Symptoms:**
- Worker status shows "UNHEALTHY"
- Frequent re-registrations

**Solutions:**
1. Increase heartbeat interval:
   ```properties
   vajraedge.worker.heartbeat-interval=10
   vajraedge.worker.timeout=60
   ```

2. Check network latency:
   ```bash
   ping -c 10 controller-host
   ```

3. Review worker logs for exceptions

### Uneven Load Distribution

**Symptoms:**
- Some workers idle while others overloaded
- Lower than expected total TPS

**Solutions:**
1. Verify worker capacities are properly configured
2. Check worker health status (unhealthy workers receive no load)
3. Review TaskDistributor logs for distribution decisions
4. Ensure all workers support the task type

### Low Total TPS

**Symptoms:**
- Actual TPS significantly lower than target
- Workers not fully utilized

**Solutions:**
1. Increase worker max concurrency:
   ```properties
   vajraedge.worker.max-capacity=20000
   ```

2. Add more workers:
   ```bash
   kubectl scale deployment vajraedge-worker --replicas=10
   ```

3. Check target system capacity (may be bottleneck)

4. Review task execution time (long tasks limit TPS)

### High Latency

**Symptoms:**
- P95/P99 latencies higher than expected
- Timeouts in worker logs

**Solutions:**
1. Reduce concurrency per worker to avoid resource contention
2. Optimize task implementation
3. Check network latency between worker and target
4. Scale horizontally (more workers, lower load each)

## Security Considerations

### Network Security

**TLS for gRPC:**
```properties
# Controller
grpc.server.tls.enabled=true
grpc.server.tls.cert-chain=/path/to/server.crt
grpc.server.tls.private-key=/path/to/server.key

# Worker
grpc.client.tls.enabled=true
grpc.client.tls.ca-cert=/path/to/ca.crt
```

**Authentication:**
```java
// Implement gRPC interceptor for authentication
@Component
public class AuthInterceptor implements ServerInterceptor {
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        
        String token = headers.get(Metadata.Key.of("authorization", 
            Metadata.ASCII_STRING_MARSHALLER));
        
        if (!validateToken(token)) {
            call.close(Status.UNAUTHENTICATED, headers);
            return new ServerCall.Listener<>() {};
        }
        
        return next.startCall(call, headers);
    }
}
```

### Resource Limits

**Prevent DoS:**
```properties
# Limit worker registrations
vajraedge.worker.max-workers=100

# Limit concurrent tests
vajraedge.distributed.max-concurrent-tests=10

# Limit test duration
vajraedge.distributed.max-test-duration=3600
```

### Input Validation

All REST API inputs are validated:
- `targetTps`: 1 - 1,000,000
- `durationSeconds`: 1 - 86,400 (24 hours)
- `maxConcurrency`: 1 - 100,000
- `taskType`: Must match registered types

## Performance Tuning

### Controller JVM Options

```bash
JAVA_OPTS="-Xms2g -Xmx4g \
  -XX:+UseZGC \
  -XX:+UseStringDeduplication \
  -XX:MaxDirectMemorySize=1g"
```

### Worker JVM Options

```bash
JAVA_OPTS="-Xms4g -Xmx8g \
  -XX:+UseZGC \
  -XX:+AlwaysPreTouch \
  -XX:+UseStringDeduplication"
```

### OS Tuning (Linux)

```bash
# Increase open file limit
ulimit -n 65535

# TCP tuning
sysctl -w net.ipv4.tcp_tw_reuse=1
sysctl -w net.ipv4.ip_local_port_range="1024 65535"
sysctl -w net.core.somaxconn=65535

# Network buffer sizes
sysctl -w net.core.rmem_max=16777216
sysctl -w net.core.wmem_max=16777216
```

## Best Practices

### Sizing Workers

**CPU-Bound Tasks:**
- Workers: 1 per physical machine
- Capacity: 2x CPU cores

**I/O-Bound Tasks (HTTP, Database):**
- Workers: 1-2 per physical machine
- Capacity: 10,000 - 50,000 (leverages virtual threads)

### Test Planning

**Ramp-Up:**
- Use 10-30 second ramp-up for large tests
- Prevents overwhelming target system
- Allows auto-scaling to respond

**Duration:**
- Minimum 60 seconds for meaningful metrics
- 300-600 seconds for steady-state analysis
- Longer for soak tests (hours/days)

**Monitoring:**
- Monitor target system metrics alongside VajraEdge metrics
- Watch for resource saturation (CPU, memory, connections)
- Correlate errors with target system logs

### Failure Handling

**Graceful Shutdown:**
- Always use `graceful=true` when stopping tests
- Allows in-flight requests to complete
- Prevents data corruption

**Worker Failures:**
- Controller automatically redistributes load
- Failed worker's tasks reassigned to healthy workers
- Monitor worker health panel during tests

## Roadmap

### Planned Features

- **Auto-scaling**: Automatic worker registration/deregistration based on load
- **Geographic Distribution**: Workers in different regions for global testing
- **Advanced Scheduling**: Cron-based recurring tests
- **Result Comparison**: Compare test runs over time
- **Alerting**: Email/Slack notifications on test completion or failure
- **Database Backend**: Persistent storage of test results

---

**Version**: 1.0  
**Last Updated**: October 26, 2025  
**Maintainer**: VajraEdge Team
