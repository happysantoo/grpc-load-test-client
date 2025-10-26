# Running the VajraEdge Demo

## Quick Summary

You now have a complete demo environment:
- ✅ Sample Spring Boot API (port 8081)
- ✅ HTTP Task implementation
- ✅ Updated VajraEdge dashboard with HTTP support
- ✅ Comprehensive tests (all passing)
- ✅ Detailed documentation

## How to Run the Demo

### Terminal 1: Start the Sample API

```bash
cd samples/simple-api
../../gradlew bootRun
```

**Expected**: Server starts on port 8081

**Verify**:
```bash
curl http://localhost:8081/api/products
```

### Terminal 2: Start VajraEdge

```bash
cd /Users/santhoshkuppusamy/IdeaProjects/vajraedge
./gradlew bootRun
```

**Expected**: Server starts on port 8080

### Browser: Open Dashboard

Navigate to: **http://localhost:8080**

### Configure Test in Dashboard

1. **Target TPS**: 100
2. **Max Concurrency**: 50
3. **Test Duration**: 60 seconds
4. **Ramp-Up Duration**: 10 seconds
5. **Task Type**: Select **"HTTP Request"**
6. **Task Parameter**: `http://localhost:8081/api/products`

### Start Test

Click **"Start Test"** and watch:
- Real-time TPS chart (ramps from 0 to 100)
- Latency percentiles (P50 ~10-15ms, P95 ~15-25ms, P99 ~20-35ms)
- Live metrics updating every 500ms
- Success rate near 100%

## What You'll See

### Sample API Terminal
```
2025-10-26 19:00:15.234  INFO ... : Started SimpleApiApplication in 2.3 seconds
... (access logs showing incoming requests)
```

### VajraEdge Terminal
```
2025-10-26 19:00:30.567  INFO ... : Started Application in 3.1 seconds
2025-10-26 19:01:00.123  INFO ... : Starting test abc123... with config: ...
```

### Dashboard
- **Connection Status**: Green "Connected" badge
- **TPS Chart**: Smooth ramp from 0 to 100
- **Latency Chart**: Stable P50/P95/P99 lines
- **Metrics**: Active tasks, total requests, success rate
- **Percentiles Table**: Detailed breakdown

## Expected Performance

For the sample API with 10ms sleep:

| Metric | Value |
|--------|-------|
| P50 (Median) | 10-15ms |
| P95 | 15-25ms |
| P99 | 20-35ms |
| Success Rate | 99.9%+ |
| Max TPS | 1000+ |

## Try Different Scenarios

### Light Load
```
TPS: 50, Concurrency: 25, Duration: 30s, Ramp-Up: 5s
Expected: Very stable, low latency
```

### Moderate Load
```
TPS: 200, Concurrency: 100, Duration: 60s, Ramp-Up: 10s
Expected: Slightly higher latency, still stable
```

### Stress Test
```
TPS: 1000, Concurrency: 500, Duration: 60s, Ramp-Up: 10s
Expected: Test the limits
```

## Documentation

- **Detailed Guide**: `samples/DEMO_GUIDE.md`
- **Sample API README**: `samples/simple-api/README.md`
- **Main README**: `README.md`

## Troubleshooting

**Sample API not responding?**
```bash
# Check if running
curl http://localhost:8081/api/products

# Restart
cd samples/simple-api
../../gradlew bootRun
```

**VajraEdge not loading?**
```bash
# Check if running
curl http://localhost:8080/actuator/health

# Restart
./gradlew bootRun
```

**WebSocket disconnected?**
- Refresh browser
- Check console for errors
- Ensure VajraEdge is fully started

## Next Steps

1. **Modify Sample API**: Change sleep duration, add more endpoints
2. **Test Public APIs**: Try `https://httpbin.org/get` or `https://api.github.com/users/octocat`
3. **Create Custom Tasks**: Implement database or message queue tasks
4. **Integrate CI/CD**: Add performance tests to your pipeline

---

**Everything is ready to demo!** 🚀
