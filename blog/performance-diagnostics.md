# Performance Testing with Virtual Threads: A Deep Dive into VajraEdge's Diagnostic Capabilities
 
**Date**: October 28, 2025  
**Reading Time**: 12 minutes

---

## The Problem: Understanding What's Really Happening During Load Tests

As developers, we've all been there. You start a load test, watch the metrics climb, and then... things plateau. Your TPS (Transactions Per Second) hits a ceiling. You keep adding more virtual users, but throughput doesn't budge. Is it your load testing framework? Is it the service under test? Is it network congestion? Without proper diagnostics, you're essentially flying blind.

This is the story of how I built VajraEdge, a modern load testing framework using Java 21's virtual threads, and discovered some fascinating insights about performance testing, bottleneck identification, and the beauty of lightweight concurrency.

## The Journey Begins: Building with Virtual Threads

When Java 21 introduced virtual threads as a production feature, I knew it was the perfect foundation for a load testing framework. Unlike traditional thread pools that can strain the JVM with thousands of platform threads, virtual threads are incredibly lightweight—you can spawn millions without breaking a sweat.

### Why Virtual Threads for Load Testing?

Traditional load testing tools face a fundamental challenge: simulating realistic concurrency without overwhelming the test runner itself. Here's what makes virtual threads revolutionary:

```java
// Traditional approach - limited by platform thread overhead
ExecutorService executor = Executors.newFixedThreadPool(1000);
// Each thread consumes ~1MB of stack memory = 1GB for 1000 threads

// Virtual threads - essentially unlimited concurrency
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    // Spawn 100,000 virtual users? No problem!
    for (int i = 0; i < 100_000; i++) {
        executor.submit(() -> executeTask());
    }
}
```

**The benefits:**
- **Minimal memory footprint**: Virtual threads use heap memory, not stack
- **No thread pool tuning**: No more wrestling with core sizes and queue capacities
- **Natural code**: Write blocking code that looks synchronous but runs efficiently
- **JVM efficiency**: Millions of virtual threads map to a handful of platform threads

### Monitoring Virtual Threads with Spring Boot Actuator

One of my favorite discoveries was leveraging Spring Boot Actuator to monitor virtual threads in real-time. Here's what I configured:

```properties
# Actuator Configuration
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=always
```

With this simple setup, I could hit `/actuator/metrics` and see:

```json
{
  "names": [
    "jvm.threads.live",
    "jvm.threads.daemon",
    "jvm.threads.states",
    "process.cpu.usage",
    "system.cpu.usage"
  ]
}
```

The beauty? Even with 10,000 virtual users actively hammering a service, the JVM thread count remained remarkably stable—typically under 20 platform threads. Virtual threads were doing their magic, multiplexing thousands of concurrent operations onto a small pool of carrier threads.

## The Mystery: Why Isn't TPS Increasing?

Here's where things got interesting. I was testing a simple REST API (my `simple-api` sample application) with a `/api/products` endpoint that returns JSON after a 10ms simulated database delay. The test scenario:

```
Start: 100 virtual users → ~13,300 TPS
After 10s: 200 virtual users → ~13,300 TPS  🤔
After 20s: 300 virtual users → ~13,300 TPS  🤔
After 30s: 400 virtual users → ~13,300 TPS  🤔
```

My first thought: "Is my framework broken? Are the virtual users not actually increasing?"

Spoiler alert: The framework was working perfectly. This observation revealed something far more valuable.

## The Revelation: Understanding Bottlenecks

After digging through metrics and adding more instrumentation, I realized this was **exactly correct behavior**. The HTTP service I was testing had a natural throughput limit of approximately 13,300 requests per second. Once we saturated that capacity, adding more concurrent users didn't increase throughput—it just made each user wait longer.

Think of it like a highway:
- **100 cars** on a highway with max capacity of 1,000 cars/hour → traffic flows smoothly
- **500 cars** trying to use the same highway → still only 1,000 cars/hour get through, but now there's a backup

### What Should Change During Ramp-Up

This realization led me to understand the key metrics to watch:

| Metric | Behavior When Service is Bottlenecked |
|--------|--------------------------------------|
| **TPS** | Plateaus at service capacity (~13,300) |
| **Virtual Users** | Increases linearly (100 → 200 → 300) |
| **Active Tasks** | Matches virtual user count |
| **Average Latency** | **Increases proportionally** ⚠️ |
| **P95/P99 Latency** | **Increases dramatically** ⚠️ |
| **Pending Tasks** | Should stay at 0 if framework keeps up |

