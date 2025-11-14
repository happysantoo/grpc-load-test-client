# UI Unification Plan

## Current Problems

### 1. **Duplicate Logic**
- `loadActiveTests()` for single-node tests
- `loadDistributedTests()` for distributed tests
- Two separate polling mechanisms
- Two separate metrics update functions

### 2. **Inconsistent State Management**
- `window.currentTest` for single-node
- `currentDistributedTest` for distributed
- Different WebSocket vs REST polling patterns

### 3. **Confusing UX**
- Two tabs that show different tests
- Same panels (left, middle, right) but different behavior
- Unclear which test is "active" when switching tabs

### 4. **No Single Source of Truth**
- Single-node tests tracked separately
- Distributed tests tracked separately
- Can't see all tests in one view

## Proposed Unified Architecture

### Core Principle
**One test list, one metrics panel, one status display - regardless of test type**

### UI Structure

```
┌─────────────────────────────────────────────────────────────┐
│ Navigation Bar                                              │
│ ⚡ VajraEdge  [Connection Status]                          │
└─────────────────────────────────────────────────────────────┘

┌─────────────────┬────────────────────────┬─────────────────┐
│ LEFT PANEL      │ MIDDLE PANEL           │ RIGHT PANEL     │
│ (Test Control)  │ (Metrics Display)      │ (Charts/Worker) │
├─────────────────┼────────────────────────┼─────────────────┤
│                 │                        │                 │
│ [Start Test]    │ Test: test-abc123      │ TPS Chart       │
│  ○ Single-Node  │ Status: RUNNING        │                 │
│  ○ Distributed  │ Type: Distributed      │ Latency Chart   │
│                 │                        │                 │
│ [Active Tests]  │ Total Requests: 10,000 │ Workers (if     │
│ ┌─────────────┐ │ Success Rate: 99.5%    │ distributed):   │
│ │ test-abc123 │←│ Current TPS: 850       │ • worker-001 ✓  │
│ │ Distributed │ │                        │ • worker-002 ✓  │
│ │ HTTP 850TPS │ │ P50: 45ms              │                 │
│ └─────────────┘ │ P95: 120ms             │                 │
│ ┌─────────────┐ │ P99: 250ms             │                 │
│ │ test-def456 │ │                        │                 │
│ │ Single-Node │ │                        │                 │
│ │ SLEEP 500TPS│ │                        │                 │
│ └─────────────┘ │                        │                 │
│                 │                        │                 │
│ [Stop Test]     │                        │                 │
└─────────────────┴────────────────────────┴─────────────────┘
```

### Unified Data Model

```javascript
// Single test object structure for BOTH types
const Test = {
    testId: string,
    type: 'SINGLE_NODE' | 'DISTRIBUTED',
    taskType: 'HTTP' | 'SLEEP' | 'CUSTOM',
    status: 'RUNNING' | 'RAMPING' | 'SUSTAINING' | 'STOPPING' | 'COMPLETED',
    startedAt: timestamp,
    
    // Metrics (same structure for both)
    metrics: {
        totalRequests: number,
        successfulRequests: number,
        failedRequests: number,
        currentTps: number,
        latency: {
            p50Ms: number,
            p95Ms: number,
            p99Ms: number,
            ...
        }
    },
    
    // Only for distributed
    workerCount?: number,
    workerMetrics?: Map<workerId, metrics>
}
```

### Unified JavaScript Architecture

#### State Management (SINGLE SOURCE OF TRUTH)
```javascript
// Global state
const appState = {
    currentMode: 'SINGLE_NODE' | 'DISTRIBUTED',
    activeTests: Map<testId, Test>,  // ALL tests (both types)
    selectedTest: testId | null,      // Currently viewing
    pollingInterval: null
};
```

