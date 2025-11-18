# VajraEdge Metrics Architecture: Detailed Analysis

**Date**: November 9, 2025  
**Purpose**: Analyze current metrics collection and identify common code opportunities  
**Status**: Analysis Complete

---

## Executive Summary

This document provides a detailed analysis of how metrics are collected, transmitted, and aggregated in VajraEdge's distributed testing architecture. The analysis reveals significant code duplication and opportunities for consolidation between the single-node (core) and distributed (worker) implementations.

### Key Findings

1. **Two Separate Metrics Systems**: Core has `MetricsCollector.java` (single-node), workers have custom implementation
2. **Heartbeat vs Metrics**: Currently separate mechanisms with overlapping data
3. **TPS Calculation**: Duplicated logic with different implementations (both incomplete)
4. **Latency Tracking**: Core has full implementation, worker has hardcoded zeros
5. **Code Duplication**: ~60% of metrics logic could be shared

---

## 1. Current Metrics Flow Architecture

### 1.1 Worker → Controller Data Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    WORKER NODE                               │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────────────────────────────────┐           │
│  │  TaskExecutorService                          │           │
│  │  • Tracks active tasks                        │           │
│  │  • Records completions/failures               │           │
│  │  • No latency tracking (!)                    │           │
│  │  • No TPS calculation (!)                     │           │
│  └──────────────┬───────────────────────────────┘           │
│                 │ getStats()                                 │
│                 ↓                                            │
│  ┌──────────────────────────────────────────────┐           │
│  │  HeartbeatSender (every 5s)                   │           │
│  │  • workerId                                   │           │
│  │  • currentLoad (active tasks)                 │           │
│  │  • status (RUNNING/IDLE/etc)                  │           │
│  │  • timestamp                                  │           │
│  └──────────────┬───────────────────────────────┘           │
│                 │ gRPC (separate from metrics)              │
│                 ↓                                            │
│  ┌──────────────────────────────────────────────┐           │
│  │  MetricsReporter (every 5s)                   │           │
│  │  • workerId + testId                          │           │
│  │  • completedTasks                             │           │
│  │  • successfulTasks / failedTasks              │           │
│  │  • currentTps = 0.0 (TODO!)                   │           │
│  │  • activeTasks                                │           │
│  │  • p50/p95/p99 = 0.0 (TODO!)                  │           │
│  └──────────────┬───────────────────────────────┘           │
│                 │ gRPC streaming                            │
│                 ↓                                            │
└─────────────────┼───────────────────────────────────────────┘
                  │
                  │ WorkerService.StreamMetrics()
                  │
                  ↓
┌─────────────────────────────────────────────────────────────┐
│                 CONTROLLER NODE                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────────────────────────────────┐           │
│  │  WorkerServiceImpl (gRPC)                     │           │
│  │  • Receives heartbeat → updates WorkerInfo   │           │
│  │  • Receives metrics → DistributedMetrics...  │           │
│  └──────────────┬───────────────────────────────┘           │
│                 ↓                                            │
│  ┌──────────────────────────────────────────────┐           │
│  │  DistributedMetricsCollector                  │           │
│  │  • Stores: Map<testId, Map<workerId, ...>>   │           │
│  │  • Aggregates across workers:                │           │
│  │    - Sum totalRequests                        │           │
│  │    - Sum TPS (from workers)                   │           │
│  │    - Weighted avg latencies                   │           │
│  │  • Returns AggregatedMetrics                  │           │
│  └──────────────┬───────────────────────────────┘           │
│                 ↓                                            │
│  ┌──────────────────────────────────────────────┐           │
│  │  TestController (REST API)                    │           │
│  │  • GET /api/tests/distributed                 │           │
│  │  • Enriches with metrics from collector       │           │
│  │  • Returns to UI                              │           │
│  └──────────────────────────────────────────────┘           │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 Single-Node (Core) Metrics Flow

