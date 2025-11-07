# Troubleshooting: Metrics Not Displaying

## Issue
Tests start and hit the API endpoints, but no metrics are registered in the dashboard.

## Diagnostic Steps

### 1. Check VajraEdge Logs

Run VajraEdge with enhanced logging:

```bash
./gradlew bootRun
```

Look for these log messages when a test runs:

**Test Starting:**
```
Starting test abc123... with config: ...
```

**Results Being Recorded (every 100 tasks):**
```
Recorded result for task 100: success=true, latency=12.34ms
Recorded result for task 200: success=true, latency=11.23ms
```

**Metrics Being Broadcast (every 500ms):**
```
Broadcasted metrics for test abc123: TPS=98.50, Total=500, Active=45, P50=12.00, P95=18.00
```

**Percentiles Being Calculated:**
```
Calculated percentiles from 500 latency samples
```

### 2. Check Browser Console

Open browser Developer Tools (F12) → Console tab

Look for:

**WebSocket Connection:**
```
WebSocket connected
Subscribed to: /topic/metrics/abc123
```

**Metrics Received:**
```
Received metrics: {totalRequests: 500, currentTps: 98.5, ...}
```

**Errors:**
```
WebSocket disconnected
Failed to parse metrics
```

### 3. Check Network Tab

Browser Developer Tools → Network tab → Filter: WS (WebSocket)

- Should see WebSocket connection to `/ws`
- Status: 101 Switching Protocols (green)
- Click on connection → Messages tab
- Should see metrics flowing every 500ms

### 4. Verify Sample API is Running

```bash
curl http://localhost:8081/api/products
```

Should return JSON array of products. If not, start sample API:

```bash
cd samples/simple-api
../../gradlew bootRun
```

### 5. Check Test Configuration

Ensure HTTP task parameter is correct:

```
Task Type: HTTP Request
Task Parameter: http://localhost:8081/api/products
```

(Not just `localhost:8081` or missing `http://`)

## Common Issues & Solutions

### Issue 1: WebSocket Not Connecting

**Symptoms:**
- Dashboard shows "Disconnected" (red badge)
- No metrics received in browser console

**Solution:**
```bash
# Restart VajraEdge
./gradlew clean build bootRun
```

**Check:**
- `http://localhost:8080/ws` should be accessible
- No firewall blocking WebSocket connections

### Issue 2: Sample API Not Running

**Symptoms:**
- VajraEdge logs show connection errors
- All requests fail

**Solution:**
```bash
# Terminal 1: Start sample API
cd samples/simple-api
../../gradlew bootRun

# Verify
curl http://localhost:8081/api/products
```

### Issue 3: Results Not Being Recorded

**Symptoms:**
- No "Recorded result" logs
- Total requests stay at 0

**Possible Causes:**

1. **Task execution failing silently**
   - Check VajraEdge logs for exceptions
   - Enable TRACE logging: `logging.level.net.vajraedge.perftest=TRACE`

2. **Async completion not working**
   - Results recorded in `future.thenAccept()` callback
   - Check if futures are completing

3. **MetricsCollector not initialized**
   - Check for "Created MetricsCollector" log message
   - Verify PerformanceTestRunner created properly

### Issue 4: Metrics Broadcasting but Not Displaying

**Symptoms:**
- VajraEdge logs show "Broadcasted metrics"
- Browser shows "Connected"
- But charts don't update

**Solution:**

1. **Check browser console for JavaScript errors**
   ```javascript
   // Should see:
   Received metrics: {totalRequests: 500, ...}
   ```

2. **Verify Chart.js is loaded**
   ```javascript
   // In browser console:
   typeof Chart
   // Should return: "function"
   ```

3. **Check if data is reaching the charts**
   ```javascript
   // In browser console:
   console.log(tpsChart.data.datasets[0].data)
   // Should show array of TPS values
   ```

### Issue 5: Zero Metrics Despite Requests

**Symptoms:**
- Logs show "Metrics for test abc123 have zero requests"
- Tests are running

**Debug:**

1. **Check MetricsCollector recording**
   ```
   # Look for this log (should be > 0):
   Calculated percentiles from 500 latency samples
   ```

