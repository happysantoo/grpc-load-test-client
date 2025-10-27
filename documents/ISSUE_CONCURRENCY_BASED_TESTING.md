# Enhancement: Shift to Concurrency-Based Load Testing (Hybrid Mode)

## Issue Type
Enhancement / Feature Request

## Priority
High - Fundamental improvement to testing approach

## Summary
Transform VajraEdge from a rate-based load generator to a concurrency-based load generator with hybrid mode support. This provides more realistic load testing by simulating concurrent users and measuring actual system throughput rather than forcing a target TPS.

## Current Behavior (Rate-Based)
- User specifies **Target TPS** (e.g., 100 requests/second)
- Framework attempts to maintain this rate regardless of system performance
- TPS is the **independent variable** (controlled)
- System response is measured but artificial constraints may hide issues

**Configuration:**
```
Target TPS: 100
Max Concurrency: 50
Test Duration: 60s
Ramp-Up Duration: 5s
```

## Proposed Behavior (Concurrency-Based with Hybrid Mode)
- User specifies **concurrent virtual users** and ramp strategy
- Framework measures the **actual TPS** the system can sustain
- Concurrency is the **independent variable** (controlled)
- TPS becomes the **dependent variable** (measured)
- Optional: Max TPS limit to protect downstream systems

**Configuration:**
```
Mode: Concurrency-Based (default) or Rate-Limited
Starting Concurrency: 10 users
Ramp Step: +10 users
Ramp Interval: 30s
Max Concurrency: 100 users
Test Duration: 5 minutes
Optional Max TPS: 500 (safety limit)
```

## Why This Matters

### Real-World Simulation
- **Realistic**: Simulates actual user behavior (N users hitting the system)
- **Natural Load**: Discovers true system limits organically
- **Performance Visibility**: Shows how system degrades under increasing load

### Industry Standard
- JMeter, Gatling, LoadRunner all use concurrency-based approach
- Makes VajraEdge comparable to established tools
- Easier adoption for users familiar with other load testing tools

### Better Metrics
- Reveals capacity limits (e.g., "System handles 50 concurrent users at 200 TPS")
- Shows degradation patterns (latency increase as concurrency grows)
- Identifies breaking points more naturally

## Detailed Design

### 1. Load Test Modes

```java
public enum LoadTestMode {
    CONCURRENCY_BASED,  // Default: control concurrency, measure TPS
    RATE_LIMITED        // Hybrid: concurrency-based with max TPS cap
}
```

### 2. Ramp Strategy (Strategy Pattern)

```java
public interface RampStrategy {
    /**
     * Calculate target concurrency at given elapsed time.
     * @param elapsedSeconds seconds since test started
     * @return target number of concurrent virtual users
     */
    int getTargetConcurrency(long elapsedSeconds);
}

public class LinearRampStrategy implements RampStrategy {
    private final int startConcurrency;
    private final int maxConcurrency;
    private final long rampDurationSeconds;
    
    // Linear increase from start to max over ramp duration
}

public class StepRampStrategy implements RampStrategy {
    private final int startConcurrency;
    private final int rampStep;
    private final long rampIntervalSeconds;
    private final int maxConcurrency;
    
    // Add 'rampStep' users every 'rampInterval' seconds
}
```

### 3. Concurrency Controller

```java
@Component
public class ConcurrencyController {
    private final RampStrategy rampStrategy;
    private final int maxConcurrency;
    private final Optional<Integer> maxTpsLimit;
    
    /**
     * Determines how many virtual threads should be active.
     */
    public int getTargetConcurrency(long elapsedSeconds) {
        int target = rampStrategy.getTargetConcurrency(elapsedSeconds);
        return Math.min(target, maxConcurrency);
    }
    
    /**
     * Optional rate limiting for hybrid mode.
     */
    public boolean shouldThrottle(double currentTps) {
        return maxTpsLimit.isPresent() && currentTps >= maxTpsLimit.get();
    }
}
```

### 4. Updated Configuration DTO

```java
public class TestConfigRequest {
    // Load test mode
    private LoadTestMode mode = LoadTestMode.CONCURRENCY_BASED;
    
    // Concurrency-based parameters
    private Integer startingConcurrency = 10;
    private RampStrategyType rampType = RampStrategyType.STEP;
    private Integer rampStep = 10;              // For step ramp
    private Long rampIntervalSeconds = 30L;     // For step ramp
    private Long rampDurationSeconds;           // For linear ramp
    
    // Common parameters
    private Integer maxConcurrency = 100;
    private Long testDurationSeconds = 300L;
    
    // Hybrid mode parameters
    private Integer maxTpsLimit;  // Optional safety limit
    
    // Task configuration
    private String taskType = "HTTP";
    private Object taskParameter;
    
    // Validation
    @AssertTrue
    public boolean isValidConfiguration() {
        if (mode == CONCURRENCY_BASED) {
            return startingConcurrency != null && maxConcurrency != null;
        }
        return true;
    }
}
```

### 5. Execution Engine Changes

Current `PerformanceTestRunner`:
- Uses `RateController` to maintain target TPS
- Submits tasks based on rate schedule

New `ConcurrencyBasedTestRunner`:
- Maintains pool of virtual threads (concurrent users)
- Each virtual user executes tasks in a loop
- Ramps up/down thread pool size based on strategy
- Measures actual TPS achieved

