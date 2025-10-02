# Performance Tuning Guide

**Quick Reference for Optimizing Load Test Performance**

---

## 🎯 Quick Start: Get 30% Better Performance in 30 Minutes

### Step 1: Update JVM Flags (5 minutes)

Edit your run script or gradle.properties:

```bash
# Before
java -jar grpc-load-test-client.jar

# After
java -Xms4g -Xmx4g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=100 \
     -XX:G1ReservePercent=20 \
     -Djdk.virtualThreadScheduler.parallelism=8 \
     -jar grpc-load-test-client.jar
```

**Expected Gain**: 10-15% throughput improvement

### Step 2: Optimize System Settings (10 minutes)

```bash
# Increase file descriptors
ulimit -n 65536

# Network buffer tuning (Linux)
sudo sysctl -w net.core.rmem_max=16777216
sudo sysctl -w net.core.wmem_max=16777216
sudo sysctl -w net.ipv4.tcp_rmem="4096 87380 16777216"
sudo sysctl -w net.ipv4.tcp_wmem="4096 65536 16777216"
```

**Expected Gain**: 5-10% throughput improvement

### Step 3: Configure Test Parameters (15 minutes)

```yaml
# config.yaml
load:
  tps: 5000
  max_concurrent_requests: 2000  # Set to 40% of target TPS
  ramp_up_duration: "PT30S"      # 30 second ramp-up
  
reporting:
  reporting_interval_seconds: 10  # Don't report too frequently
```

**Expected Gain**: 10-20% more stable performance

---

## 📊 Performance Tuning Matrix

### Scenario-Based Configuration

#### 1. High Throughput (10,000+ TPS)

**Goal**: Maximize requests per second

```yaml
load:
  tps: 10000
  max_concurrent_requests: 4000  # 40% of TPS
  ramp_up_duration: "PT60S"      # Longer ramp-up

client:
  timeout_milliseconds: 5000
  
reporting:
  reporting_interval_seconds: 15  # Less frequent reporting
```

**JVM Settings**:
```bash
-Xms8g -Xmx8g \
-XX:+UseG1GC \
-XX:MaxGCPauseMillis=200 \
-XX:G1ReservePercent=20 \
-Djdk.virtualThreadScheduler.parallelism=16
```

**Expected Performance**:
- Throughput: 9,800-10,000 TPS (98-100% of target)
- Average Latency: 15-25ms
- P99 Latency: 50-80ms
- CPU Usage: 60-70%
- Memory: 6-7 GB

#### 2. Low Latency (< 10ms average)

**Goal**: Minimize response time

```yaml
load:
  tps: 2000
  max_concurrent_requests: 500   # Lower concurrency
  ramp_up_duration: "PT20S"

client:
  timeout_milliseconds: 1000     # Tight timeout
  
reporting:
  reporting_interval_seconds: 5
```

**JVM Settings**:
```bash
-Xms2g -Xmx2g \
-XX:+UseG1GC \
-XX:MaxGCPauseMillis=50 \       # Aggressive GC tuning
-XX:G1ReservePercent=15 \
-Djdk.virtualThreadScheduler.parallelism=4
```

**Expected Performance**:
- Throughput: 2,000 TPS
- Average Latency: 5-8ms
- P99 Latency: 15-25ms
- CPU Usage: 30-40%
- Memory: 1.5-2 GB

#### 3. Long-Running Stress Test (Hours)

**Goal**: Stability over extended periods

```yaml
load:
  tps: 5000
  duration: "PT4H"               # 4 hours
  max_concurrent_requests: 2000
  ramp_up_duration: "PT2M"       # Slow ramp-up

warmup:
  warmup_duration: "PT5M"        # 5 minute warmup

reporting:
  reporting_interval_seconds: 30  # Less frequent reporting
  output_format: "csv"            # CSV for time-series analysis
```