2. **Verify recordResult() is being called**
   ```
   # Should see these logs:
   Recorded result for task 100: success=true, latency=12.34ms
   ```

3. **Check if futures are completing**
   ```java
   // In PerformanceTestRunner.java
   future.thenAccept(result -> {
       metricsCollector.recordResult(result);  // This must execute
   })
   ```

## Debugging Commands

### Enable Maximum Logging

Edit `src/main/resources/application.properties`:

```properties
logging.level.net.vajraedge.perftest=TRACE
logging.level.org.springframework.messaging=DEBUG
logging.level.org.springframework.web.socket=DEBUG
```

### Test HTTP Task Directly

Create a simple test:

```bash
curl -X POST http://localhost:8080/api/tests \
  -H "Content-Type: application/json" \
  -d '{
    "targetTps": 10,
    "maxConcurrency": 5,
    "testDurationSeconds": 10,
    "rampUpDurationSeconds": 2,
    "taskType": "HTTP",
    "taskParameter": "http://localhost:8081/api/products"
  }'
```

Watch logs for:
- Test started message
- Recorded result messages
- Broadcasted metrics messages

### Check Active Tests

```bash
curl http://localhost:8080/api/tests
```

Should return array of active test IDs.

### Get Test Status

```bash
curl http://localhost:8080/api/tests/{testId}
```

Should show current metrics.

## Expected Log Output (Successful Test)

```
2025-10-26 19:00:00 - TestExecutionService - Starting test abc123... with config: TestConfigRequest{targetTps=100, ...}
2025-10-26 19:00:00 - MetricsCollector - Created MetricsCollector with maxLatencyHistory=10000
2025-10-26 19:00:00 - VirtualThreadTaskExecutor - Created VirtualThreadTaskExecutor with max concurrency: 50
2025-10-26 19:00:00 - PerformanceTestRunner - Starting performance test for PT1M
2025-10-26 19:00:01 - PerformanceTestRunner - Recorded result for task 100: success=true, latency=12.34ms
2025-10-26 19:00:02 - MetricsCollector - Calculated percentiles from 200 latency samples
2025-10-26 19:00:02 - MetricsWebSocketHandler - Broadcasted metrics for test abc123: TPS=98.50, Total=200, Active=45, P50=12.00, P95=18.00
2025-10-26 19:00:03 - PerformanceTestRunner - Recorded result for task 200: success=true, latency=11.23ms
2025-10-26 19:00:03 - MetricsWebSocketHandler - Broadcasted metrics for test abc123: TPS=99.20, Total=400, Active=48, P50=12.10, P95=17.80
...
```

## Still Not Working?

### Collect Full Diagnostics

1. **VajraEdge logs**
   ```bash
   ./gradlew bootRun > vajraedge.log 2>&1
   ```

2. **Browser console output**
   - F12 → Console → Right-click → Save as...

3. **Network trace**
   - F12 → Network → WebSocket messages

4. **Test configuration**
   - Screenshot of test configuration form
   - JSON from POST request

### Manual Verification

Test each component separately:

1. **Sample API works**
   ```bash
   curl http://localhost:8081/api/products
   ```

2. **VajraEdge API works**
   ```bash
   curl http://localhost:8080/actuator/health
   ```

3. **WebSocket endpoint exists**
   ```bash
   curl -i -N -H "Connection: Upgrade" \
        -H "Upgrade: websocket" \
        -H "Sec-WebSocket-Version: 13" \
        -H "Sec-WebSocket-Key: test" \
        http://localhost:8080/ws
   ```
   Should return 101 or 400 (not 404)

4. **Test can be started**
   ```bash
   curl -X POST http://localhost:8080/api/tests -H "Content-Type: application/json" -d '{"targetTps":10,"maxConcurrency":5,"testDurationSeconds":10,"rampUpDurationSeconds":2,"taskType":"SLEEP","taskParameter":100}'
   ```

---

**Most Common Fix:** Restart both applications:
```bash
# Terminal 1
cd samples/simple-api
../../gradlew bootRun

# Terminal 2  
./gradlew clean build bootRun

# Browser
http://localhost:8080
```