```
┌─────────────────────────────────────────────────────────────┐
│                  CORE (Single-Node Test)                     │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────────────────────────────────┐           │
│  │  VirtualThreadTaskExecutor                    │           │
│  │  • Executes tasks                             │           │
│  │  • Gets TaskResult from each execution        │           │
│  └──────────────┬───────────────────────────────┘           │
│                 │ TaskResult (includes latency)             │
│                 ↓                                            │
│  ┌──────────────────────────────────────────────┐           │
│  │  MetricsCollector                             │           │
│  │  • recordResult(TaskResult)                   │           │
│  │  • Tracks latencies in queue                  │           │
│  │  • Calculates TPS from timestamps             │           │
│  │  • Computes percentiles (p50/p95/p99)         │           │
│  │  • Returns MetricsSnapshot                    │           │
│  └──────────────┬───────────────────────────────┘           │
│                 │ getSnapshot()                             │
│                 ↓                                            │
│  ┌──────────────────────────────────────────────┐           │
│  │  MetricsService                               │           │
│  │  • Converts to MetricsResponse DTO            │           │
│  └──────────────┬───────────────────────────────┘           │
│                 ↓                                            │
│  ┌──────────────────────────────────────────────┐           │
│  │  TestController (REST API)                    │           │
│  │  • GET /api/tests/{testId}/metrics            │           │
│  │  • Returns MetricsResponse                    │           │
│  └──────────────────────────────────────────────┘           │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Detailed Component Analysis

### 2.1 Worker Components

#### TaskExecutorService (`vajraedge-worker`)
**Location**: `vajraedge-worker/src/main/java/net/vajraedge/worker/TaskExecutorService.java`

**Current Implementation**:
```java
public record ExecutorStats(
    int completedTasks,    // ✅ Tracked
    int failedTasks,       // ✅ Tracked  
    int activeTasks,       // ✅ Tracked
    double currentTps      // ❌ Hardcoded 0.0 (line 167)
) {}
```

**What's Missing**:
- ❌ No latency recording per task
- ❌ No TPS calculation (timestamps not tracked)
- ❌ No percentile calculation
- ❌ No error categorization

**What Works**:
- ✅ Thread-safe atomic counters
- ✅ Virtual thread execution
- ✅ Task success/failure tracking

---

#### MetricsReporter (`vajraedge-worker`)
**Location**: `vajraedge-worker/src/main/java/net/vajraedge/worker/MetricsReporter.java`

**Current Implementation**:
```java
LocalWorkerMetrics metrics = new LocalWorkerMetrics(
    stats.completedTasks(),
    stats.completedTasks() - stats.failedTasks(),
    stats.failedTasks(),
    stats.activeTasks(),
    stats.currentTps(),     // Always 0.0
    0.0,  // p50 - TODO      ❌ Line 128
    0.0,  // p95 - TODO      ❌ Line 129
    0.0,  // p99 - TODO      ❌ Line 130
    System.currentTimeMillis()
);
```

**Reporting Mechanism**:
- **Frequency**: Every 5 seconds (configurable)
- **Transport**: gRPC streaming (`WorkerService.StreamMetrics()`)
- **Data Structure**: `WorkerMetrics` protobuf
- **Reliability**: Streams can fail and get recreated

**What's Missing**:
- ❌ Actual latency data
- ❌ Actual TPS calculation
- ❌ Error details
- ❌ Test phase information

---

#### HeartbeatSender (`vajraedge-worker`)
**Location**: `vajraedge-worker/src/main/java/net/vajraedge/worker/HeartbeatSender.java`

**Current Implementation**:
```java
HeartbeatRequest request = HeartbeatRequest.newBuilder()
    .setWorkerId(workerId)
    .setCurrentLoad(currentLoad)  // active tasks count
    .setTimestampMs(System.currentTimeMillis())
    .setStatus(WorkerStatus.WORKER_STATUS_RUNNING)
    .build();
```

**Purpose**:
- Keep worker registration alive
- Report current load for capacity planning
- Health check mechanism

**Overlap with Metrics**:
- `currentLoad` ≈ `activeTasks` (same data!)
- Both sent every 5 seconds
- Could be consolidated

---

### 2.2 Controller Components

#### DistributedMetricsCollector (`vajraedge-core`)
**Location**: `vajraedge-core/src/main/java/net/vajraedge/perftest/distributed/DistributedMetricsCollector.java`

**Current Implementation**:
```java
// Storage structure
Map<String, Map<String, WorkerMetrics>> testMetrics;
//    testId  →  workerId  →  latest metrics