#### Unified API Layer
```javascript
class TestAPI {
    async getAllTests() {
        // Combines /api/tests + /api/tests/distributed
        const [singleNode, distributed] = await Promise.all([
            fetch('/api/tests'),
            fetch('/api/tests/distributed')
        ]);
        
        return {
            singleNode: parseTests(singleNode, 'SINGLE_NODE'),
            distributed: parseTests(distributed, 'DISTRIBUTED')
        };
    }
    
    async startTest(config) {
        const endpoint = config.mode === 'DISTRIBUTED' 
            ? '/api/tests/distributed' 
            : '/api/tests';
        return fetch(endpoint, { method: 'POST', body: JSON.stringify(config) });
    }
    
    async stopTest(testId, type) {
        const endpoint = type === 'DISTRIBUTED'
            ? `/api/tests/distributed/${testId}/stop`
            : `/api/tests/${testId}`;
        return fetch(endpoint, { method: type === 'DISTRIBUTED' ? 'POST' : 'DELETE' });
    }
    
    async getMetrics(testId, type) {
        if (type === 'SINGLE_NODE') {
            // Use WebSocket (existing)
            return subscribeToWebSocket(testId);
        } else {
            // Use REST polling
            return fetch(`/api/tests/distributed/${testId}/metrics`);
        }
    }
}
```

#### Unified UI Controller
```javascript
class TestController {
    constructor() {
        this.api = new TestAPI();
        this.startPolling();
    }
    
    async refreshAllTests() {
        const tests = await this.api.getAllTests();
        
        // Merge and update state
        appState.activeTests.clear();
        [...tests.singleNode, ...tests.distributed].forEach(test => {
            appState.activeTests.set(test.testId, test);
        });
        
        this.renderTestList();
        
        // Update selected test if still active
        if (appState.selectedTest && appState.activeTests.has(appState.selectedTest)) {
            this.updateMetrics(appState.selectedTest);
        }
    }
    
    renderTestList() {
        const container = document.getElementById('activeTestsList');
        container.innerHTML = '';
        
        if (appState.activeTests.size === 0) {
            container.innerHTML = '<p class="text-muted">No active tests</p>';
            return;
        }
        
        appState.activeTests.forEach((test, testId) => {
            const item = this.createTestListItem(test);
            item.addEventListener('click', () => this.selectTest(testId));
            container.appendChild(item);
        });
    }
    
    createTestListItem(test) {
        const item = document.createElement('div');
        const isSelected = test.testId === appState.selectedTest;
        
        item.className = 'list-group-item list-group-item-action' + 
            (isSelected ? ' active' : '');
        
        const badge = test.type === 'DISTRIBUTED' ? '🌐' : '💻';
        const tps = test.metrics?.currentTps?.toFixed(0) || '0';
        
        item.innerHTML = `
            <div class="d-flex w-100 justify-content-between">
                <h6 class="mb-1">${badge} ${test.testId.substring(0, 12)}</h6>
                <small class="status-running">●</small>
            </div>
            <small class="text-muted">
                ${test.type} | ${test.taskType} | ${tps} TPS
            </small>
        `;
        
        return item;
    }
    
    selectTest(testId) {
        appState.selectedTest = testId;
        this.renderTestList(); // Re-render to update highlighting
        this.updateMetrics(testId);
    }
    
    async updateMetrics(testId) {
        const test = appState.activeTests.get(testId);
        if (!test) return;
        
        // Show metrics panel
        document.getElementById('noTestMessage').classList.add('d-none');
        document.getElementById('metricsPanel').classList.remove('d-none');
        
        // Unified metrics display (same for both types)
        this.renderMetrics(test);
        
        // Type-specific: Show workers for distributed
        if (test.type === 'DISTRIBUTED' && test.workerMetrics) {
            this.renderWorkerBreakdown(test.workerMetrics);
        }
    }
    
    renderMetrics(test) {
        const metrics = test.metrics || {};
        
        // Update all metric fields (same elements for both types)
        document.getElementById('currentTestId').textContent = test.testId;
        document.getElementById('testStatus').textContent = test.status;
        document.getElementById('testPhase').textContent = test.type;
        
        document.getElementById('totalRequests').textContent = formatNumber(metrics.totalRequests || 0);
        document.getElementById('currentTps').textContent = (metrics.currentTps || 0).toFixed(1);
        document.getElementById('successRate').textContent = (metrics.successRate || 0).toFixed(2) + '%';
        
        // Latencies
        const latency = metrics.latency || {};
        document.getElementById('p50Latency').textContent = formatLatency(latency.p50Ms);
        document.getElementById('p95Latency').textContent = formatLatency(latency.p95Ms);
        document.getElementById('p99Latency').textContent = formatLatency(latency.p99Ms);
    }
    
    startPolling() {
        this.refreshAllTests(); // Immediate
        
        appState.pollingInterval = setInterval(() => {
            this.refreshAllTests();
        }, 2000);
    }
}
```

