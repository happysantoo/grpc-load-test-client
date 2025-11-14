# Dashboard Improvements Plan

## Issues Identified (November 11, 2025)

### Critical UX Problems

1. **Metrics panel disappears after test completion**
   - Active tests panel also disappears
   - No way to view historical metrics
   - Lost context for completed tests

2. **TPS over time graph never populated**
   - Chart exists but shows no data
   - Missing time-series data collection
   - No visual performance trends

3. **Performance stats panel never populated**
   - Stats section exists but remains empty
   - Missing aggregated statistics
   - No summary metrics display

---

## Root Cause Analysis

### Issue 1: Disappearing Metrics Panel

**Current Behavior:**
```javascript
// In refreshAllTests() or similar
if (appState.activeTests.size === 0) {
    // Hides metrics panel when no active tests
    hideMetricsPanel();
}
```

**Problem:**
- Only tracks RUNNING tests
- Completed tests are removed from `activeTests` map
- Metrics panel tied to active test existence
- No historical data retention

**Required Changes:**
1. Maintain test history (completed tests)
2. Keep last selected test visible after completion
3. Add test status filter (Active/Completed/All)
4. Persist completed test metrics

---

### Issue 2: TPS Graph Not Populating

**Current Behavior:**
```javascript
// chart.js or unified-dashboard.js
function updateCharts(metrics, phase) {
    // Called but data not accumulated over time
}
```

**Problem:**
- No time-series data structure
- Metrics are point-in-time snapshots
- Chart update receives only current value, not historical
- Missing data accumulation logic

**Required Changes:**
1. Create time-series data store for each test
2. Accumulate TPS readings with timestamps
3. Pass historical data to chart
4. Implement data point limits (e.g., last 100 points)

---

### Issue 3: Performance Stats Not Populated

**Current Behavior:**
- Stats panel HTML exists
- No data binding implementation
- No calculation of summary statistics

**Problem:**
- Missing statistical calculations
- No final summary generation
- Stats never computed from metrics

**Required Changes:**
1. Calculate final statistics when test completes
2. Display peak TPS, average TPS, total duration
3. Show error rate, total requests, throughput
4. Persist stats for completed tests

---

## Implementation Plan

### Phase 1: Test History & Persistence (HIGH PRIORITY)

**Objective:** Keep completed tests visible with their metrics

#### Changes Required:

**1.1 Data Model Enhancement**
```javascript
// Add test history tracking
const appState = {
    currentMode: 'SINGLE_NODE' | 'DISTRIBUTED',
    activeTests: Map<testId, Test>,      // RUNNING/RAMPING tests
    completedTests: Map<testId, Test>,   // COMPLETED tests
    selectedTest: testId | null,
    pollingInterval: null
};

// Enhance Test object
const Test = {
    testId: string,
    type: 'SINGLE_NODE' | 'DISTRIBUTED',
    status: 'RUNNING' | 'COMPLETED' | 'FAILED',
    startedAt: timestamp,
    completedAt: timestamp | null,       // NEW
    
    metrics: {
        // Current snapshot
    },
    
    metricsHistory: [{                    // NEW: Time-series
        timestamp: number,
        tps: number,
        latency: object,
        requests: number
    }],
    
    finalStats: {                         // NEW: Summary
        peakTps: number,
        avgTps: number,
        totalRequests: number,
        duration: number,
        errorRate: number
    }
};
```