// Aggregation logic
public AggregatedMetrics getAggregatedMetrics(String testId) {
    // Sum counts
    long totalRequests = allMetrics.stream()
        .mapToLong(WorkerMetrics::getTotalRequests)
        .sum();
    
    // Sum TPS (problematic when workers report 0!)
    double totalTps = allMetrics.stream()
        .mapToDouble(WorkerMetrics::getCurrentTps)
        .sum();  // 0 + 0 + 0 = 0
    
    // Weighted average latencies
    AggregatedLatency aggregatedLatency = 
        aggregateLatencies(latencyDataPoints);
}
```

**Aggregation Strategy**:
- **Counts**: Simple sum (works correctly)
- **TPS**: Sum across workers (works IF workers report correctly)
- **Latencies**: Weighted by request count (good approach, but workers send 0s)

**Problems**:
1. Garbage-in-garbage-out: If workers send zeros, aggregation returns zeros
2. No validation of worker data quality
3. No fallback calculation mechanism

---

#### MetricsCollector (`vajraedge-core`)
**Location**: `vajraedge-core/src/main/java/net/vajraedge/perftest/metrics/MetricsCollector.java`

**Current Implementation** (WORKING!):
```java
public class MetricsCollector implements AutoCloseable {
    // Atomic counters
    private final AtomicLong totalTasks = new AtomicLong(0);
    private final AtomicLong successfulTasks = new AtomicLong(0);
    private final AtomicLong failedTasks = new AtomicLong(0);
    
    // Latency tracking
    private final ConcurrentLinkedQueue<Double> latencyHistory;
    private final int maxLatencyHistory = 10000;
    
    // TPS calculation
    private final ConcurrentLinkedQueue<Long> taskTimestamps;
    private static final long TPS_WINDOW_MS = 5000;
    
    public void recordResult(TaskResult result) {
        totalTasks.incrementAndGet();
        
        // Record timestamp for TPS
        taskTimestamps.offer(System.currentTimeMillis());
        
        // Record latency
        latencyHistory.offer(result.getLatencyMs());
        
        if (result.isSuccess()) {
            successfulTasks.incrementAndGet();
        } else {
            failedTasks.incrementAndGet();
            recordError(result.getErrorMessage());
        }
    }
    
    private double calculateCurrentTps() {
        long now = System.currentTimeMillis();
        long windowStart = now - TPS_WINDOW_MS;
        
        // Count tasks in window
        long count = taskTimestamps.stream()
            .filter(ts -> ts > windowStart)
            .count();
        
        return (count * 1000.0) / TPS_WINDOW_MS;
    }
    
    private PercentileStats calculatePercentiles() {
        List<Double> latencies = new ArrayList<>(latencyHistory);
        if (latencies.isEmpty()) {
            return new PercentileStats(0, 0, 0);
        }
        
        double[] values = latencies.stream()
            .mapToDouble(Double::doubleValue)
            .toArray();
        
        Percentile percentile = new Percentile();
        percentile.setData(values);
        
        return new PercentileStats(
            percentile.evaluate(50.0),
            percentile.evaluate(95.0),
            percentile.evaluate(99.0)
        );
    }
}
```

**What Works**:
- ✅ Complete latency tracking
- ✅ Working TPS calculation (sliding window)
- ✅ Percentile computation (Apache Commons Math)
- ✅ Error tracking and categorization
- ✅ Thread-safe operations
- ✅ Memory-bounded queues

**This is the GOLD STANDARD** - we should replicate this in workers!

---

## 3. Data Transmission Mechanisms

### 3.1 Heartbeat Mechanism

**Protocol**: Unary gRPC RPC  
**Frequency**: Every 5 seconds  
**Direction**: Worker → Controller

```protobuf
message HeartbeatRequest {
    string worker_id = 1;
    int32 current_load = 2;       // ← Overlaps with metrics!
    WorkerStatus status = 3;
    int64 timestamp_ms = 4;
}

message HeartbeatResponse {
    bool healthy = 1;
    string message = 2;
}
```

**Purpose**:
- Worker keepalive
- Load reporting (for task distribution)
- Health check

**Problems**:
1. Overlaps with metrics (current_load ≈ active_tasks)
2. Separate RPC from metrics (network overhead)
3. No test-specific information

---

### 3.2 Metrics Streaming Mechanism

**Protocol**: Bidirectional gRPC streaming  
**Frequency**: Every 5 seconds (worker-side interval)  
**Direction**: Worker → Controller (stream)

```protobuf
rpc StreamMetrics(stream WorkerMetrics) 
    returns (MetricsAcknowledgment);