```java
public class ConcurrencyBasedTestRunner {
    private final ConcurrencyController concurrencyController;
    private final TaskFactory taskFactory;
    private final MetricsCollector metricsCollector;
    private final List<CompletableFuture<Void>> activeUsers;
    
    public TestResult run(Duration testDuration) {
        long startTime = System.currentTimeMillis();
        
        while (notFinished(startTime, testDuration)) {
            long elapsed = getElapsedSeconds(startTime);
            int targetConcurrency = concurrencyController.getTargetConcurrency(elapsed);
            
            // Adjust active virtual users
            adjustConcurrency(targetConcurrency);
            
            // Check rate limit if in hybrid mode
            if (concurrencyController.shouldThrottle(getCurrentTps())) {
                backoff();
            }
            
            Thread.sleep(100); // Control loop interval
        }
        
        shutdownUsers();
        return buildResult();
    }
    
    private void adjustConcurrency(int target) {
        int current = activeUsers.size();
        
        if (target > current) {
            // Ramp up: add users
            int toAdd = target - current;
            for (int i = 0; i < toAdd; i++) {
                activeUsers.add(startVirtualUser());
            }
        } else if (target < current) {
            // Ramp down: remove users
            int toRemove = current - target;
            for (int i = 0; i < toRemove; i++) {
                stopVirtualUser(activeUsers.remove(activeUsers.size() - 1));
            }
        }
    }
    
    private CompletableFuture<Void> startVirtualUser() {
        return CompletableFuture.runAsync(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                Task task = taskFactory.createTask(taskId++);
                TaskResult result = task.execute();
                metricsCollector.recordResult(result);
            }
        }, virtualThreadExecutor);
    }
}
```

## UI Changes

### Configuration Panel

**Mode Toggle:**
```
○ Concurrency-Based (Recommended)
○ Rate-Limited (Advanced)
```

**Concurrency-Based Configuration:**
```
Starting Concurrency: [10] users
Max Concurrency: [100] users

Ramp Strategy: [Step ▼]
  ├─ Linear Ramp
  └─ Step Ramp

[If Step Ramp selected:]
Ramp Step: [10] users
Ramp Interval: [30] seconds

[If Linear Ramp selected:]
Ramp Duration: [60] seconds

Test Duration: [300] seconds
```

**Optional (Hybrid Mode):**
```
☑ Enable Rate Limit (Safety)
Max TPS: [500] requests/second
```

### Metrics Display Enhancements

Show both concurrency AND TPS:
```
┌─────────────────────────────────┐
│ Real-Time Metrics               │
├─────────────────────────────────┤
│ Active Virtual Users: 45        │
│ Measured TPS: 187.5            │
│ Total Requests: 11,250          │
│ Avg Latency: 15.3ms            │
└─────────────────────────────────┘
```

### New Chart: Concurrency Over Time
```
Concurrency vs TPS Over Time
─────────────────────────────
 100 │         ╱─────
  80 │       ╱
  60 │     ╱         ← Concurrency
  40 │   ╱
  20 │ ╱             ← TPS
   0 └─────────────────
     0  30s 60s 90s
```

## Implementation Plan

### Phase 1: Core Framework (Backend)
- [ ] Create `LoadTestMode` enum
- [ ] Design `RampStrategy` interface
- [ ] Implement `LinearRampStrategy`
- [ ] Implement `StepRampStrategy`
- [ ] Create `ConcurrencyController`
- [ ] Implement `ConcurrencyBasedTestRunner`
- [ ] Update `TestConfigRequest` DTO
- [ ] Update `TestExecutionService` to support both modes

### Phase 2: Testing
- [ ] Unit tests for `RampStrategy` implementations
- [ ] Unit tests for `ConcurrencyController`
- [ ] Integration tests for `ConcurrencyBasedTestRunner`
- [ ] Spock specs for all new components
- [ ] Test edge cases (ramp-down, duration end during ramp)

### Phase 3: UI Updates
- [ ] Add mode toggle to configuration form
- [ ] Add ramp strategy selector
- [ ] Update metrics display to show concurrency
- [ ] Add "Concurrency Over Time" chart
- [ ] Update help text and tooltips

### Phase 4: Documentation
- [ ] Update README.md with concurrency-based examples
- [ ] Update FRAMEWORK_README.md architecture docs
- [ ] Add migration guide for existing users
- [ ] Create tutorial: "Finding Your System's Capacity"
- [ ] Add JavaDoc for all new classes

### Phase 5: Polish
- [ ] Add configuration validation
- [ ] Add sensible defaults
- [ ] Add example configurations
- [ ] Update blog article
- [ ] Create demo video

## Backward Compatibility

**Option 1: Breaking Change (Recommended)**
- Remove rate-based approach entirely
- Cleaner, simpler codebase
- Version bump to 2.0.0

**Option 2: Maintain Both**
- Keep `RateController` for legacy mode
- Add `LoadTestMode` toggle
- More complex but no breaking changes

**Recommendation**: Go with Option 1 (breaking change) since this is early in the project lifecycle and the new approach is fundamentally better.

## Success Criteria

- [ ] User can configure concurrency-based tests via UI
- [ ] Framework correctly ramps up virtual users
- [ ] Actual TPS is measured and displayed
- [ ] Optional max TPS limit works correctly
- [ ] Charts show both concurrency and TPS over time
- [ ] All tests pass (target 80%+ coverage)
- [ ] Documentation is complete and clear
- [ ] Sample API demo works with new approach

## Related Issues
- Closes #[issue-number]

## References
- JMeter: Thread Groups with ramp-up
- Gatling: Injection profiles (rampUsers, constantUsersPerSec)
- LoadRunner: Virtual User Generator
- K6: VUs and iterations model

---

**Labels**: `enhancement`, `core`, `high-priority`, `breaking-change`  
**Milestone**: v2.0.0  
**Assignee**: @santhoshkuppusamy