**1.2 Test List UI Updates**
```javascript
// Show both active and completed tests
renderTestList() {
    const container = document.getElementById('activeTestsList');
    container.innerHTML = '';
    
    // Section: Active Tests
    if (appState.activeTests.size > 0) {
        container.innerHTML += '<h6 class="text-muted">Active Tests</h6>';
        appState.activeTests.forEach((test, testId) => {
            container.appendChild(createTestListItem(test, true));
        });
    }
    
    // Section: Completed Tests (last 10)
    if (appState.completedTests.size > 0) {
        container.innerHTML += '<h6 class="text-muted mt-3">Completed Tests</h6>';
        const recentCompleted = Array.from(appState.completedTests.values())
            .sort((a, b) => b.completedAt - a.completedAt)
            .slice(0, 10);
        
        recentCompleted.forEach(test => {
            container.appendChild(createTestListItem(test, false));
        });
    }
}

// Visual distinction for completed tests
createTestListItem(test, isActive) {
    const item = document.createElement('div');
    const isSelected = test.testId === appState.selectedTest;
    
    const statusClass = isActive ? 'border-start border-success' : 'border-start border-secondary';
    const statusIcon = isActive ? '🟢' : '✓';
    
    item.className = `list-group-item ${statusClass} ${isSelected ? 'active' : ''}`;
    item.innerHTML = `
        <div class="d-flex justify-content-between">
            <h6>${statusIcon} ${test.testId.substring(0, 12)}</h6>
            <small>${formatTimestamp(test.completedAt || test.startedAt)}</small>
        </div>
        <small>${test.type} | ${test.taskType}</small>
    `;
    
    item.addEventListener('click', () => selectTest(testId));
    return item;
}
```

**1.3 Polling Logic Update**
```javascript
async refreshAllTests() {
    const tests = await this.api.getAllTests();
    
    // Update active tests
    appState.activeTests.clear();
    [...tests.singleNode, ...tests.distributed]
        .filter(t => t.status === 'RUNNING' || t.status === 'RAMPING')
        .forEach(test => {
            appState.activeTests.set(test.testId, test);
        });
    
    // Move completed tests to history
    [...tests.singleNode, ...tests.distributed]
        .filter(t => t.status === 'COMPLETED' || t.status === 'FAILED')
        .forEach(test => {
            if (!appState.completedTests.has(test.testId)) {
                // Calculate final stats
                test.finalStats = calculateFinalStats(test);
                test.completedAt = Date.now();
                appState.completedTests.set(test.testId, test);
            }
        });
    
    this.renderTestList();
    
    // Keep showing selected test even if completed
    if (appState.selectedTest) {
        const selectedTest = appState.activeTests.get(appState.selectedTest) 
            || appState.completedTests.get(appState.selectedTest);
        
        if (selectedTest) {
            this.updateMetrics(appState.selectedTest);
        }
    }
}
```

**Estimated Time:** 2-3 hours

---

### Phase 2: TPS Time-Series Chart (MEDIUM PRIORITY)

**Objective:** Populate TPS over time graph with real-time data

#### Changes Required:

**2.1 Data Accumulation**
```javascript
// Store time-series data per test
class MetricsHistoryCollector {
    constructor(maxDataPoints = 100) {
        this.maxDataPoints = maxDataPoints;
        this.testHistory = new Map(); // testId -> dataPoints[]
    }
    
    recordMetrics(testId, metrics) {
        if (!this.testHistory.has(testId)) {
            this.testHistory.set(testId, []);
        }
        
        const history = this.testHistory.get(testId);
        const dataPoint = {
            timestamp: Date.now(),
            tps: metrics.currentTps || metrics.totalTps || 0,
            totalRequests: metrics.totalRequests || 0,
            latency: {
                p50: metrics.latencyPercentiles?.p50 || 0,
                p95: metrics.latencyPercentiles?.p95 || 0,
                p99: metrics.latencyPercentiles?.p99 || 0
            }
        };
        
        history.push(dataPoint);
        
        // Keep only last N points
        if (history.length > this.maxDataPoints) {
            history.shift();
        }
    }
    
    getHistory(testId) {
        return this.testHistory.get(testId) || [];
    }
    
    clearHistory(testId) {
        this.testHistory.delete(testId);
    }
}

// Global instance
const metricsHistoryCollector = new MetricsHistoryCollector();
```

**2.2 Chart Update Logic**
```javascript
// Modified renderMetrics to record and display history
renderMetrics(test) {
    const metrics = test.metrics || {};
    
    // Record current metrics for time-series
    if (test.status === 'RUNNING' || test.status === 'RAMPING') {
        metricsHistoryCollector.recordMetrics(test.testId, metrics);
    }
    
    // Update point-in-time metrics display
    document.getElementById('currentTps').textContent = (metrics.currentTps || 0).toFixed(1);
    // ... other metrics
    
    // Update charts with historical data
    if (typeof window.updateCharts === 'function') {
        const history = metricsHistoryCollector.getHistory(test.testId);
        window.updateCharts(metrics, test.type, history);
    }
}
```

