# Distributed Testing - Work in Progress

**Branch**: feature/distributed-testing  
**Date**: November 8, 2025  
**Status**: Foundation Complete, UI and Documentation Pending

## Summary

This branch implements the foundation for **distributed testing** (Wishlist Item #9), allowing VajraEdge to scale across multiple worker nodes for massive load generation.

## What's Implemented ✅

### 1. gRPC Protocol Definition
**File**: `vajraedge-core/src/main/proto/vajraedge.proto`

Complete protocol buffer definitions for:
- **WorkerService** - Main gRPC service
  - `RegisterWorker` - Worker registration with capabilities
  - `Heartbeat` - Health check mechanism
  - `AssignTask` - Task distribution from controller to worker
  - `StopTest` - Graceful test termination
  - `StreamMetrics` - Real-time metrics streaming

- **Message Types**:
  - WorkerRegistrationRequest/Response
  - HeartbeatRequest/Response
  - TaskAssignment/Response
  - WorkerMetrics with latency statistics
  - Error details and worker status enums

### 2. Controller-Side Components
**Directory**: `vajraedge-core/src/main/java/net/vajraedge/perftest/distributed/`

#### WorkerInfo.java
- Represents a connected worker with capabilities
- Tracks health status, capacity, current load
- Methods for load percentage, available capacity checks

#### WorkerManager.java
- Central registry for all connected workers
- Worker registration/unregistration
- Health monitoring with heartbeat timeout detection
- Query methods: getHealthyWorkers(), getWorkersForTaskType(), findBestWorkerForTask()
- Pool statistics aggregation

#### WorkerServiceImpl.java
- gRPC service implementation
- Handles worker registration with validation
- Processes heartbeats and updates worker health
- Manages task assignments (stub)
- Receives metrics streams from workers

#### DistributedMetricsCollector.java
- Aggregates metrics from multiple workers
- Weighted averaging for latency percentiles
- Per-worker and global metrics views
- Real-time metrics collection

#### GrpcServerConfig.java
- Spring Bean configuration for gRPC server
- Configurable port (default: 9090)
- Feature flag: `vajraedge.grpc.enabled=false` by default
- Graceful shutdown handling

### 3. Worker-Side Components
**Directory**: `vajraedge-worker/src/main/java/net/vajraedge/worker/`

#### GrpcClient.java - **Fully Implemented**
- ManagedChannel setup to controller
- Worker registration with capabilities
- Periodic heartbeat sending
- Metrics streaming via async stub
- Connection lifecycle management

#### Worker.java - **Updated**
- Main worker entry point
- Connects to controller on startup
- Registers capabilities
- Starts task executor and metrics reporter
- Graceful shutdown handling

#### LocalWorkerMetrics.java
- Local metrics snapshot record
- Includes: completed, successful, failed tasks
- TPS, latency percentiles (p50, p95, p99)

#### MetricsReporter.java - **Updated**
- Periodic metrics collection from TaskExecutorService
- Sends metrics to controller via gRPC
- Configurable reporting interval

### 4. Build Configuration
- Added `com.google.protobuf` plugin to both core and worker
- gRPC dependencies (grpc-netty-shaded, grpc-protobuf, grpc-stub)
- Proto source directory configuration
- Automatic code generation on build

## Architecture Overview

```
┌─────────────────┐
│   Controller    │ (vajraedge-core)
│   (Spring Boot) │
│                 │
│  ┌───────────┐  │
│  │ Worker    │  │ - Manages worker pool
│  │ Manager   │  │ - Tracks health/capacity
│  └───────────┘  │
│                 │
│  ┌───────────┐  │
│  │ gRPC      │  │ - Port 9090
│  │ Server    │  │ - WorkerService
│  └───────────┘  │
│                 │
│  ┌───────────┐  │
│  │ Metrics   │  │ - Aggregates from workers
│  │ Collector │  │ - Weighted percentiles
│  └───────────┘  │
└────────┬────────┘
         │ gRPC
    ┌────┴────┐
    │         │
┌───▼──┐  ┌──▼───┐
│Worker│  │Worker│  (vajraedge-worker)
│  #1  │  │  #2  │
│      │  │      │
│gRPC  │  │gRPC  │ - Registration
│Client│  │Client│ - Heartbeat (5s)
│      │  │      │ - Metrics stream (1s)
│      │  │      │ - Task execution
└──────┘  └──────┘
```

## What's Pending 🚧

### 1. UI Updates (Todo #6)
**Not Started**

Need to add to dashboard (`src/main/resources/static/index.html`):
- Worker status panel (connected workers, capacity, health)
- Per-worker metrics display
- Global aggregated metrics view
- Worker health indicators (green/yellow/red)
- Worker registration/disconnect events

### 2. Task Distribution Logic
**Partially Implemented**

Need to create:
- `TaskDistributor.java` - Distributes tasks to workers based on:
  - Task type compatibility
  - Worker capacity
  - Current load
  - Load balancing strategy
- Integration with TestExecutionService for distributed mode
- REST API endpoints for distributed test initiation

### 3. Task Assignment Implementation
**Stub Only**

Workers currently don't receive/execute actual tasks:
- Implement task assignment reception in Worker.java
- Task deserialization and execution
- Result reporting back to controller

### 4. Integration Tests (Todo #7)
**Not Started**

Need comprehensive tests:
- Multi-worker registration
- Task distribution scenarios
- Worker failure handling (crash, network partition)
- Metrics aggregation correctness
- Load balancing fairness

### 5. Documentation (Todo #8)
**Not Started**

Need to create:
- Distributed testing guide in `documents/`
- Update README.md with distributed mode section
- Worker deployment instructions
- Configuration examples
- Troubleshooting guide

## Configuration

### Enable Distributed Testing

**Controller** (`application.properties`):
```properties
vajraedge.grpc.enabled=true
vajraedge.grpc.port=9090
```

**Worker** (command line):
```bash
java -jar vajraedge-worker.jar \
  --controller-address=localhost:9090 \
  --worker-id=worker-1 \
  --max-concurrency=10000
```

## Testing Locally

1. **Start Controller**:
   ```bash
   cd vajraedge-core
   ./gradlew bootRun
   ```

2. **Enable gRPC** in `application.properties`:
   ```properties
   vajraedge.grpc.enabled=true
   ```

3. **Start Worker**:
   ```bash
   cd vajraedge-worker
   ./gradlew run --args="--controller-address=localhost:9090 --worker-id=test-worker-1"
   ```

4. **Observe Logs**:
   - Controller: Worker registration, heartbeats
   - Worker: Connection, registration success, metrics sending

## Next Steps

1. **Implement Task Distribution**
   - Create TaskDistributor component
   - Wire up to TestController for distributed tests
   - Implement load balancing algorithm

2. **Complete Task Assignment Flow**
   - Worker receives TaskAssignment
   - Deserialize task parameters
   - Execute using TaskExecutorService
   - Report results

3. **Add UI Components**
   - Worker status dashboard
   - Real-time worker metrics
   - Distributed test controls

4. **Write Tests**
   - gRPC service tests
   - Multi-worker integration tests
   - Failure scenario tests

5. **Documentation**
   - User guide for distributed testing
   - Architecture documentation
   - Deployment guide

## Commits in This Branch

1. `feat: add distributed testing foundation with gRPC`
   - gRPC proto definitions
   - Controller-side components
   - gRPC server configuration

2. `feat: implement gRPC client in worker module`
   - Full gRPC client implementation
   - Worker registration and heartbeat
   - Metrics streaming

## Ready for PR?

**Not Yet** ❌

This branch has a solid foundation but is not ready for production:
- ✅ gRPC infrastructure complete
- ✅ Worker-controller communication working
- ✅ Metrics aggregation implemented
- ❌ Task distribution not implemented
- ❌ UI not updated
- ❌ No tests
- ❌ No documentation

**Estimated Remaining Work**: 20-30 hours
- Task distribution: 8 hours
- UI updates: 6 hours
- Testing: 10 hours
- Documentation: 4 hours

## Notes

- gRPC is **disabled by default** to not break existing functionality
- Both controller and worker modules build successfully
- Proto files are shared (worker references core's proto)
- Health monitoring works via scheduled task (every 10s)
- Metrics streaming uses bidirectional gRPC stream

---

**This is foundational work for distributed testing. More commits will follow to complete the feature.**