message WorkerMetrics {
    string worker_id = 1;
    string test_id = 2;           // ← Test-specific!
    int64 timestamp_ms = 3;
    
    // Counts
    int64 total_requests = 4;
    int64 successful_requests = 5;
    int64 failed_requests = 6;
    
    // Performance
    double current_tps = 7;       // ← Currently always 0
    int32 active_tasks = 8;       // ← Overlaps with heartbeat!
    
    // Latencies
    LatencyStatistics latency = 9; // ← Currently all zeros
    
    // Errors
    repeated ErrorDetail errors = 10;
}
```

**Problems**:
1. Stream can break and needs recreation
2. No acknowledgment mechanism effectively used
3. Worker doesn't implement TPS/latency calculation

---

## 4. Code Duplication Analysis

### 4.1 What's Duplicated

| Functionality | Core Implementation | Worker Implementation | Duplication % |
|--------------|---------------------|----------------------|---------------|
| Task counting | `AtomicLong` counters | `AtomicLong` counters | 100% |
| Success/fail tracking | `recordResult()` | Manual increment | 80% |
| Latency storage | `ConcurrentLinkedQueue` | ❌ None | 0% |
| TPS calculation | Sliding window | ❌ Hardcoded 0 | 0% |
| Percentile calculation | Apache Commons Math | ❌ Hardcoded 0 | 0% |
| Error tracking | `ConcurrentHashMap` | ❌ None | 0% |
| Thread safety | Built-in | Built-in | 100% |

**Overall Duplication**: ~40% of code is duplicated, 60% missing in worker

---

### 4.2 What Can Be Shared

**HIGH PRIORITY - Common Metrics Library**:
```
vajraedge-sdk/metrics/
├── MetricsCollector.java        ← Move from core (working!)
├── MetricsSnapshot.java         ← Common data structure
├── PercentileStats.java         ← Common calculation
└── TpsCalculator.java           ← Extract windowed TPS logic
```

**MEDIUM PRIORITY - Worker Enhancements**:
- Worker should use shared `MetricsCollector`
- Worker's `TaskExecutorService.recordTaskComplete(TaskResult)` 
- Delegate to `MetricsCollector.recordResult()`

**LOW PRIORITY - Protocol Consolidation**:
- Merge heartbeat data into metrics stream
- Single gRPC stream for both health + metrics
- Reduce network overhead

---

## 5. Authority Model: Core as Single Source of Truth

### 5.1 Current Model (Distributed Calculation)

```
Worker 1: Calculates own TPS/latencies → sends
Worker 2: Calculates own TPS/latencies → sends
Worker 3: Calculates own TPS/latencies → sends
                    ↓
        Controller: Aggregates (sum/avg)
```

**Problems**:
- Workers must implement metrics correctly (currently don't!)
- Inconsistent implementations possible
- Hard to debug when numbers don't match
- Controller trusts worker calculations

---

### 5.2 Proposed Model (Centralized Calculation)

```
Worker 1: Sends raw task results → 
Worker 2: Sends raw task results →  Controller:
Worker 3: Sends raw task results →  • Stores all raw results
                                     • Calculates TPS globally
                                     • Calculates percentiles globally
                                     • Single source of truth
```

**Benefits**:
- ✅ Consistent calculation logic
- ✅ Easier to debug/verify
- ✅ Workers stay simple
- ✅ Controller has complete picture

**Tradeoffs**:
- ❌ More network traffic (raw results vs aggregated)
- ❌ Controller must process more data
- ❌ Scalability concerns at high TPS (10k+ workers)

---

### 5.3 Hybrid Model (Recommended)

```
Worker:
  • Collects raw TaskResults
  • Every 1s: Calculate local snapshot (TPS/latencies)
  • Every 5s: Send snapshot to controller
  
Controller:
  • Receives snapshots from all workers
  • Stores raw snapshot data (not just counters)
  • Recalculates global metrics from snapshots
  • Can verify worker calculations
  • Single source of truth for final numbers
