# Dashboard Fixes - October 26, 2025

## Issues Identified

### 1. jQuery 404 Error
**Problem**: jQuery was returning 404 despite previous webjar path fixes
**Root Cause**: jQuery uses Maven webjar format (`org.webjars:jquery`) not NPM format, so it doesn't have `/dist/` subdirectory
**Fix**: Removed `/dist/` from jQuery path
- Before: `/webjars/jquery/3.7.1/dist/jquery.min.js`
- After: `/webjars/jquery/3.7.1/jquery.min.js`

### 2. Active Tests Display Error
**Problem**: Browser console showed `TypeError: Cannot read properties of undefined (reading 'taskType')`
**Root Cause**: Backend returns `Map<String, String>` (testId → status) but frontend expected `Map<String, TestObject>` with config details
**Fix**: Updated `loadActiveTests()` to correctly handle simplified response structure
- Backend: `{ activeTests: { "uuid": "RUNNING" }, count: 1 }`
- Frontend now accesses status string directly instead of `test.config.taskType`

### 3. Charts Not Populating
**Problem**: TPS over time and Latency percentiles charts remained empty
**Root Cause**: Charts might not be initialized when first metrics arrive
**Fix**: 
- Added fallback initialization in `updateCharts()`
- Added console logging to track metrics reception
- Added warning when latency percentiles are missing

## Webjar Path Reference

For future reference, here's the correct path structure for each library:

| Library | Type | Path Format | Actual Path |
|---------|------|-------------|-------------|
| jQuery | Maven webjar | `/webjars/{name}/{version}/{file}` | `/webjars/jquery/3.7.1/jquery.min.js` |
| STOMP | Maven webjar | `/webjars/{name}/{version}/{file}` | `/webjars/stomp-websocket/2.3.4/stomp.min.js` |
| Bootstrap | NPM webjar | `/webjars/{name}/{version}/dist/{file}` | `/webjars/bootstrap/5.3.3/dist/css/bootstrap.min.css` |
| Chart.js | NPM webjar | `/webjars/{name}/{version}/dist/{file}` | `/webjars/chart.js/4.4.7/dist/chart.umd.js` |
| SockJS | NPM webjar | `/webjars/{name}/{version}/dist/{file}` | `/webjars/sockjs-client/1.6.1/dist/sockjs.min.js` |

**Rule of Thumb**: 
- `org.webjars` (Maven) = NO `/dist/`
- `org.webjars.npm` (NPM) = YES `/dist/`

## Verification Steps

### 1. Restart VajraEdge
```bash
# Stop current instance (Ctrl+C)
./gradlew clean bootRun
```

### 2. Verify in Browser Console
Open http://localhost:8080 and check browser console (F12):

**Expected (Good)**:
```
Connected: CONNECTED
Test started: {testId: '...', status: 'RUNNING', message: 'Test started successfully'}
Subscribed to metrics for test: ...
Received metrics update: {totalRequests: 100, currentTps: 50, ...}
```

**Should NOT see**:
- ❌ `404 (Not Found)` for any webjar resources
- ❌ `TypeError: Cannot read properties of undefined`
- ❌ `ReferenceError: Chart is not defined`
- ❌ `ReferenceError: SockJS is not defined`

### 3. Verify Charts Update
After starting a test:
1. **Connection Status**: Should show green "Connected" badge
2. **Active Tests Panel**: Should list running test with status
3. **TPS Over Time Chart**: Should show increasing line graph
4. **Latency Percentiles Chart**: Should show P50, P95, P99 lines
5. **Metrics Panel**: All counters should update in real-time

### 4. Console Logging
You should see periodic logs every 500ms:
```javascript
Received metrics update: {
  totalRequests: 1234,
  successfulRequests: 1234,
  currentTps: 100.5,
  avgLatencyMs: 12.34,
  activeTasks: 50,
  successRate: 100.0,
  latencyPercentiles: {
    p50: 10,
    p75: 15,
    p90: 20,
    p95: 25,
    p99: 30,
    'p99.9': 35
  }
}
```

## Testing Checklist

- [ ] No 404 errors in browser console
- [ ] jQuery loads successfully
- [ ] Bootstrap CSS/JS loads successfully
- [ ] Chart.js loads successfully
- [ ] SockJS loads successfully
- [ ] WebSocket connects (green "Connected" badge)
- [ ] Test starts without errors
- [ ] Active tests panel populates
- [ ] Metrics counters update in real-time
- [ ] TPS chart displays and updates
- [ ] Latency chart displays and updates
- [ ] Test can be stopped cleanly

## Files Modified

1. `src/main/resources/static/index.html`
   - Fixed jQuery path (removed `/dist/`)

2. `src/main/resources/static/js/dashboard.js`
   - Fixed `loadActiveTests()` to handle `Map<String, String>` response
   - Simplified active test display to show status only

3. `src/main/resources/static/js/charts.js`
   - Added fallback chart initialization
   - Added warning for missing latency percentiles

4. `src/main/resources/static/js/websocket.js`
   - Added console logging for received metrics

## Commits

- `9f78651`: fix: correct webjar paths for NPM-based libraries
- `7c0c702`: fix: correct jQuery path and active test display logic

## Next Steps

If issues persist:

1. **Clear Browser Cache**: Hard refresh (Cmd+Shift+R / Ctrl+Shift+R)
2. **Check Backend Logs**: Verify metrics are being broadcast
3. **Verify Sample API**: Ensure it's running on port 8081
4. **Test WebSocket**: Check `/topic/metrics/{testId}` subscription
5. **Validate Metrics Format**: Backend should send proper JSON structure

## Future Improvements

1. **Backend Enhancement**: Return full test config in `GET /api/tests` endpoint
   - Current: `Map<String, String>` (testId → status)
   - Proposed: `Map<String, TestInfo>` with config, metrics, status

2. **Error Handling**: Add user-friendly error messages for:
   - WebSocket connection failures
   - Chart initialization failures
   - Missing webjar resources

3. **Health Check**: Add frontend health check endpoint
   - Verify all webjars are accessible
   - Verify WebSocket endpoint is available

---

**Status**: ✅ All fixes implemented and pushed to `main`  
**Build**: SUCCESS (48s)  
**Tests**: All passing (15 specs)  
**Ready for Testing**: Yes
