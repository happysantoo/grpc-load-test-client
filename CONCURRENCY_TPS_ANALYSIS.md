# Concurrency vs TPS Analysis

## Your Observation
- **Initial**: 100 virtual users → 13,300 TPS
- **Ramp-up**: 200, 300, 400... virtual users → TPS stays at ~13,300

## Why This Happens (Root Cause Analysis)

### The Correct Behavior ✅

This is **exactly correct** and reveals important performance characteristics:

#### 1. **Your HTTP Service is the Bottleneck**
```
100 users → 13,300 TPS (service saturated)
200 users → 13,300 TPS (service still saturated)
300 users → 13,300 TPS (service still saturated)
```

**What's happening:**
- Your HTTP service can handle a maximum of ~13,300 requests/second
- Once you hit this limit, adding more virtual users doesn't increase throughput
- The extra users just wait longer for responses

#### 2. **What SHOULD Change As You Ramp Up**

| Metric | Expected Behavior |
|--------|------------------|
| **TPS** | Stays constant at ~13,300 (service limit) ✅ |
| **Active Virtual Users** | Increases: 100 → 200 → 300 → 400... ✅ |
| **Average Latency** | **INCREASES** (more users = more competition) ⚠️ |
| **P95/P99 Latency** | **INCREASES SIGNIFICANTLY** ⚠️ |
| **Pending Tasks** | May increase if queue builds up |

### The Key Metric: Latency

**This is what you should watch during ramp-up:**

```
100 users:
  - TPS: 13,300
  - Avg Latency: 7.5ms  (100 users ÷ 13,300 TPS = 7.5ms per request)

200 users:
  - TPS: 13,300 (same!)
  - Avg Latency: 15ms   (200 users ÷ 13,300 TPS = 15ms per request) ⚠️

400 users:
  - TPS: 13,300 (same!)
  - Avg Latency: 30ms   (400 users ÷ 13,300 TPS = 30ms per request) ⚠️
```

**Why latency increases:**
- Same TPS (13,300) is now shared among more users
- Each user has to wait longer for their turn
- Queue depth increases

## Is This a Problem?

### No - This is Capacity Testing! ✅

You've successfully discovered:
1. **Maximum throughput**: ~13,300 TPS
2. **Saturation point**: 100 concurrent users is enough to saturate the service
3. **Scalability limit**: Adding more users doesn't help - the service is the bottleneck

### What You're Testing

**Concurrency-based testing** answers:
- ✅ "How many concurrent users can the system handle?"
- ✅ "At what point does latency become unacceptable?"
- ✅ "What is the maximum throughput under load?"

**NOT**:
- ❌ "Can I get more than 13,300 TPS?" (No - service limit reached)

## Diagnosing the Bottleneck

### Is it the HTTP Service or VajraEdge?

**Quick Test:**

1. **Check pending tasks**:
   - If pending tasks = 0 → VajraEdge can keep up ✅
   - If pending tasks > 0 → VajraEdge is the bottleneck ⚠️

2. **Check latency distribution**:
   - If P50 ≈ P99 → Service responds consistently (good)
   - If P99 >> P50 → Service is struggling (queue buildup)

3. **Run simple load test**:
   ```bash
   # Test with different concurrency levels
   100 users → 13,300 TPS
   50 users  → ??? TPS (should be ~6,650 if linear)
   10 users  → ??? TPS (should be ~1,330 if linear)
   ```

### Expected Results

If the service is the bottleneck (most likely):
```
10 users  → ~1,300 TPS   (linear relationship)
50 users  → ~6,500 TPS   (linear relationship)
100 users → 13,300 TPS   (saturated!)
200 users → 13,300 TPS   (saturated!)
```

If VajraEdge is the bottleneck (unlikely):
```
10 users  → ~1,300 TPS
50 users  → ~6,500 TPS
100 users → ~10,000 TPS  (VajraEdge maxed out)
200 users → ~10,000 TPS  (VajraEdge maxed out)
Pending tasks would increase
```

## What To Look For

### Healthy Load Test (Service is Bottleneck)
```
Virtual Users: 100 → 200 → 300
TPS:          13.3K → 13.3K → 13.3K  ✅ (constant - service limit)
Active Tasks: 100 → 200 → 300       ✅ (increases linearly)
Pending:      0 → 0 → 0              ✅ (VajraEdge keeping up)
Avg Latency:  7ms → 15ms → 30ms     ⚠️ (increases - expected!)
P99 Latency:  20ms → 50ms → 100ms   ⚠️ (increases more - queue buildup)
```

### Unhealthy (VajraEdge is Bottleneck)
```
Virtual Users: 100 → 200 → 300
TPS:          10K → 10K → 10K        ⚠️ (capped below service capacity)
Active Tasks: 100 → 150 → 150       ⚠️ (not increasing linearly)
Pending:      0 → 50 → 150           🔴 (queue building up)
```

## Recommendations

### 1. Verify Active Tasks Increase Linearly
**Expected:**
- 100 users → 100 active tasks
- 200 users → 200 active tasks
- 300 users → 300 active tasks

**If not**, there's a concurrency limiting issue.

### 2. Monitor Latency Trends
- Watch **P95 and P99** latency during ramp-up
- These should increase as more users compete
- **Sudden spikes** indicate queue saturation

### 3. Find Optimal Concurrency
The "sweet spot" is where:
- TPS is maximized (~13,300)
- Latency is acceptable (< 100ms?)
- Error rate is low (< 1%)

For your service, this might be:
- **100 users**: Max throughput, reasonable latency
- **200 users**: Same throughput, higher latency (wasteful)

### 4. Improve the HTTP Service (If Needed)
If 13,300 TPS isn't enough:
- Scale horizontally (more instances)
- Optimize the service code
- Add caching
- Use connection pooling
- Optimize database queries

## Conclusion

**Your observations are correct!** 

The TPS staying constant at 13,300 while ramping up virtual users is **expected behavior** when the HTTP service is the bottleneck. This is exactly what concurrency-based load testing is designed to reveal.

The key metrics to watch are:
1. ✅ **TPS** - shows maximum throughput
2. ✅ **Active Tasks** - should increase with ramp-up
3. ⚠️ **Latency** - shows degradation under load
4. ⚠️ **Pending Tasks** - shows if VajraEdge is keeping up

You've successfully stress-tested the system and found its limits! 🎉