```

**Implementation**:
```java
// Worker side
public record TaskResultSnapshot(
    long timestamp,
    List<Double> latencies,     // Last 1000 results
    List<Long> timestamps,      // For TPS calculation
    int successCount,
    int failedCount
) {}

// Controller side
public class DistributedMetricsCollector {
    // Store snapshots, not just aggregated numbers
    Map<String, List<TaskResultSnapshot>> workerSnapshots;
    
    public AggregatedMetrics calculate(String testId) {
        // Recalculate from raw snapshots
        // Controller has full control
    }
}
```

**Benefits**:
- ✅ Controller is authoritative
- ✅ Reasonable network usage
- ✅ Can verify worker calculations
- ✅ Debuggable (snapshots are stored)

---

## 6. Recommendations

### 6.1 Phase 1: Extract Common Metrics Code (IMMEDIATE)

**Goal**: Create `vajraedge-sdk/metrics` package with shared code

**Steps**:
1. Move `MetricsCollector.java` to SDK
2. Move `PercentileStats.java` to SDK  
3. Extract `TpsCalculator` utility from `MetricsCollector`
4. Create `MetricsSnapshot` common DTO
5. Update core to use SDK metrics
6. Update worker to use SDK metrics

**Files to Move**:
```
vajraedge-core/metrics/MetricsCollector.java 
  → vajraedge-sdk/metrics/MetricsCollector.java

vajraedge-core/metrics/PercentileStats.java
  → vajraedge-sdk/metrics/PercentileStats.java

vajraedge-core/metrics/MetricsSnapshot.java
  → vajraedge-sdk/metrics/MetricsSnapshot.java
```

**Expected Impact**:
- ✅ Workers get working TPS calculation
- ✅ Workers get working latency tracking
- ✅ Workers get working percentiles
- ✅ Consistent metrics across system
- ✅ ~500 lines of code deduplicated

---

### 6.2 Phase 2: Enhance Worker to Collect Metrics (NEXT)

**Goal**: Workers use SDK `MetricsCollector` to track results

**Changes in `TaskExecutorService.java`**:
```java
public class TaskExecutorService {
    private final MetricsCollector metricsCollector;
    
    private void executeTaskInternal(Task task, ...) {
        long startTime = System.nanoTime();
        try {
            TaskResult result = task.execute();
            result.setLatencyNanos(System.nanoTime() - startTime);
            metricsCollector.recordResult(result);  // ← Use SDK!
        } catch (Exception e) {
            TaskResult failed = TaskResult.failure(e.getMessage());
            failed.setLatencyNanos(System.nanoTime() - startTime);
            metricsCollector.recordResult(failed);
        }
    }
    
    public MetricsSnapshot getMetricsSnapshot() {
        return metricsCollector.getSnapshot();  // ← Real metrics!
    }
}
```

**Changes in `MetricsReporter.java`**:
```java
private void reportMetrics() {
    MetricsSnapshot snapshot = taskExecutor.getMetricsSnapshot();
    
    LocalWorkerMetrics metrics = new LocalWorkerMetrics(
        snapshot.totalRequests(),
        snapshot.successfulRequests(),
        snapshot.failedRequests(),
        snapshot.activeTasks(),
        snapshot.currentTps(),      // ✅ Real TPS!
        snapshot.p50(),             // ✅ Real p50!
        snapshot.p95(),             // ✅ Real p95!
        snapshot.p99(),             // ✅ Real p99!
        System.currentTimeMillis()
    );
    
    grpcClient.sendMetrics(workerId, testId, metrics);
}
```

**Expected Impact**:
- ✅ Workers report real TPS
- ✅ Workers report real latencies
- ✅ UI shows correct metrics
- ✅ Aggregation works as intended

---

### 6.3 Phase 3: Consolidate Heartbeat and Metrics (OPTIONAL)

**Goal**: Reduce protocol overhead by merging heartbeat into metrics

**Current**:
- Heartbeat every 5s (workerId, currentLoad, status)
- Metrics every 5s (workerId, testId, requests, TPS, latencies)

**Proposed**:
- Single stream every 5s with both health + metrics

**Protocol Change**:
```protobuf
message WorkerMetrics {
    // Keep existing fields
    ...
    
    // Add health fields (from HeartbeatRequest)
    WorkerStatus worker_status = 11;
    
    // No separate heartbeat RPC needed
}