**JVM Settings**:
```bash
-Xms4g -Xmx4g \                  # Fixed heap
-XX:+UseG1GC \
-XX:MaxGCPauseMillis=100 \
-XX:+PrintGCDetails \             # Monitor GC
-XX:+PrintGCDateStamps \
-Xloggc:gc.log \
-XX:+UseGCLogFileRotation \
-XX:NumberOfGCLogFiles=5 \
-XX:GCLogFileSize=10M
```

**Expected Performance**:
- Throughput: 5,000 TPS sustained
- Memory: Stable over time (no leaks)
- GC Pause: < 0.5% of runtime
- Success Rate: > 99.9%

#### 4. Spike Testing (Rapid Load Changes)

**Goal**: Test system response to sudden load

```yaml
# Run multiple tests with different TPS
tests:
  - tps: 1000
    duration: "PT2M"
  - tps: 10000              # 10x spike
    duration: "PT1M"
  - tps: 1000               # Back to baseline
    duration: "PT2M"
```

**Script**:
```bash
#!/bin/bash
run_test() {
  java -Xms4g -Xmx4g -XX:+UseG1GC \
       -jar grpc-load-test-client.jar \
       --tps $1 --duration $2 --output-file "spike_${1}_tps.json"
}

run_test 1000 120
sleep 10
run_test 10000 60
sleep 10
run_test 1000 120
```

---

## 🔧 Common Performance Issues & Solutions

### Issue 1: Can't Achieve Target TPS

**Symptoms**:
```
Target TPS: 10,000
Actual TPS: 7,500 (75%)
CPU Usage: 40%
Active Tasks: Maxed at concurrency limit
```

**Diagnosis**: Insufficient concurrency

**Solution**:
```yaml
load:
  max_concurrent_requests: 4000  # Increase from 2000
```

**Rule of Thumb**: Concurrency = TPS × Average_Latency_Seconds

Example:
- TPS: 10,000
- Avg Latency: 20ms (0.020s)
- Required Concurrency: 10,000 × 0.020 = 200

Add buffer: 200 × 2 = 400 concurrent requests

### Issue 2: High CPU Usage (> 80%)

**Symptoms**:
```
CPU Usage: 85-95%
TPS: At target but unstable
GC Time: 15% of runtime
```

**Diagnosis**: Insufficient resources or GC pressure

**Solution 1 - Increase Heap**:
```bash
# Before
-Xms2g -Xmx2g

# After  
-Xms6g -Xmx6g
```

**Solution 2 - Reduce Metrics Collection**:
```java
// Increase max latency history buffer
MetricsCollector collector = new MetricsCollector(5000); // Down from 10000
```

**Solution 3 - Less Frequent Reporting**:
```yaml
reporting:
  reporting_interval_seconds: 30  # Up from 10
```

### Issue 3: High Memory Usage

**Symptoms**:
```
Memory Usage: 90% of heap
Frequent Full GCs
Performance degradation over time
```

**Diagnosis**: Memory leak or inefficient memory usage

**Solutions**:

1. **Monitor heap over time**:
```bash
jcmd <pid> GC.heap_info
```

2. **Take heap dump when memory high**:
```bash
jcmd <pid> GC.heap_dump /tmp/heap.hprof
```

3. **Reduce latency history**:
```yaml
# Reduce memory footprint
metrics:
  max_latency_history: 5000  # Down from 10000
```

4. **Enable string deduplication**:
```bash
-XX:+UseStringDeduplication
```

### Issue 4: P99 Latency Spikes

**Symptoms**:
```
Average Latency: 15ms
P50 Latency: 12ms
P95 Latency: 25ms
P99 Latency: 200ms  ⚠️ High!
```

**Diagnosis**: GC pauses or system resource contention

**Solutions**:

1. **Tune GC for lower pause times**:
```bash
-XX:+UseG1GC \
-XX:MaxGCPauseMillis=50 \    # Aggressive target
-XX:G1ReservePercent=20
```

