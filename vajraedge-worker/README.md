# VajraEdge Worker

Template for building custom worker nodes for distributed load testing.

## Overview

The VajraEdge Worker is a lightweight, stateless task executor that:
- Connects to the controller via gRPC
- Registers available plugins (task types)
- Receives task assignments from controller
- Executes tasks using Java 21 virtual threads
- Reports metrics back to controller

## Building a Custom Worker

### 1. Clone the Worker Template

```bash
git clone https://github.com/vajraedge/vajraedge-worker-template
cd vajraedge-worker-template
```

### 2. Add Dependencies

```gradle
dependencies {
    // VajraEdge SDK (required)
    implementation 'com.vajraedge:vajraedge-sdk:1.0.0'
    
    // Add your custom plugins
    implementation project(':my-custom-plugin')
    
    // Or use published plugins
    implementation 'com.vajraedge:vajraedge-plugins:1.0.0'
}
```

### 3. Implement Custom Tasks (Optional)

```java
package com.mycompany.tasks;

import com.vajraedge.sdk.*;
import com.vajraedge.sdk.annotations.VajraTask;

@VajraTask(
    name = "MY_API_CALL",
    displayName = "My API Call",
    description = "Calls my internal API"
)
public class MyApiTask implements TaskPlugin {
    
    private String apiUrl;
    
    @Override
    public void initialize(Map<String, Object> parameters) {
        this.apiUrl = (String) parameters.get("url");
    }
    
    @Override
    public TaskResult execute() throws Exception {
        long start = System.nanoTime();
        try {
            // Your API call logic
            MyApiResponse response = myApiClient.call(apiUrl);
            long latency = System.nanoTime() - start;
            
            return SimpleTaskResult.success(latency);
        } catch (Exception e) {
            long latency = System.nanoTime() - start;
            return SimpleTaskResult.failure(latency, e.getMessage());
        }
    }
    
    @Override
    public TaskMetadata getMetadata() {
        return TaskMetadata.builder()
            .name("MY_API_CALL")
            .displayName("My API Call")
            .description("Calls my internal API endpoint")
            .build();
    }
}
```

### 4. Build Worker JAR

```bash
./gradlew clean build
```

Output: `build/libs/my-worker-1.0.0.jar`

### 5. Deploy Worker

```bash
# Copy JAR to worker nodes
scp build/libs/my-worker-1.0.0.jar worker1:/opt/vajraedge/

# Run worker
java -jar my-worker-1.0.0.jar \
  --worker-id=worker1 \
  --controller=controller.example.com:8080 \
  --max-concurrency=10000
```

## Configuration

### Command Line Options

```bash
java -jar vajraedge-worker.jar \
  --worker-id=worker1 \              # Unique worker identifier
  --controller=localhost:8080 \      # Controller address (host:port)
  --max-concurrency=10000 \          # Maximum concurrent tasks
  --region=us-west-2 \               # Worker region/zone (optional)
  --tags=gpu,high-memory \           # Worker tags (optional)
  --metrics-interval=5 \             # Metrics reporting interval (seconds)
  --heartbeat-interval=10            # Heartbeat interval (seconds)
```

### Environment Variables

```bash
export WORKER_ID=worker1
export CONTROLLER_ADDRESS=controller.example.com:8080
export MAX_CONCURRENCY=10000

java -jar vajraedge-worker.jar
```

### Programmatic Configuration

```java
WorkerConfig config = WorkerConfig.builder()
    .workerId("worker1")
    .controllerAddress("controller.example.com:8080")
    .maxConcurrency(10_000)
    .region("us-west-2")
    .tags(List.of("gpu", "high-memory"))
    .build();

Worker worker = new Worker(config);
worker.start();
```

## Architecture

```
┌─────────────────────────────────────────┐
│           Worker Node                    │
├─────────────────────────────────────────┤
│                                          │
│  ┌────────────────────────────────────┐ │
│  │  GrpcClient                        │ │
│  │  • Connect/disconnect               │ │
│  │  • Register/unregister              │ │
│  │  • Send metrics & heartbeats        │ │
│  └────────────────────────────────────┘ │
│                 ↓                        │
│  ┌────────────────────────────────────┐ │
│  │  TaskExecutorService                │ │
│  │  • Virtual thread pool              │ │
│  │  • Concurrency control (10K+)       │ │
│  │  • Task execution & tracking        │ │
│  └────────────────────────────────────┘ │
│                 ↓                        │
│  ┌────────────────────────────────────┐ │
│  │  MetricsReporter                    │ │
│  │  • Periodic reporting (5s)          │ │
│  │  • Completed/failed/active counts   │ │
│  │  • Latency statistics               │ │
│  └────────────────────────────────────┘ │
│                                          │
└─────────────────────────────────────────┘
           ↓           ↑
       gRPC         Metrics
           ↓           ↑
┌─────────────────────────────────────────┐
│         Controller Node                  │
│  • Task orchestration                    │
│  • Load distribution                     │
│  • Metrics aggregation                   │
└─────────────────────────────────────────┘
```

## Features

### Virtual Threads
- Uses Java 21 virtual threads for efficient concurrency
- Supports 10,000+ concurrent tasks per worker
- Minimal resource overhead

### Graceful Shutdown
- Stops accepting new tasks
- Waits for in-flight tasks to complete (with timeout)
- Unregisters from controller
- Proper cleanup of resources

### Metrics Reporting
- Periodic metrics to controller (configurable interval)
- Task counts (completed, failed, active)
- Latency statistics (P50, P95, P99)
- Worker health status

### Plugin Support
- Automatically discovers available TaskPlugin implementations
- Reports capabilities to controller during registration
- Controller assigns appropriate tasks based on worker capabilities

## Example: Production Deployment

### Docker Deployment

```dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY build/libs/my-worker-1.0.0.jar worker.jar

ENTRYPOINT ["java", "-jar", "worker.jar"]
CMD ["--controller=${CONTROLLER_ADDRESS}", \
     "--worker-id=${HOSTNAME}", \
     "--max-concurrency=10000"]
```

```bash
docker build -t my-worker:1.0.0 .

docker run -d \
  --name worker1 \
  -e CONTROLLER_ADDRESS=controller.example.com:8080 \
  my-worker:1.0.0
```

### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: vajraedge-workers
spec:
  replicas: 5
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
        image: my-worker:1.0.0
        env:
        - name: CONTROLLER_ADDRESS
          value: "vajraedge-controller:8080"
        - name: WORKER_ID
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
        resources:
          requests:
            memory: "1Gi"
            cpu: "2"
          limits:
            memory: "2Gi"
            cpu: "4"
```

## Development

### Running Locally

```bash
# Build
./gradlew build

# Run worker
java -jar build/libs/vajraedge-worker-1.0.0.jar \
  --worker-id=local-worker \
  --controller=localhost:8080 \
  --max-concurrency=100
```

### Adding Custom Plugins

1. Create plugin implementing `TaskPlugin`
2. Annotate with `@VajraTask`
3. Add dependency in `build.gradle`
4. Build and deploy

Worker will automatically discover and report the plugin to controller.

## Notes

- gRPC implementation is stubbed in this version
- Full distributed testing features coming in Item 9
- Protocol buffer definitions will be added for controller↔worker communication
- This is a template - customize for your needs!

## License

MIT License - See LICENSE file for details