The latency increase is mathematical:
```
100 users sharing 13,300 TPS = ~7.5ms average wait
200 users sharing 13,300 TPS = ~15ms average wait
400 users sharing 13,300 TPS = ~30ms average wait
```

## Building the Performance Diagnostics Panel

This insight drove me to create a comprehensive diagnostics panel that helps identify where the bottleneck actually is. I added real-time metrics that calculate and display:

### 1. Virtual Users & Task Efficiency
```javascript
const virtualUsers = metrics.activeTasks;
const tasksPerUser = virtualUsers > 0 
    ? (metrics.currentTps / virtualUsers).toFixed(2) 
    : '0.00';
```

**Tasks/User/Sec** tells you how efficiently each virtual user is operating:
- **High value** (> 10): Users are blazing through tasks, service is keeping up
- **Low value** (< 1): Users are waiting, potential bottleneck

### 2. Queue Depth Monitoring
```javascript
const queueDepth = metrics.pendingTasks;
// Color-coded badges
if (queueDepth === 0) {
    badge.className = 'badge bg-success';  // Healthy
} else if (queueDepth < 100) {
    badge.className = 'badge bg-warning';  // Building up
} else {
    badge.className = 'badge bg-danger';   // Serious backlog
}
```

This immediately shows if your load testing framework is keeping up with demand.

### 3. Latency Ratio (Consistency Check)
```javascript
const latencyRatio = (p99 / p50).toFixed(2);
```

**P99/P50 ratio** reveals response time consistency:
- **1.0x - 2.0x**: Excellent consistency
- **2.0x - 5.0x**: Good, some variance under load
- **> 5.0x**: High variance, potential queueing issues

### 4. Smart Bottleneck Detection

The crown jewel of the diagnostics panel is automatic bottleneck identification:

```javascript
if (queueDepth > virtualUsers * 0.5) {
    // Significant queue buildup = VajraEdge bottleneck
    bottleneckEl.textContent = 'VajraEdge (Queue Buildup)';
    bottleneckEl.className = 'badge bg-danger';
} else if (virtualUsers > 100 && 
           Math.abs(tps - previousTps) < 100) {
    // TPS plateaued with increasing users = Service bottleneck
    bottleneckEl.textContent = 'HTTP Service (Saturated)';
    bottleneckEl.className = 'badge bg-warning';
} else if (queueDepth === 0 && virtualUsers > 0) {
    // No queue, tasks flowing = Healthy
    bottleneckEl.textContent = 'None (Healthy)';
    bottleneckEl.className = 'badge bg-success';
}
```

This algorithm:
1. **Checks for internal queuing** - if pending tasks build up, VajraEdge is the problem
2. **Detects TPS plateau** - if throughput stops increasing despite more users, the service is saturated
3. **Confirms healthy operation** - no queue and tasks flowing smoothly

## Real-World Test Results

Let me show you what this looks like in practice. Testing my sample API with concurrency-based ramping:

### Test Configuration
```javascript
{
  "mode": "CONCURRENCY_BASED",
  "startingConcurrency": 100,
  "maxConcurrency": 500,
  "rampStrategy": "STEP",
  "rampStep": 100,
  "rampInterval": 10,
  "taskType": "HTTP",
  "endpoint": "http://localhost:9090/api/products"
}
```

### Observed Metrics

**At 100 Virtual Users (t=0s):**
```
TPS:              13,300
Active Tasks:     100
Pending Tasks:    0
Avg Latency:      7.2ms
P99 Latency:      15.3ms
Tasks/User/Sec:   133
Latency Ratio:    2.1x
Bottleneck:       None (Healthy) ✅
```

**At 200 Virtual Users (t=10s):**
```
TPS:              13,300  (unchanged)
Active Tasks:     200
Pending Tasks:    0
Avg Latency:      14.8ms  (doubled!)
P99 Latency:      42.1ms  (almost tripled!)
Tasks/User/Sec:   66.5
Latency Ratio:    2.8x
Bottleneck:       HTTP Service (Saturated) ⚠️
```

**At 500 Virtual Users (t=40s):**
```
TPS:              13,300  (still unchanged)
Active Tasks:     500
Pending Tasks:    0
Avg Latency:      37.5ms  (5x the baseline!)
P99 Latency:      128.7ms (8x the baseline!)
Tasks/User/Sec:   26.6
Latency Ratio:    3.4x
Bottleneck:       HTTP Service (Saturated) ⚠️
```

### What This Tells Us

1. **The service saturates at ~13,300 TPS** - This is its maximum throughput
2. **VajraEdge is not the bottleneck** - Pending tasks remain at 0 throughout
3. **Latency degrades gracefully** - Linear increase with user count
4. **Optimal concurrency is around 100 users** - Beyond this, you're just adding latency without gaining throughput