2. **Increase concurrency to smooth out spikes**:
```yaml
load:
  max_concurrent_requests: 3000  # Increase buffer
```

3. **Check system resources**:
```bash
# CPU steal time (in VMs)
top -b -n 1 | grep Cpu

# I/O wait
iostat -x 1 5

# Network drops
netstat -s | grep -i drop
```

### Issue 5: Connection Failures

**Symptoms**:
```
Success Rate: 85% (Target: 99%+)
Error: "Connection refused"
Error: "Too many open files"
```

**Solutions**:

1. **Increase file descriptors**:
```bash
ulimit -n 65536
```

2. **Tune connection pool** (if applicable):
```yaml
client:
  max_connections: 100
  connection_timeout_ms: 5000
```

3. **Check server capacity**:
```bash
# Server connections
netstat -an | grep ESTABLISHED | wc -l

# Server load
uptime
```

---

## 📈 Performance Benchmarking

### Baseline Performance Test

```bash
#!/bin/bash
# Run standard benchmark

echo "Starting baseline performance test..."

java -Xms4g -Xmx4g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=100 \
     -XX:+PrintGCDetails \
     -XX:+PrintGCDateStamps \
     -Xloggc:gc_baseline.log \
     -jar grpc-load-test-client.jar \
     --host localhost \
     --port 8080 \
     --tps 5000 \
     --duration 300 \
     --concurrency 2000 \
     --output-format json \
     --output-file baseline_results.json

# Analyze results
echo "Throughput: $(jq '.metrics.tps' baseline_results.json) TPS"
echo "Avg Latency: $(jq '.metrics.avgLatencyMs' baseline_results.json) ms"
echo "P95 Latency: $(jq '.metrics.percentiles.P95' baseline_results.json) ms"
echo "Success Rate: $(jq '.metrics.successRate' baseline_results.json)%"

# Check GC logs
echo "GC Analysis:"
grep "Full GC" gc_baseline.log | wc -l
```

### Performance Comparison Script

```bash
#!/bin/bash
# Compare two configurations

run_test() {
  local name=$1
  local config=$2
  
  java -Xms4g -Xmx4g -XX:+UseG1GC \
       -jar grpc-load-test-client.jar \
       --config $config \
       --output-file results_${name}.json
}

# Run tests
run_test "baseline" "config_baseline.yaml"
run_test "optimized" "config_optimized.yaml"

# Compare results
echo "=== Performance Comparison ==="
echo "Configuration | TPS | Avg Latency | P95 | Success Rate"
echo "-----------------------------------------------------------"
for name in baseline optimized; do
  tps=$(jq '.metrics.tps' results_${name}.json)
  avg=$(jq '.metrics.avgLatencyMs' results_${name}.json)
  p95=$(jq '.metrics.percentiles.P95' results_${name}.json)
  success=$(jq '.metrics.successRate' results_${name}.json)
  echo "$name | $tps | $avg ms | $p95 ms | $success%"
done
```

---

## 🎓 Advanced Tuning Techniques

### 1. CPU Pinning (Linux)

Pin the load test process to specific CPU cores:

```bash
# Pin to cores 0-7
taskset -c 0-7 java -jar grpc-load-test-client.jar

# For NUMA systems, pin to single NUMA node
numactl --cpunodebind=0 --membind=0 java -jar grpc-load-test-client.jar
```

**When to Use**:
- Dedicated test machine
- Reduces CPU cache misses
- More consistent performance

### 2. Huge Pages (Linux)

Enable huge pages for better memory performance:

```bash
# Check current huge pages
grep HugePages /proc/meminfo

# Enable huge pages
sudo sysctl -w vm.nr_hugepages=512

# Run with huge pages
java -XX:+UseLargePages -jar grpc-load-test-client.jar
```

**When to Use**:
- Large heap sizes (> 8GB)
- Long-running tests
- Reduces TLB misses

### 3. JIT Compiler Tuning

For maximum performance after warmup:

```bash
# Aggressive JIT compilation
-XX:+TieredCompilation \
-XX:TieredStopAtLevel=4 \
-XX:+UseCompressedOops \
-XX:+UseCompressedClassPointers

# For long-running tests, prefer C2 compiler
-XX:-TieredCompilation
```

### 4. G1GC Advanced Tuning

Fine-tune G1GC for your workload:

```bash
# High throughput focus
-XX:+UseG1GC \
-XX:MaxGCPauseMillis=200 \
-XX:G1ReservePercent=10 \
-XX:InitiatingHeapOccupancyPercent=45 \
-XX:G1HeapRegionSize=16M

# Low latency focus
-XX:+UseG1GC \
-XX:MaxGCPauseMillis=50 \
-XX:G1ReservePercent=20 \
-XX:InitiatingHeapOccupancyPercent=35 \
-XX:G1HeapRegionSize=8M
```

### 5. Monitoring and Profiling

**Enable JMX Monitoring**:
```bash
-Dcom.sun.management.jmxremote \
-Dcom.sun.management.jmxremote.port=9010 \
-Dcom.sun.management.jmxremote.authenticate=false \
-Dcom.sun.management.jmxremote.ssl=false
```

**Enable Flight Recorder**:
```bash
-XX:StartFlightRecording=duration=60s,filename=recording.jfr
```

**CPU Profiling with async-profiler**:
```bash
-agentpath:/path/to/libasyncProfiler.so=start,event=cpu,file=profile.html
```

---

## 📊 Performance Monitoring

### Key Metrics to Track

**1. Throughput Metrics**:
```bash
# Real-time TPS monitoring
watch -n 5 "jcmd <pid> PerfCounter.print | grep 'tps'"
```

**2. JVM Metrics**:
```bash
# Heap usage
jstat -gc <pid> 1000

# Thread count
jstat -gcutil <pid> 1000
```

**3. System Metrics**:
```bash
# CPU and memory
top -p <pid>

# Network I/O
iftop -i eth0

# Disk I/O
iostat -x 1
```

### Performance Dashboard

Create a real-time monitoring script:

```bash
#!/bin/bash
# performance_monitor.sh

PID=$(pgrep -f "grpc-load-test-client.jar")

while true; do
  clear
  echo "=== Performance Monitor ==="
  echo "Time: $(date)"
  echo ""
  
  # JVM Stats
  echo "--- JVM Stats ---"
  jstat -gcutil $PID | tail -1
  echo ""
  
  # System Stats
  echo "--- System Stats ---"
  top -b -n 1 -p $PID | grep java
  echo ""
  
  # Network Stats
  echo "--- Network Stats ---"
  netstat -an | grep ESTABLISHED | wc -l
  
  sleep 5
done
```

---

## 🎯 Performance Testing Checklist

### Pre-Test Checklist

- [ ] **System Configuration**
  - [ ] File descriptor limit increased (ulimit -n 65536)
  - [ ] Network buffers tuned
  - [ ] Sufficient disk space for logs
  - [ ] Time sync enabled (NTP)

- [ ] **JVM Configuration**
  - [ ] Heap size set appropriately
  - [ ] GC tuned for workload
  - [ ] Virtual thread scheduler configured
  - [ ] Monitoring enabled

- [ ] **Test Configuration**
  - [ ] Concurrency calculated correctly
  - [ ] Ramp-up duration appropriate
  - [ ] Timeout values set
  - [ ] Output format configured

- [ ] **Target System**
  - [ ] Target system warmed up
  - [ ] Target system monitored
  - [ ] Baseline performance established
  - [ ] Network connectivity verified

### During Test Monitoring

- [ ] **Real-time Checks** (every 5 minutes)
  - [ ] TPS at target
  - [ ] CPU usage reasonable
  - [ ] Memory stable
  - [ ] No error spikes
  - [ ] Network stable

