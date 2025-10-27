# Diagnostic Steps for Empty Panels Issue

## Current Status
You're experiencing empty panels for:
- ❌ Active Tests panel
- ❌ TPS Over Time chart  
- ❌ Latency Percentiles chart

## What I've Added
Comprehensive logging throughout the metrics pipeline to help diagnose the issue:

1. **WebSocket Connection**: STOMP debug logging enabled
2. **Metrics Reception**: Logs when metrics are received via WebSocket
3. **Metrics Display**: Logs when updating UI elements
4. **Chart Updates**: Logs when charts are updated
5. **Percentiles**: Logs when percentile table is updated

## Step-by-Step Diagnostic Process

### Step 1: Restart Both Applications

#### Terminal 1 - Sample API (Port 8081)
```bash
cd samples/simple-api
../../gradlew bootRun
```

Wait for: `Started SimpleApiApplication`

#### Terminal 2 - VajraEdge (Port 8080)
```bash
cd /Users/santhoshkuppusamy/IdeaProjects/vajraedge
./gradlew clean bootRun
```

Wait for: `Started Application`

### Step 2: Open Browser with Console

1. Open Chrome/Firefox
2. Navigate to: `http://localhost:8080`
3. Open Developer Tools (F12 or Cmd+Option+I)
4. Go to **Console** tab
5. Clear console (click clear button or Cmd+K)

### Step 3: Check Initial Connection

Look for these console messages immediately:

#### ✅ Expected (Good):
```
Attempting to connect to WebSocket...
STOMP: Opening Web Socket...
STOMP: Web Socket Opened...
STOMP: >>> CONNECT
STOMP: <<< CONNECTED
Connected: CONNECTED
```

#### ❌ If you see errors:
- `WebSocket connection failed` - Backend not running
- `404 /ws` - WebSocket endpoint configuration issue

### Step 4: Start a Test

1. Configure test:
   - Task Type: **HTTP**
   - URL: `http://localhost:8081/api/products`
   - Target TPS: `100`
   - Max Concurrency: `50`
   - Test Duration: `60` seconds
   - Ramp-Up: `5` seconds

2. Click **"Start Test"**

### Step 5: Monitor Console Logs

You should see a sequence like this:

#### A. Test Started
```javascript
Test started: {testId: 'xxx-xxx-xxx', status: 'RUNNING', message: 'Test started successfully'}
```

#### B. WebSocket Subscription
```
STOMP: >>> SUBSCRIBE
destination:/topic/metrics/xxx-xxx-xxx
id:sub-0

Subscribed to metrics for test: xxx-xxx-xxx
```

#### C. Metrics Updates (every 500ms)
```javascript
STOMP: <<< MESSAGE
destination:/topic/metrics/xxx-xxx-xxx

Received metrics update: {
  testId: "xxx-xxx-xxx",
  timestamp: 1730000000000,
  totalRequests: 150,
  successfulRequests: 150,
  failedRequests: 0,
  successRate: 100,
  activeTasks: 45,
  currentTps: 98.5,
  latencyPercentiles: {
    p50: 12.5,
    p75: 15.0,
    p90: 18.0,
    p95: 20.0,
    p99: 25.0,
    "p99.9": 30.0
  },
  avgLatencyMs: 13.2,
  minLatencyMs: 10.0,
  maxLatencyMs: 35.0
}

Updating metrics display: {totalRequests: 150, currentTps: 98.5, ...}
Updating percentiles: {p50: 12.5, p75: 15.0, ...}
updateCharts called with: {totalRequests: 150, currentTps: 98.5, ...}
```

### Step 6: Diagnose Based on Logs

#### Scenario A: No WebSocket Connection
**Symptoms**: Don't see "Connected: CONNECTED"

**Possible Causes**:
- Backend WebSocket not configured properly
- Port conflict
- CORS issue

**Action**: Check backend logs for WebSocket initialization errors

#### Scenario B: WebSocket Connected but No Subscriptions
**Symptoms**: See "Connected" but no "Subscribed to metrics for test"

**Possible Causes**:
- Test didn't start properly
- testId not being passed to subscribeToMetrics()