**2.3 Chart.js Integration**
```javascript
// In charts.js - modify updateCharts function
function updateCharts(currentMetrics, testType, metricsHistory) {
    // TPS Chart - use historical data
    if (tpsChart && metricsHistory.length > 0) {
        const labels = metricsHistory.map(dp => 
            new Date(dp.timestamp).toLocaleTimeString()
        );
        const tpsData = metricsHistory.map(dp => dp.tps);
        
        tpsChart.data.labels = labels;
        tpsChart.data.datasets[0].data = tpsData;
        tpsChart.update('none'); // No animation for performance
    }
    
    // Latency Chart - use historical data
    if (latencyChart && metricsHistory.length > 0) {
        const labels = metricsHistory.map(dp => 
            new Date(dp.timestamp).toLocaleTimeString()
        );
        
        latencyChart.data.labels = labels;
        latencyChart.data.datasets[0].data = metricsHistory.map(dp => dp.latency.p50);
        latencyChart.data.datasets[1].data = metricsHistory.map(dp => dp.latency.p95);
        latencyChart.data.datasets[2].data = metricsHistory.map(dp => dp.latency.p99);
        latencyChart.update('none');
    }
}
```

**Estimated Time:** 2 hours

---

### Phase 3: Performance Stats Panel (LOW PRIORITY)

**Objective:** Display aggregated statistics for completed tests

#### Changes Required:

**3.1 Statistics Calculation**
```javascript
function calculateFinalStats(test) {
    const history = metricsHistoryCollector.getHistory(test.testId);
    
    if (history.length === 0) {
        return {
            peakTps: 0,
            avgTps: 0,
            totalRequests: test.metrics?.totalRequests || 0,
            duration: 0,
            errorRate: 0
        };
    }
    
    const tpsValues = history.map(dp => dp.tps);
    const peakTps = Math.max(...tpsValues);
    const avgTps = tpsValues.reduce((a, b) => a + b, 0) / tpsValues.length;
    
    const firstPoint = history[0];
    const lastPoint = history[history.length - 1];
    const durationMs = lastPoint.timestamp - firstPoint.timestamp;
    const durationSec = durationMs / 1000;
    
    const finalMetrics = test.metrics || {};
    const totalRequests = finalMetrics.totalRequests || 0;
    const successfulRequests = finalMetrics.successfulRequests || 0;
    const errorRate = totalRequests > 0 
        ? ((totalRequests - successfulRequests) / totalRequests * 100).toFixed(2)
        : 0;
    
    return {
        peakTps: peakTps.toFixed(1),
        avgTps: avgTps.toFixed(1),
        totalRequests,
        duration: durationSec.toFixed(0),
        errorRate
    };
}
```

**3.2 Stats Panel Display**
```javascript
// Add to renderMetrics function
function renderMetrics(test) {
    // ... existing metrics display
    
    // Show performance stats for completed tests
    const statsPanel = document.getElementById('performanceStats');
    if (test.status === 'COMPLETED' && test.finalStats) {
        statsPanel.classList.remove('d-none');
        
        document.getElementById('peakTps').textContent = test.finalStats.peakTps;
        document.getElementById('avgTps').textContent = test.finalStats.avgTps;
        document.getElementById('testDuration').textContent = test.finalStats.duration + 's';
        document.getElementById('totalRequests').textContent = formatNumber(test.finalStats.totalRequests);
        document.getElementById('errorRate').textContent = test.finalStats.errorRate + '%';
    } else {
        statsPanel.classList.add('d-none');
    }
}
```

**3.3 HTML Update (if needed)**
```html
<!-- Performance Stats Panel (shown for completed tests) -->
<div id="performanceStats" class="card mt-3 d-none">
    <div class="card-header">
        <h5>Performance Summary</h5>
    </div>
    <div class="card-body">
        <div class="row">
            <div class="col-md-4">
                <strong>Peak TPS:</strong> <span id="peakTps">-</span>
            </div>
            <div class="col-md-4">
                <strong>Avg TPS:</strong> <span id="avgTps">-</span>
            </div>
            <div class="col-md-4">
                <strong>Duration:</strong> <span id="testDuration">-</span>
            </div>
        </div>
        <div class="row mt-2">
            <div class="col-md-6">
                <strong>Total Requests:</strong> <span id="totalRequests">-</span>
            </div>
            <div class="col-md-6">
                <strong>Error Rate:</strong> <span id="errorRate">-</span>
            </div>
        </div>
    </div>
</div>
```