- [ ] **Resource Alerts**
  - [ ] CPU > 90%: Investigate
  - [ ] Memory > 90%: Check for leaks
  - [ ] Success rate < 95%: Check errors
  - [ ] P99 > 10x P50: Check system health

### Post-Test Analysis

- [ ] **Results Validation**
  - [ ] Test completed successfully
  - [ ] Duration as expected
  - [ ] Success rate acceptable
  - [ ] Latency within SLA

- [ ] **Performance Analysis**
  - [ ] Compare to baseline
  - [ ] Analyze GC logs
  - [ ] Review error logs
  - [ ] Check resource usage

- [ ] **Report Generation**
  - [ ] Generate charts
  - [ ] Document findings
  - [ ] Note anomalies
  - [ ] Recommend actions

---

## 🚀 Performance Optimization Examples

### Example 1: Optimize for 10K TPS

**Before**:
```yaml
load:
  tps: 10000
  max_concurrent_requests: 1000
  ramp_up_duration: "PT10S"

reporting:
  reporting_interval_seconds: 5
```

**Result**: 7,500 TPS (75%), high CPU

**After**:
```yaml
load:
  tps: 10000
  max_concurrent_requests: 4000      # ✅ Increased
  ramp_up_duration: "PT60S"          # ✅ Longer ramp

reporting:
  reporting_interval_seconds: 15     # ✅ Less frequent
```

**JVM Flags**:
```bash
-Xms8g -Xmx8g \                      # ✅ More heap
-XX:+UseG1GC \
-XX:MaxGCPauseMillis=200 \
-Djdk.virtualThreadScheduler.parallelism=16  # ✅ More carriers
```

**Result**: 9,800 TPS (98%), CPU 65%

### Example 2: Reduce P99 Latency Spikes

**Before**:
```
Avg: 15ms, P50: 12ms, P95: 28ms, P99: 180ms
```

**Changes**:

1. **GC Tuning**:
```bash
-XX:MaxGCPauseMillis=50              # ✅ Aggressive
-XX:G1ReservePercent=20              # ✅ More reserve
```

2. **Configuration**:
```yaml
load:
  max_concurrent_requests: 3000      # ✅ More buffer
```

3. **System**:
```bash
# Pin to specific cores
taskset -c 0-7 java -jar ...
```

**After**:
```
Avg: 15ms, P50: 12ms, P95: 26ms, P99: 48ms  # ✅ Much better!
```

---

## 📚 Performance Tuning Resources

### Profiling Tools

1. **async-profiler**: https://github.com/jvm-profiling-tools/async-profiler
2. **Java Mission Control**: https://www.oracle.com/java/technologies/jdk-mission-control.html
3. **VisualVM**: https://visualvm.github.io/
4. **JMH**: https://github.com/openjdk/jmh

### Documentation

1. **G1GC Tuning**: https://www.oracle.com/technical-resources/articles/java/g1gc.html
2. **Virtual Threads**: https://openjdk.org/jeps/444
3. **JVM Options**: https://chriswhocodes.com/

### Books

1. **"Java Performance"** by Scott Oaks
2. **"Optimizing Java"** by Benjamin J Evans, James Gough, Chris Newland

---

## 🎯 Summary

**Quick Wins** (30 minutes):
1. Add JVM flags: -Xms4g -Xmx4g -XX:+UseG1GC
2. Increase ulimit: ulimit -n 65536
3. Tune concurrency: 40% of target TPS

**Medium Impact** (2-4 hours):
1. Optimize system networking
2. Tune GC for workload
3. Configure proper ramp-up

**High Impact** (1+ day):
1. Implement code optimizations (see PERFORMANCE_ANALYSIS.md)
2. Add comprehensive monitoring
3. Establish performance baselines

**Expected Overall Gain**: 30-50% better performance

---

*For detailed performance analysis and code-level optimizations, see [PERFORMANCE_ANALYSIS.md](./PERFORMANCE_ANALYSIS.md)*