service WorkerService {
    // Remove: rpc Heartbeat(...)
    
    // Keep: rpc StreamMetrics(...)
    // Now includes health info
}
```

**Benefits**:
- 50% reduction in gRPC calls
- Simpler protocol
- Synchronized health and metrics

**Risks**:
- Breaking change to protocol
- Existing workers need updates

---

### 6.4 Phase 4: Controller as Authority (FUTURE)

**Goal**: Controller recalculates metrics from worker snapshots

**Current**:
```java
// Worker sends
WorkerMetrics {
    total_requests: 1000,
    current_tps: 95.4,     // ← Calculated by worker
    p50_ms: 123.4          // ← Calculated by worker
}

// Controller aggregates
totalTps = sum(worker1.tps + worker2.tps + ...)
```

**Proposed**:
```java
// Worker sends
WorkerSnapshot {
    timestamp: now,
    latencies: [123.4, 145.2, ...],  // Last 1000
    timestamps: [ts1, ts2, ...],     // For TPS
    success_count: 900,
    failed_count: 100
}

// Controller recalculates
public AggregatedMetrics calculate(testId) {
    // Combine all worker snapshots
    List<Double> allLatencies = combineLatencies();
    List<Long> allTimestamps = combineTimestamps();
    
    // Controller calculates TPS (authoritative)
    double tps = calculateTps(allTimestamps);
    
    // Controller calculates percentiles (authoritative)
    PercentileStats stats = calculatePercentiles(allLatencies);
    
    return new AggregatedMetrics(tps, stats, ...);
}
```

**Benefits**:
- ✅ Controller is single source of truth
- ✅ Can verify/audit worker data
- ✅ Easier debugging
- ✅ Consistent calculation logic

**Considerations**:
- Network bandwidth (sending raw data)
- Controller CPU usage (more calculation)
- Scale limits (10k+ workers)

---

## 7. Migration Path

### Step-by-Step Implementation

```
Week 1: Phase 1 - Extract Common Code
├── Day 1-2: Move MetricsCollector to SDK
├── Day 3: Update core to use SDK
├── Day 4: Update worker to use SDK
└── Day 5: Testing and validation

Week 2: Phase 2 - Worker Integration
├── Day 1-2: Modify TaskExecutorService
├── Day 3: Update MetricsReporter
├── Day 4: Integration testing
└── Day 5: Performance validation

Week 3: Phase 3 - Protocol Optimization (Optional)
├── Day 1: Design unified protocol
├── Day 2-3: Implementation
├── Day 4: Testing
└── Day 5: Documentation

Future: Phase 4 - Authority Model
└── Evaluate based on scale requirements
```

---

## 8. Conclusion

### Current State Summary

1. **Metrics Mechanisms**:
   - Separate heartbeat (health) and metrics (performance) streams
   - Both run every 5 seconds
   - Significant data overlap (currentLoad ≈ activeTasks)

2. **Code Quality**:
   - Core has excellent `MetricsCollector` (working TPS and latencies)
   - Worker has stub implementation (TPS = 0, latencies = 0)
   - ~60% of metrics code should be shared but isn't

3. **Authority**:
   - Currently distributed (workers calculate, controller aggregates)
   - No validation of worker calculations
   - Leads to garbage-in-garbage-out problem

### Recommended Actions

**IMMEDIATE** (This Sprint):
- Extract `MetricsCollector` to SDK
- Worker uses SDK metrics
- Fixes TPS and latency display

**NEXT** (Following Sprint):
- Consolidate heartbeat into metrics stream
- Single gRPC stream for health + metrics

**FUTURE** (When Scaling):
- Evaluate controller-as-authority model
- Depends on scale (number of workers, TPS target)

### Success Metrics

After implementing recommendations:
- ✅ Worker TPS shows real-time values
- ✅ Worker latencies show p50/p95/p99
- ✅ UI displays correct distributed metrics
- ✅ Code duplication reduced by 60%
- ✅ Single metrics implementation (SDK)
- ✅ Controller aggregation works correctly

---

**Next Steps**: Review this analysis and confirm approach for Phase 1 implementation.