**Action**: Check network tab for `/api/tests` POST request and response

#### Scenario C: Subscribed but No Metrics
**Symptoms**: See "Subscribed" but no "Received metrics update"

**Possible Causes**:
- Backend not broadcasting metrics
- Wrong topic name
- Test not actually running

**Action**: 
1. Check backend logs for `Broadcasted metrics for test`
2. Verify test is in active tests: `curl http://localhost:8080/api/tests`

#### Scenario D: Receiving Metrics but UI Not Updating
**Symptoms**: See "Received metrics update" but panels stay empty

**Possible Causes**:
- JavaScript error in updateMetricsDisplay or updateCharts
- DOM elements not found
- Chart.js not initialized

**Action**: Look for JavaScript errors in console (red text)

### Step 7: Check Active Tests Endpoint

Open new browser tab or use curl:
```bash
curl http://localhost:8080/api/tests | jq
```

**Expected Response**:
```json
{
  "activeTests": {
    "xxx-xxx-xxx-xxx-xxx": "RUNNING"
  },
  "count": 1
}
```

**If empty**:
```json
{
  "activeTests": {},
  "count": 0
}
```
→ Test didn't start or already completed

### Step 8: Check Backend Logs

Look for these in VajraEdge terminal:

#### Test Start
```
INFO  - TestExecutionService - Starting test with config: ...
INFO  - PerformanceTestRunner - Starting test with TPS schedule: ...
```

#### Metrics Broadcasting
```
DEBUG - MetricsWebSocketHandler - Broadcasted metrics for test xxx: TPS=98.50, Total=150, Active=45, P50=12.50, P95=20.00
```

#### If you see:
```
DEBUG - MetricsWebSocketHandler - Metrics for test xxx have zero requests
```
→ Tasks aren't being executed

### Step 9: Test Sample API Directly

Verify the sample API is responding:
```bash
curl -w "\nTime: %{time_total}s\n" http://localhost:8081/api/products
```

**Expected**: JSON response with 10 products, ~10ms response time

### Step 10: Check Network Tab

In browser Developer Tools:
1. Go to **Network** tab
2. Filter by: **WS** (WebSocket)
3. Click on the WebSocket connection
4. Go to **Messages** tab
5. You should see messages flowing every 500ms

## Common Issues and Solutions

### Issue 1: "Charts not initialized"
**Solution**: Ensure Chart.js loads before websocket.js
- Check script order in index.html
- Verify no 404 for chart.js

### Issue 2: "Cannot read property 'p50' of undefined"
**Solution**: latencyPercentiles is null initially (no requests yet)
- Wait a few seconds after test starts
- Check if tasks are actually executing

### Issue 3: Active Tests shows "No active tests"
**Solution**: Backend returns Map<String, String>, frontend updated to handle it
- Verify fix is in place
- Check browser console for errors in loadActiveTests()

### Issue 4: WebSocket shows "Disconnected"
**Solution**: Backend WebSocket broker not started
- Check backend logs for "BrokerAvailabilityEvent"
- Verify Spring WebSocket is configured

## What to Share

If issue persists, share these from browser console:

1. **Initial connection logs** (first 10 lines after page load)
2. **Test start response** (the JSON from "Test started:")
3. **First metrics update** (the full JSON object)
4. **Any red errors** (JavaScript exceptions)

Also share from backend terminal:
1. WebSocket initialization logs
2. Test start logs
3. First few "Broadcasted metrics" logs

## Quick Checklist

- [ ] Sample API running on port 8081
- [ ] VajraEdge running on port 8080
- [ ] Browser shows "Connected" (green badge)
- [ ] No 404 errors in console
- [ ] No JavaScript errors (red text)
- [ ] Test starts without error
- [ ] Sample API responds to curl
- [ ] Backend shows "Broadcasted metrics" logs
- [ ] Console shows "Received metrics update" logs
- [ ] Console shows "Updating metrics display" logs

---

**Next Action**: Follow Step 1-5 and paste the console output here. I'll help interpret the logs and identify the exact issue.