**Estimated Time:** 1 hour

---

## Implementation Order

### Priority 1 (Today): Test History & Persistence
- **Why:** Most critical UX issue - losing all data after test completes
- **Impact:** High - enables all other features
- **Effort:** 2-3 hours
- **Files to modify:**
  - `unified-dashboard.js` (data model, polling, rendering)
  - Minor HTML changes if needed

### Priority 2 (Next): TPS Time-Series Chart
- **Why:** Second most visible issue - empty charts look broken
- **Impact:** High - provides visual feedback during tests
- **Effort:** 2 hours
- **Files to modify:**
  - `unified-dashboard.js` (data collection)
  - `charts.js` (chart updates)

### Priority 3 (Later): Performance Stats
- **Why:** Nice-to-have summary, less critical
- **Impact:** Medium - improves final analysis
- **Effort:** 1 hour
- **Files to modify:**
  - `unified-dashboard.js` (stats calculation)
  - `index.html` (stats panel HTML)

---

## Testing Plan

### For Each Phase:

**Phase 1 Testing:**
1. Start test, let it complete
2. Verify test moves to "Completed Tests" section
3. Click completed test → verify metrics still visible
4. Start another test → verify both active and completed visible
5. Refresh browser → verify completed tests persist in session

**Phase 2 Testing:**
1. Start test
2. Watch TPS chart populate in real-time
3. Verify chart shows last 100 data points
4. Test completes → verify chart frozen at final state
5. Start new test → verify chart resets

**Phase 3 Testing:**
1. Complete a test
2. Select completed test
3. Verify performance stats panel appears
4. Verify all stats calculated correctly
5. Compare with real-time metrics during test

---

## Success Criteria

✅ **Issue 1 Fixed:**
- Completed tests remain in UI
- Can view metrics for completed tests
- Test history persists for session
- Clear visual distinction between active/completed

✅ **Issue 2 Fixed:**
- TPS chart shows real-time data
- Chart updates smoothly during test
- Historical data visible for completed tests
- Chart performs well (no lag)

✅ **Issue 3 Fixed:**
- Stats panel appears for completed tests
- All statistics calculated and displayed
- Stats accurately reflect test performance
- Panel hidden for running tests

---

## Files to Modify

1. **vajraedge-core/src/main/resources/static/js/unified-dashboard.js**
   - Add completedTests map
   - Add MetricsHistoryCollector class
   - Modify refreshAllTests()
   - Modify renderTestList()
   - Modify renderMetrics()
   - Add calculateFinalStats()

2. **vajraedge-core/src/main/resources/static/js/charts.js**
   - Modify updateCharts() signature
   - Add historical data handling
   - Update chart data structures

3. **vajraedge-core/src/main/resources/static/index.html** (optional)
   - Add performance stats panel HTML
   - Adjust test list sections

---

## Rollback Plan

If issues arise:
1. Keep backup of working `unified-dashboard.js`
2. Phase 1 can be developed in new functions first
3. Test incrementally - commit after each phase
4. Can disable features via flag if needed

---

## Future Enhancements (Post-Implementation)

1. **Persistent Storage**
   - Save completed tests to localStorage
   - Survive browser refresh
   - Export test results to JSON/CSV

2. **Test Comparison**
   - Compare two test runs side-by-side
   - Overlay charts from different tests
   - Regression detection

3. **Advanced Filtering**
   - Filter by test type, task type, date
   - Search tests by ID
   - Sort by various metrics

4. **Export/Reporting**
   - Generate PDF reports
   - Export charts as images
   - CSV export for analysis

---

**Created:** November 11, 2025  
**Status:** Ready for Implementation  
**Next Action:** Begin Phase 1 - Test History & Persistence