### Simplified HTML Structure

**Remove tabs** - Use radio buttons instead:

```html
<div class="card">
    <div class="card-header">
        <h5>Start New Test</h5>
    </div>
    <div class="card-body">
        <!-- Test Type Selector -->
        <div class="mb-3">
            <label class="form-label">Test Type</label>
            <div class="btn-group w-100" role="group">
                <input type="radio" class="btn-check" name="testType" id="singleNode" value="SINGLE_NODE" checked>
                <label class="btn btn-outline-primary" for="singleNode">💻 Single Node</label>
                
                <input type="radio" class="btn-check" name="testType" id="distributed" value="DISTRIBUTED">
                <label class="btn btn-outline-primary" for="distributed">🌐 Distributed</label>
            </div>
        </div>
        
        <!-- Dynamic form based on selection -->
        <div id="testConfigForm">
            <!-- Single form that adapts based on radio selection -->
        </div>
    </div>
</div>

<!-- Active Tests List (UNIFIED) -->
<div class="card mt-3">
    <div class="card-header">
        <h5>Active Tests</h5>
    </div>
    <div class="card-body">
        <div id="activeTestsList">
            <!-- Both single-node AND distributed tests -->
        </div>
    </div>
</div>
```

## Implementation Plan

### Phase 1: Refactor JavaScript (2-3 hours)
1. Create unified `TestAPI` class
2. Create unified `TestController` class
3. Consolidate state management
4. Remove duplicate polling functions
5. Unify metrics display

### Phase 2: Simplify HTML (1 hour)
1. Remove tab structure
2. Use radio buttons for test type
3. Single dynamic form
4. Unified test list panel

### Phase 3: Testing (30 min)
1. Start single-node test → verify shows in list
2. Start distributed test → verify shows in list
3. Click between tests → verify highlighting works
4. Metrics update correctly for both types

### Phase 4: Cleanup (30 min)
1. Remove old functions
2. Update documentation
3. Commit changes

## Benefits

### 1. **Predictability**
- One way to view tests (no tabs to switch)
- One test list (all tests visible)
- Click test → see metrics (always)

### 2. **Single Source of Truth**
- `appState.activeTests` has ALL tests
- `appState.selectedTest` shows which one you're viewing
- No confusion about state

### 3. **Maintainability**
- One polling mechanism
- One metrics display function
- One test selection handler
- Easier to debug

### 4. **User Experience**
- See all tests at once
- Compare single-node vs distributed
- Clear visual distinction (💻 vs 🌐)
- Highlight shows selected test

### 5. **Extensibility**
- Easy to add new test types (just add to enum)
- Worker breakdown is additive (not separate flow)
- Charts work for both types

## Migration Strategy

### Backward Compatibility
- Keep both API endpoints (`/api/tests` and `/api/tests/distributed`)
- Just unify how they're consumed on frontend
- No backend changes needed initially

### Rollout
1. Implement unified controller in new file (`unified-dashboard.js`)
2. Test thoroughly
3. Replace old `dashboard.js` when ready
4. Keep old version as backup

## Success Criteria

✅ Single test list shows both types  
✅ Clicking any test highlights it  
✅ Metrics update for selected test  
✅ No duplicate code  
✅ No confusing tabs  
✅ Clear visual distinction between types  
✅ Workers shown only for distributed tests  
✅ All existing features work  

---

**Conclusion**: By treating tests as a **unified entity** with a `type` field instead of completely separate systems, we achieve simplicity, predictability, and maintainability while improving UX.
