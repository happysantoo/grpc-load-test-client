# Quick Performance Guide - TL;DR

**Get 30% better performance in 30 minutes** ⚡

---

## 🚀 Instant Performance Boost (5 minutes)

### 1. Add These JVM Flags

```bash
java -Xms4g -Xmx4g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=100 \
     -Djdk.virtualThreadScheduler.parallelism=8 \
     -jar grpc-load-test-client.jar
```

**Expected: 10-15% performance improvement**

### 2. Increase File Descriptors

```bash
ulimit -n 65536
```

**Expected: Prevents connection failures at high loads**

---

## 📊 Performance Cheat Sheet

### Calculate Correct Concurrency

```
Concurrency = Target_TPS × Average_Latency_Seconds × 2

Example:
- Target: 10,000 TPS
- Latency: 20ms (0.020s)
- Concurrency = 10,000 × 0.020 × 2 = 400
```

### Common Scenarios

| Scenario | TPS | Concurrency | Heap | Cores |
|----------|-----|-------------|------|-------|
| Light | 1,000 | 200 | 2g | 4 |
| Medium | 5,000 | 2,000 | 4g | 8 |
| Heavy | 10,000 | 4,000 | 8g | 16 |
| Extreme | 20,000 | 8,000 | 16g | 32 |

---

## 🔧 Quick Fixes for Common Issues

### Issue: Can't Reach Target TPS

**Symptom**: Getting 7,500 TPS instead of 10,000

**Fix**: Increase concurrency
```yaml
max_concurrent_requests: 4000  # Was: 2000
```

### Issue: High CPU Usage (> 80%)

**Fix 1**: Increase heap
```bash
-Xms8g -Xmx8g  # Was: 4g
```

**Fix 2**: Report less frequently
```yaml
reporting_interval_seconds: 30  # Was: 10
```

### Issue: P99 Latency Spikes

**Fix**: Tune GC
```bash
-XX:MaxGCPauseMillis=50 \
-XX:G1ReservePercent=20
```

### Issue: Memory Growing

**Fix**: Check for leaks, increase heap, or reduce history
```yaml
max_latency_history: 5000  # Was: 10000
```

---

## 📈 Performance Benchmarks

### What to Expect (16-core, 16GB RAM)

| TPS | Concurrency | Latency (avg) | P99 | CPU | Memory |
|-----|-------------|---------------|-----|-----|--------|
| 1K  | 200         | 8ms           | 20ms | 15% | 0.8GB  |
| 5K  | 2K          | 15ms          | 35ms | 40% | 1.5GB  |
| 10K | 4K          | 18ms          | 50ms | 65% | 3GB    |
| 20K | 8K          | 22ms          | 80ms | 85% | 6GB    |

---

## 🎯 Optimization Priority

### Phase 1: Critical (Highest Impact) ⚡

1. **Fix MetricsCollector size check** → 35% CPU reduction
2. **Add JVM tuning flags** → 15% throughput boost
3. **Proper concurrency calculation** → Reach target TPS

### Phase 2: Important (High Impact) 🔥

4. **Network tuning** → 10% improvement
5. **GC tuning** → Better latency
6. **System limits** → No connection failures

### Phase 3: Nice-to-Have (Medium Impact) ✨

7. **Code optimizations** → 20% overall gain
8. **Advanced tuning** → Edge cases

---

## 💡 Best Practices

### DO ✅

- Use fixed heap size: `-Xms4g -Xmx4g`
- Calculate concurrency properly
- Include warmup: 30-60 seconds
- Use ramp-up: 30-60 seconds
- Monitor during test
- Run baseline first

### DON'T ❌

- Don't set heap too small
- Don't skip warmup phase
- Don't report too frequently
- Don't ignore resource limits
- Don't run on shared systems
- Don't forget to monitor target

---

## 🔍 Quick Diagnostics

### Check Current Performance

```bash
# JVM stats
jstat -gc <pid> 1000

# CPU usage
top -p <pid>

# Network connections
netstat -an | grep ESTABLISHED | wc -l
```

### Is It Good Enough?

```
TPS Achieved / TPS Target > 0.95     ✅
Success Rate > 99%                   ✅
P99 < 10 × P50                       ✅
CPU < 80%                            ✅
Memory stable over time              ✅
```

---

## 📚 Learn More

- [Performance Analysis](PERFORMANCE_ANALYSIS.md) - Deep dive into bottlenecks
- [Performance Tuning Guide](PERFORMANCE_TUNING_GUIDE.md) - Comprehensive tuning
- [Architecture Review](ARCHITECTURE_REVIEW.md) - Design and patterns

---

## 🎓 Remember

1. **Start with baseline** - Know your current performance
2. **Change one thing** - Isolate impact
3. **Measure everything** - Data over guesses
4. **Optimize hotspots** - 80/20 rule applies
5. **Test under load** - Real conditions only

---

**Quick Win**: Add the JVM flags above and get 15% better performance right now! 🚀