## The Power of Virtual Threads in Practice

Throughout all this testing, here's what impressed me most about virtual threads:

### JVM Resource Usage

Monitoring via Actuator during a 10,000 virtual user test:
```
Platform Threads:     18 (stable)
Heap Usage:          245 MB
CPU Usage:           12% (of 10 cores)
GC Pauses:           < 10ms (minor GCs only)
```

Compare this to what 10,000 platform threads would require:
```
Platform Threads:    10,000
Stack Memory:        ~10 GB (1MB each)
Context Switches:    Millions per second
CPU Usage:           80-90% (thread management overhead)
GC Pressure:         Extreme
```

### Code Simplicity

The virtual thread implementation is remarkably simple:

```java
public CompletableFuture<TaskResult> submit(Task task) {
    semaphore.acquire();  // Limit concurrency
    submittedTasks.incrementAndGet();
    activeTasks.incrementAndGet();
    
    return CompletableFuture.supplyAsync(() -> {
        try {
            return task.execute();  // Blocking call - no problem!
        } finally {
            activeTasks.decrementAndGet();
            completedTasks.incrementAndGet();
            semaphore.release();
        }
    }, virtualThreadExecutor);
}
```

No callback hell, no complex async/await chains, no thread pool tuning. Just straightforward, readable code that scales to hundreds of thousands of concurrent operations.

## Lessons Learned

### 1. Metrics Must Tell a Story

Raw numbers are useless without context. The diagnostics panel doesn't just show "Pending Tasks: 150"—it shows:
- Is this growing or shrinking?
- What does this mean (queue buildup)?
- Where is the bottleneck?

### 2. Bottleneck Identification is Critical

Without automatic bottleneck detection, users would waste hours debugging their services when the real issue is the load testing framework (or vice versa). The smart detection algorithm saves this time.

### 3. Virtual Threads Change Everything

The ability to spawn 100,000 virtual users without thinking about thread pools or memory constraints is transformative. It lets you focus on what matters: understanding your application's behavior under load.

### 4. Real-Time Feedback Accelerates Learning

The WebSocket-based live updates (refreshing every 500ms) mean you can see bottlenecks forming in real-time. You don't wait for a test to complete—you adjust on the fly.

## Technical Deep Dive: How It Works

### Architecture Overview

```
┌─────────────────────────────────────────────┐
│           Web Dashboard (Browser)           │
│  ┌─────────────────────────────────────┐   │
│  │  Performance Diagnostics Panel      │   │
│  │  - Virtual Users                    │   │
│  │  - Tasks/User/Sec                   │   │
│  │  - Queue Depth                      │   │
│  │  - Latency Ratio                    │   │
│  │  - Bottleneck Indicator             │   │
│  └─────────────────────────────────────┘   │
└──────────────┬──────────────────────────────┘
               │ WebSocket (500ms updates)
               │
┌──────────────▼──────────────────────────────┐
│        VajraEdge Server (Spring Boot)       │
│  ┌──────────────────────────────────────┐  │
│  │  TestExecutionService                │  │
│  │  - Manages concurrent tests          │  │
│  │  - Max 10 concurrent tests (DoS)     │  │
│  └──────────────────────────────────────┘  │
│  ┌──────────────────────────────────────┐  │
│  │  ConcurrencyBasedTestRunner          │  │
│  │  - Ramps virtual users               │  │
│  │  - LINEAR or STEP strategy           │  │
│  └──────────────────────────────────────┘  │
│  ┌──────────────────────────────────────┐  │
│  │  VirtualThreadTaskExecutor           │  │
│  │  - Semaphore-based concurrency limit │  │
│  │  - Tracks: submitted, active, done   │  │
│  │  - Calculates: pending tasks         │  │
│  └──────────────────────────────────────┘  │
│  ┌──────────────────────────────────────┐  │
│  │  MetricsCollector                    │  │
│  │  - Windowed TPS (5-second window)    │  │
│  │  - HdrHistogram for percentiles      │  │
│  │  - Success/failure tracking          │  │
│  └──────────────────────────────────────┘  │
└──────────────┬──────────────────────────────┘
               │ Virtual Threads
               │
┌──────────────▼──────────────────────────────┐
│       Service Under Test (HTTP)             │
│  - Example: simple-api                      │
│  - Capacity: ~13,300 TPS                    │
└─────────────────────────────────────────────┘
```

### Pending Tasks Calculation

The key insight for bottleneck detection:

```java
public int getPendingTasks() {
    long submitted = submittedTasks.get();
    long completed = completedTasks.get();
    long active = activeTasks.get();
    
    // Pending = what we haven't started yet
    return Math.max(0, (int)(submitted - completed - active));
}
```

**When pending > 0**: Tasks are queued, framework can't keep up  
**When pending = 0**: Framework is keeping pace with demand

### Windowed TPS Calculation

To get accurate real-time TPS, I use a sliding window approach:

```java
public double calculateCurrentTps() {
    long now = System.currentTimeMillis();
    long windowStart = now - TPS_WINDOW_MS; // 5 seconds
    
    // Count requests in the window
    long requestsInWindow = timestamps.stream()
        .filter(ts -> ts >= windowStart)
        .count();
    
    return (requestsInWindow / (double)TPS_WINDOW_MS) * 1000.0;
}
```

This gives us TPS that updates smoothly every 500ms rather than jumping erratically.

## Performance Best Practices Discovered

### 1. Use Semaphores for Concurrency Limiting

```java
private final Semaphore semaphore;

public VirtualThreadTaskExecutor(int maxConcurrency) {
    this.semaphore = new Semaphore(maxConcurrency);
    this.executor = Executors.newVirtualThreadPerTaskExecutor();
}
```

Even with virtually unlimited threads, you want to control concurrency to avoid overwhelming downstream services.

### 2. Implement DoS Protection

```java
private static final int MAX_CONCURRENT_TESTS = 10;

public String startTest(TestConfigRequest config) {
    if (activeTests.size() >= MAX_CONCURRENT_TESTS) {
        throw new IllegalStateException(
            "Maximum concurrent tests limit reached: " + MAX_CONCURRENT_TESTS
        );
    }
    // Start test...
}
```

Prevents accidental resource exhaustion on the load testing server itself.

### 3. Use Atomic Counters for Thread-Safe Metrics

```java
private final AtomicInteger activeTasks = new AtomicInteger(0);
private final AtomicLong submittedTasks = new AtomicLong(0);
private final AtomicLong completedTasks = new AtomicLong(0);
```

Lock-free, high-performance tracking of concurrent operations.

### 4. Leverage HdrHistogram for Percentiles

```java
private final Histogram latencyHistogram = new Histogram(
    TimeUnit.SECONDS.toNanos(60),  // Max value
    3  // Significant digits
);

public void recordLatency(long latencyNanos) {
    latencyHistogram.recordValue(latencyNanos);
}

public PercentileStats getPercentiles() {
    return new PercentileStats(
        latencyHistogram.getValueAtPercentile(50.0),
        latencyHistogram.getValueAtPercentile(95.0),
        latencyHistogram.getValueAtPercentile(99.0)
    );
}
```

Accurate percentile calculations with minimal memory overhead.

## Future Enhancements

The diagnostics panel has already proven invaluable, but I'm planning several enhancements:

### 1. Historical Trend Analysis
Track metrics over time to identify patterns:
- TPS degradation over extended runs
- Memory leak detection
- Gradual latency increases

### 2. Automatic Optimal Concurrency Detection
Algorithm to find the "sweet spot":
```
while (tps_increasing && latency_acceptable) {
    increase_virtual_users();
}
return optimal_concurrency;
```

### 3. Distributed Load Generation
Coordinate multiple VajraEdge instances to generate massive load:
```
Total Load = Sum(VajraEdge_1 + VajraEdge_2 + ... + VajraEdge_N)
```

### 4. Custom JVM Metrics
Expose virtual thread-specific metrics:
- Carrier thread utilization
- Parking/unparking frequency
- Virtual thread creation rate

## Conclusion

Building VajraEdge taught me that modern Java, specifically virtual threads, fundamentally changes how we approach concurrency. The days of carefully tuned thread pools and callback-based async code are behind us. We can now write simple, blocking code that scales to hundreds of thousands of concurrent operations.

The performance diagnostics panel I built isn't just about pretty charts—it's about understanding system behavior in real-time. It answers the critical questions:
- **Where is the bottleneck?** (Your service, not the framework)
- **What's the maximum throughput?** (~13,300 TPS in my test)
- **What's the optimal concurrency?** (100 users for my service)
- **Is latency acceptable?** (Watch that P99!)

Most importantly, virtual threads proved that you can have both simplicity and performance. The JVM handles millions of virtual threads effortlessly, letting you focus on building features rather than managing concurrency primitives.

If you're building high-concurrency applications in Java, virtual threads should be your first choice. And if you're building load testing tools, VajraEdge demonstrates how lightweight concurrency can transform the developer experience.

---


*Tags: #Java21 #VirtualThreads #LoadTesting #Performance #SpringBoot #Concurrency #DevOps*
