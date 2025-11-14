/**
 * VajraEdge Unified Dashboard
 * 
 * Single source of truth for all test management - both single-node and distributed.
 * Eliminates duplicate code and provides consistent UX across test types.
 */

// ========================================
// GLOBAL STATE - SINGLE SOURCE OF TRUTH
// ========================================

const appState = {
    currentMode: 'SINGLE_NODE', // What user is configuring
    activeTests: new Map(),      // RUNNING/RAMPING tests: Map<testId, Test>
    completedTests: new Map(),   // COMPLETED tests (history): Map<testId, Test>
    selectedTest: null,          // Currently viewing testId
    pollingInterval: null,       // Polling timer
    websocketConnection: null    // WebSocket for single-node metrics
};

// ========================================
// METRICS HISTORY COLLECTOR
// ========================================

/**
 * Collects time-series metrics data for charts
 */
class MetricsHistoryCollector {
    constructor(maxDataPoints = 100) {
        this.maxDataPoints = maxDataPoints;
        this.testHistory = new Map(); // testId -> dataPoints[]
    }
    
    recordMetrics(testId, metrics) {
        if (!this.testHistory.has(testId)) {
            this.testHistory.set(testId, []);
            console.log(`Started metrics history collection for test ${testId}`);
        }
        
        const history = this.testHistory.get(testId);
        const dataPoint = {
            timestamp: Date.now(),
            tps: metrics.currentTps || metrics.totalTps || 0,
            totalRequests: metrics.totalRequests || 0,
            latency: {
                p50: metrics.latencyPercentiles?.p50 || metrics.latencyPercentiles?.p50Ms || 0,
                p95: metrics.latencyPercentiles?.p95 || metrics.latencyPercentiles?.p95Ms || 0,
                p99: metrics.latencyPercentiles?.p99 || metrics.latencyPercentiles?.p99Ms || 0
            }
        };
        
        history.push(dataPoint);
        console.log(`Recorded metrics for test ${testId}: TPS=${dataPoint.tps.toFixed(1)}, total points=${history.length}`);
        
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
    
    hasHistory(testId) {
        return this.testHistory.has(testId) && this.testHistory.get(testId).length > 0;
    }
}

// Global instance
const metricsHistoryCollector = new MetricsHistoryCollector();

// ========================================
// UNIFIED TEST API
// ========================================

class TestAPI {
    /**
     * Fetch all active tests (both single-node and distributed)
     */
    async getAllTests() {
        try {
            const [singleNodeRes, distributedRes] = await Promise.all([
                fetch('/api/tests').catch(() => ({ ok: false })),
                fetch('/api/tests/distributed').catch(() => ({ ok: false }))
            ]);
            
            const tests = [];
            
            // Parse single-node tests
            if (singleNodeRes.ok) {
                const data = await singleNodeRes.json();
                Object.entries(data.activeTests || {}).forEach(([testId, status]) => {
                    tests.push({
                        testId,
                        type: 'SINGLE_NODE',
                        status: status,
                        taskType: 'MIXED', // Single-node doesn't specify task type
                        metrics: null // Will be updated via WebSocket
                    });
                });
            }
            
            // Parse distributed tests
            if (distributedRes.ok) {
                const data = await distributedRes.json();
                Object.entries(data.activeTests || {}).forEach(([testId, testData]) => {
                    const testInfo = testData.testInfo || {};
                    tests.push({
                        testId,
                        type: 'DISTRIBUTED',
                        status: testInfo.status || 'RUNNING',
                        taskType: testInfo.taskType || 'UNKNOWN',
                        startedAt: testInfo.startedAt,
                        workerCount: testInfo.workerCount,
                        metrics: testData.metrics,
                        workerMetrics: testData.workerMetrics
                    });
                });
            }
            
            return tests;
        } catch (error) {
            console.error('Error fetching tests:', error);
            return [];
        }
    }
    
    /**
     * Start a test (single-node or distributed)
     */
    async startTest(config) {
        const endpoint = config.type === 'DISTRIBUTED' 
            ? '/api/tests/distributed' 
            : '/api/tests';
        
        console.log('Starting test at endpoint:', endpoint);
        console.log('Request config:', JSON.stringify(config, null, 2));
            
        const response = await fetch(endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(config)
        });
        
        console.log('Response status:', response.status);
        console.log('Response OK:', response.ok);
        
        if (!response.ok) {
            const errorText = await response.text();
            console.error('Error response:', errorText);
            throw new Error(`Failed to start test: ${response.statusText} - ${errorText}`);
        }
        
        const result = await response.json();
        console.log('Response data:', result);
        return result;
    }
    
    /**
     * Stop a test
     */
    async stopTest(testId, type) {
        const endpoint = type === 'DISTRIBUTED'
            ? `/api/tests/distributed/${testId}/stop`
            : `/api/tests/${testId}`;
            
        const method = type === 'DISTRIBUTED' ? 'POST' : 'DELETE';
        
        const response = await fetch(endpoint, { method });
        
        if (!response.ok && response.status !== 404) {
            throw new Error(`Failed to stop test: ${response.statusText}`);
        }
        
        return response.ok;
    }
    
    /**
     * Get worker status
     */
    async getWorkers() {
        const response = await fetch('/api/workers');
        if (!response.ok) {
            throw new Error('Failed to fetch workers');
        }
        return await response.json();
    }
}

// ========================================
// UNIFIED TEST CONTROLLER
// ========================================

class TestController {
    constructor() {
        this.api = new TestAPI();
        this.websocket = null;
        this.currentSubscription = null;
        this.metricsPollingInterval = null;
        this.lastRenderTime = 0;
        this.renderThrottleMs = 1000; // Only update UI once per second
        this.init();
    }
    
    init() {
        this.setupEventListeners();
        this.startPolling();
        this.initializeWebSocket();
    }
    
    setupEventListeners() {
        // Load plugins first
        this.loadTaskPlugins();
        
        // Setup form handlers
        this.setupFormHandlers();
        
        // Setup task type switcher for distributed form
        const distTaskType = document.getElementById('distTaskType');
        if (distTaskType) {
            distTaskType.addEventListener('change', (e) => {
                const taskType = e.target.value;
                const httpParams = document.getElementById('httpParams');
                const sleepParams = document.getElementById('sleepParams');
                if (httpParams && sleepParams) {
                    httpParams.classList.toggle('d-none', taskType !== 'HTTP');
                    sleepParams.classList.toggle('d-none', taskType !== 'SLEEP');
                }
            });
        }
        
        // Stop buttons (both single-node and distributed)
        const stopBtn = document.getElementById('stopBtn');
        if (stopBtn) {
            stopBtn.addEventListener('click', () => this.handleStopTest());
        }
        const stopDistBtn = document.getElementById('stopDistBtn');
        if (stopDistBtn) {
            stopDistBtn.addEventListener('click', () => this.handleStopTest());
        }
        
        // Refresh workers button
        const refreshWorkersBtn = document.getElementById('refreshWorkersBtn');
        if (refreshWorkersBtn) {
            refreshWorkersBtn.addEventListener('click', () => this.loadWorkers());
        }
        
        // Ramp strategy switcher
        const rampStrategy = document.getElementById('rampStrategy');
        if (rampStrategy) {
            rampStrategy.addEventListener('change', (e) => {
                const stepFields = document.getElementById('stepStrategyFields');
                const linearFields = document.getElementById('linearStrategyFields');
                if (stepFields && linearFields) {
                    if (e.target.value === 'STEP') {
                        stepFields.classList.remove('d-none');
                        linearFields.classList.add('d-none');
                    } else {
                        stepFields.classList.add('d-none');
                        linearFields.classList.remove('d-none');
                    }
                }
            });
        }
        
        // Test mode switcher
        const testMode = document.getElementById('testMode');
        if (testMode) {
            testMode.addEventListener('change', (e) => {
                const tpsLimitField = document.getElementById('tpsLimitField');
                if (tpsLimitField) {
                    tpsLimitField.classList.toggle('d-none', e.target.value !== 'RATE_LIMITED');
                }
            });
        }
    }
    
    setupFormHandlers() {
        // Single-node form
        const singleNodeForm = document.getElementById('testConfigForm');
        if (singleNodeForm) {
            singleNodeForm.addEventListener('submit', (e) => {
                e.preventDefault();
                this.handleStartTest(e, 'SINGLE_NODE');
            });
        }
        
        // Distributed form
        const distributedForm = document.getElementById('distributedTestForm');
        if (distributedForm) {
            distributedForm.addEventListener('submit', (e) => {
                e.preventDefault();
                this.handleStartTest(e, 'DISTRIBUTED');
            });
        }
    }
    
    async loadTaskPlugins() {
        try {
            const response = await fetch('/plugins');
            if (!response.ok) {
                console.log('Plugins endpoint not available, using defaults');
                this.loadDefaultTaskTypes();
                return;
            }
            
            const plugins = await response.json();
            const taskTypeSelect = document.getElementById('taskType');
            
            if (taskTypeSelect && plugins.length > 0) {
                taskTypeSelect.innerHTML = '';
                plugins.forEach(plugin => {
                    const option = document.createElement('option');
                    option.value = plugin.taskType;
                    option.textContent = `${plugin.taskType} - ${plugin.description}`;
                    taskTypeSelect.appendChild(option);
                });
                
                // Trigger change to load first plugin's parameters
                taskTypeSelect.dispatchEvent(new Event('change'));
            } else {
                this.loadDefaultTaskTypes();
            }
        } catch (error) {
            console.error('Error loading plugins:', error);
            this.loadDefaultTaskTypes();
        }
    }
    
    loadDefaultTaskTypes() {
        const taskTypeSelect = document.getElementById('taskType');
        if (taskTypeSelect) {
            taskTypeSelect.innerHTML = `
                <option value="HTTP">HTTP - HTTP request task</option>
                <option value="SLEEP">SLEEP - Simple sleep task for testing</option>
            `;
        }
    }
    
    toggleConfigForm() {
        const singleNodeConfig = document.getElementById('singleNodeConfig');
        const distributedConfig = document.getElementById('distributedConfig');
        
        if (appState.currentMode === 'SINGLE_NODE') {
            singleNodeConfig.classList.remove('d-none');
            distributedConfig.classList.add('d-none');
        } else {
            singleNodeConfig.classList.add('d-none');
            distributedConfig.classList.remove('d-none');
        }
    }
    
    async handleStartTest(e, type) {
        e.preventDefault();
        
        console.log('handleStartTest called with type:', type);
        
        const config = this.buildTestConfig(type);
        console.log('Config built, calling API...');
        
        try {
            const result = await this.api.startTest(config);
            console.log('API call completed, result:', result);
            
            if (result.success || result.testId) {
                const testId = result.testId;
                appState.selectedTest = testId;
                
                console.log('Test started successfully, testId:', testId);
                
                // Refresh immediately to show new test
                await this.refreshAllTests();
                
                // Select the new test
                this.selectTest(testId);
                
                console.log(`${type} test started:`, testId);
            } else {
                console.error('Test start failed, result:', result);
                alert('Failed to start test: ' + (result.message || 'Unknown error'));
            }
        } catch (error) {
            console.error('Error starting test:', error);
            alert('Error starting test: ' + error.message);
        }
    }
    
    buildTestConfig(type) {
        console.log('Building config for type:', type);
        
        if (type === 'SINGLE_NODE') {
            const config = {
                type: 'SINGLE_NODE',
                testMode: document.getElementById('testMode')?.value || 'CONCURRENCY_BASED',
                startingConcurrency: parseInt(document.getElementById('startingConcurrency')?.value || '10'),
                maxConcurrency: parseInt(document.getElementById('maxConcurrency')?.value || '100'),
                rampStrategy: document.getElementById('rampStrategy')?.value || 'LINEAR',
                rampStep: parseInt(document.getElementById('rampStep')?.value || '10'),
                rampInterval: parseInt(document.getElementById('rampInterval')?.value || '30'),
                rampDuration: parseInt(document.getElementById('rampDuration')?.value || '60'),
                sustainDuration: parseInt(document.getElementById('sustainDuration')?.value || '0'),
                testDurationSeconds: parseInt(document.getElementById('testDuration')?.value || '300'),
                targetTps: parseInt(document.getElementById('targetTpsLimit')?.value || '0'),
                taskType: document.getElementById('taskType')?.value || 'HTTP',
                taskParameter: this.buildTaskParameters(document.getElementById('taskType')?.value || 'HTTP')
            };
            console.log('Built single-node config:', JSON.stringify(config, null, 2));
            console.log('Form values - rampDuration:', document.getElementById('rampDuration')?.value,
                       'sustainDuration:', document.getElementById('sustainDuration')?.value,
                       'testDuration:', document.getElementById('testDuration')?.value);
            return config;
        } else {
            const config = {
                type: 'DISTRIBUTED',
                taskType: document.getElementById('distTaskType')?.value || 'HTTP',
                targetTps: parseInt(document.getElementById('targetTps')?.value || '1000'),
                durationSeconds: parseInt(document.getElementById('distDuration')?.value || '60'),
                rampUpSeconds: parseInt(document.getElementById('distRampUp')?.value || '10'),
                maxConcurrency: parseInt(document.getElementById('distMaxConcurrency')?.value || '1000'),
                minWorkers: parseInt(document.getElementById('minWorkers')?.value || '1'),
                taskParameter: this.buildTaskParameters(document.getElementById('distTaskType')?.value || 'HTTP', true)
            };
            console.log('Built distributed config:', config);
            return config;
        }
    }
    
    buildTaskParameters(taskType, isDistributed = false) {
        const params = {};
        
        if (taskType === 'HTTP') {
            // Use different field IDs for single-node vs distributed
            const urlId = isDistributed ? 'httpUrl' : 'httpUrlSingle';
            const methodId = isDistributed ? 'httpMethod' : 'httpMethodSingle';
            const timeoutId = isDistributed ? 'httpTimeout' : 'httpTimeoutSingle';
            
            const urlElement = document.getElementById(urlId);
            const methodElement = document.getElementById(methodId);
            const timeoutElement = document.getElementById(timeoutId);
            
            params.url = urlElement?.value?.trim() || 'http://localhost:8080/actuator/health';
            params.method = methodElement?.value?.trim() || 'GET';
            
            const timeoutValue = timeoutElement?.value?.trim() || '30';
            params.timeoutSeconds = parseInt(timeoutValue, 10);
            
            // Validate parsed values
            if (isNaN(params.timeoutSeconds)) {
                console.warn('Invalid timeout value:', timeoutValue, '- using default 30');
                params.timeoutSeconds = 30;
            }
            
            console.log('HTTP params debug:', {
                urlId, urlElement: !!urlElement, url: params.url,
                methodId, methodElement: !!methodElement, method: params.method,
                timeoutId, timeoutElement: !!timeoutElement, timeout: params.timeoutSeconds
            });
            
            const headersId = isDistributed ? 'httpHeaders' : 'httpHeadersSingle';
            const headers = document.getElementById(headersId)?.value?.trim();
            if (headers) {
                try {
                    params.headers = JSON.parse(headers);
                } catch (e) {
                    console.warn('Invalid JSON in headers, ignoring');
                }
            }
        } else if (taskType === 'SLEEP') {
            const sleepId = isDistributed ? 'sleepDuration' : 'sleepDurationSingle';
            const sleepValue = document.getElementById(sleepId)?.value?.trim() || '1000';
            params.sleepDurationMs = parseInt(sleepValue, 10);
            if (isNaN(params.sleepDurationMs)) {
                console.warn('Invalid sleep duration:', sleepValue, '- using default 1000');
                params.sleepDurationMs = 1000;
            }
        }
        
        console.log('Built task parameters for', taskType, ':', params);
        return params;
    }
    
    async handleStopTest() {
        if (!appState.selectedTest) {
            alert('No test selected');
            return;
        }
        
        const test = appState.activeTests.get(appState.selectedTest);
        if (!test) return;
        
        try {
            await this.api.stopTest(test.testId, test.type);
            console.log('Test stopped:', test.testId);
            
            // Refresh to remove from list
            await this.refreshAllTests();
            
            // Clear selection
            appState.selectedTest = null;
            this.hideMetricsPanel();
        } catch (error) {
            console.error('Error stopping test:', error);
            alert('Error stopping test: ' + error.message);
        }
    }
    
    async refreshAllTests() {
        const tests = await this.api.getAllTests();
        
        // Track which test IDs we got from the API
        const apiTestIds = new Set(tests.map(t => t.testId));
        
        // Check if any previously active tests are missing from API response
        // They might have completed and been removed from backend
        const previouslyActiveIds = Array.from(appState.activeTests.keys());
        const missingTests = previouslyActiveIds.filter(id => !apiTestIds.has(id));
        
        // Move missing tests to completed (they finished)
        missingTests.forEach(testId => {
            const test = appState.activeTests.get(testId);
            if (test && !appState.completedTests.has(testId)) {
                test.status = 'COMPLETED';
                test.completedAt = Date.now();
                appState.completedTests.set(testId, test);
                console.log(`Test ${testId} completed and moved to history`);
            }
        });
        
        // Separate active and completed tests from API response
        const newActiveTests = new Map();
        
        tests.forEach(test => {
            const status = test.status?.toUpperCase() || 'UNKNOWN';
            
            if (status === 'COMPLETED' || status === 'FAILED' || status === 'STOPPED') {
                // Add to completed tests (if not already there)
                if (!appState.completedTests.has(test.testId)) {
                    test.completedAt = Date.now();
                    appState.completedTests.set(test.testId, test);
                    console.log(`Test ${test.testId} completed (from API)`);
                }
            } else {
                // RUNNING, RAMPING, SUSTAINING, etc.
                // IMPORTANT: Preserve existing metrics for single-node tests (updated via WebSocket)
                const existingTest = appState.activeTests.get(test.testId);
                if (existingTest && existingTest.metrics) {
                    test.metrics = existingTest.metrics;
                    console.log(`Preserved metrics for test ${test.testId}`);
                }
                newActiveTests.set(test.testId, test);
            }
        });
        
        // Update active tests
        appState.activeTests = newActiveTests;
        
        // Render updated list
        this.renderTestList();
        
        // Update selected test metrics ONLY if it's an active test
        // Completed tests don't need refreshing
        if (appState.selectedTest && appState.activeTests.has(appState.selectedTest)) {
            this.updateMetrics(appState.selectedTest);
        }
    }
    
    renderTestList() {
        const container = document.getElementById('activeTestsList');
        container.innerHTML = '';
        
        // Check if we have any tests at all
        if (appState.activeTests.size === 0 && appState.completedTests.size === 0) {
            container.innerHTML = '<p class="text-muted">No tests yet</p>';
            return;
        }
        
        // Section 1: Active Tests
        if (appState.activeTests.size > 0) {
            const activeHeader = document.createElement('h6');
            activeHeader.className = 'text-muted mt-2 mb-2';
            activeHeader.innerHTML = '<i class="bi bi-play-circle"></i> Active Tests';
            container.appendChild(activeHeader);
            
            appState.activeTests.forEach((test, testId) => {
                const item = this.createTestListItem(test, true);
                container.appendChild(item);
            });
        }
        
        // Section 2: Completed Tests (last 10)
        if (appState.completedTests.size > 0) {
            const completedHeader = document.createElement('h6');
            completedHeader.className = 'text-muted mt-3 mb-2';
            completedHeader.innerHTML = '<i class="bi bi-check-circle"></i> Completed Tests';
            container.appendChild(completedHeader);
            
            // Get last 10 completed tests, sorted by completion time
            const recentCompleted = Array.from(appState.completedTests.values())
                .sort((a, b) => (b.completedAt || 0) - (a.completedAt || 0))
                .slice(0, 10);
            
            recentCompleted.forEach(test => {
                const item = this.createTestListItem(test, false);
                container.appendChild(item);
            });
        }
    }
    
    createTestListItem(test, isActive = true) {
        const item = document.createElement('div');
        const isSelected = test.testId === appState.selectedTest;
        
        // Visual distinction: active tests have green border, completed have gray
        const borderClass = isActive ? 'border-start border-success border-3' : 'border-start border-secondary border-2';
        const statusIcon = isActive ? '🟢' : '✓';
        const statusClass = isActive ? 'status-running' : 'text-muted';
        
        item.className = `list-group-item list-group-item-action ${borderClass}` + 
            (isSelected ? ' active' : '');
        
        const badge = test.type === 'DISTRIBUTED' ? '🌐' : '💻';
        const metrics = test.metrics || {};
        const tps = metrics.totalTps?.toFixed(0) || metrics.currentTps?.toFixed(0) || '0';
        const requests = metrics.totalRequests || 0;
        
        // Format timestamp for completed tests
        const timeInfo = isActive ? '' : `<br><small class="text-muted">${this.formatTimestamp(test.completedAt)}</small>`;
        
        item.innerHTML = `
            <div class="d-flex w-100 justify-content-between">
                <h6 class="mb-1">${badge} ${test.testId.substring(0, 12)}</h6>
                <small class="${statusClass}">${statusIcon}</small>
            </div>
            <small class="text-muted">
                ${test.type === 'DISTRIBUTED' ? 'Distributed' : 'Single-Node'} | 
                ${test.taskType} | 
                ${tps} TPS | 
                ${this.formatNumber(requests)} reqs
                ${timeInfo}
            </small>
        `;
        
        item.addEventListener('click', () => this.selectTest(test.testId));
        
        return item;
    }
    
    formatTimestamp(timestamp) {
        if (!timestamp) return '';
        const date = new Date(timestamp);
        return date.toLocaleTimeString();
    }
    
    selectTest(testId) {
        console.log('Selecting test:', testId);
        
        // Find test in either active or completed tests
        const test = appState.activeTests.get(testId) || appState.completedTests.get(testId);
        if (!test) {
            console.warn('Test not found:', testId);
            return;
        }
        
        console.log('Test details:', test);
        
        appState.selectedTest = testId;
        this.renderTestList(); // Re-render to update highlighting
        this.updateMetrics(testId);
        
        // Enable appropriate stop button only for active tests
        const isActive = appState.activeTests.has(testId);
        if (isActive && test.type === 'SINGLE_NODE') {
            const stopBtn = document.getElementById('stopBtn');
            if (stopBtn) stopBtn.disabled = false;
        } else if (isActive && test.type === 'DISTRIBUTED') {
            const stopDistBtn = document.getElementById('stopDistBtn');
            if (stopDistBtn) stopDistBtn.disabled = false;
        } else {
            // Completed test - disable stop buttons
            const stopBtn = document.getElementById('stopBtn');
            const stopDistBtn = document.getElementById('stopDistBtn');
            if (stopBtn) stopBtn.disabled = true;
            if (stopDistBtn) stopDistBtn.disabled = true;
        }
    }
    
    updateMetrics(testId) {
        console.log(`🔄 updateMetrics called for ${testId}`);
        
        // Find test in either active or completed tests
        const test = appState.activeTests.get(testId) || appState.completedTests.get(testId);
        if (!test) {
            console.error(`❌ Test ${testId} not found!`);
            return;
        }
        
        console.log(`📋 Found test ${testId}:`, test);
        
        // Show metrics panel
        this.showMetricsPanel();
        
        // Render unified metrics
        this.renderMetrics(test);
        
        // Only subscribe to real-time updates for active tests
        const isActive = appState.activeTests.has(testId);
        console.log(`🔍 Test active: ${isActive}, type: ${test.type}`);
        
        if (isActive && test.type === 'SINGLE_NODE') {
            if (appState.websocketConnection) {
                console.log('🔌 WebSocket connected, subscribing...');
                this.subscribeToWebSocket(testId);
            } else {
                console.warn('⚠️ WebSocket not connected, will use polling for metrics');
                // Fallback: fetch metrics via REST API
                this.startMetricsPolling(testId);
            }
        } else {
            console.log(`ℹ️ Not subscribing to WebSocket (active=${isActive}, type=${test.type})`);
        }
        // Distributed tests get metrics from polling (already in test.metrics)
        // Completed tests just show static metrics (no updates needed)
    }
    
    startMetricsPolling(testId) {
        // Clear any existing polling interval
        if (this.metricsPollingInterval) {
            clearInterval(this.metricsPollingInterval);
        }
        
        // Poll metrics every 2 seconds to prevent browser overload
        this.metricsPollingInterval = setInterval(async () => {
            // Stop polling if test is no longer selected
            if (appState.selectedTest !== testId) {
                clearInterval(this.metricsPollingInterval);
                this.metricsPollingInterval = null;
                return;
            }
            
            try {
                const response = await fetch(`/api/tests/${testId}/metrics`);
                if (response.ok) {
                    const metrics = await response.json();
                    const test = appState.activeTests.get(testId);
                    if (test) {
                        test.metrics = metrics;
                        this.renderMetrics(test);
                    }
                }
            } catch (error) {
                console.error('Error polling metrics:', error);
            }
        }, 2000);
    }
    
    renderMetrics(test) {
        console.log(`🎨 renderMetrics called for test ${test.testId}`);
        
        // Throttle rendering to prevent browser crash from too many DOM updates
        const now = Date.now();
        if (now - this.lastRenderTime < this.renderThrottleMs) {
            console.log(`⏱️ Throttled (${now - this.lastRenderTime}ms since last render)`);
            return; // Skip this render, too soon since last update
        }
        this.lastRenderTime = now;
        
        const metrics = test.metrics || {};
        console.log(`📊 Metrics object:`, metrics);
        console.log(`📊 TotalRequests: ${metrics.totalRequests}, CurrentTPS: ${metrics.currentTps || metrics.totalTps}`);
        
        const latency = metrics.latencyPercentiles || metrics.latency || {};
        
        // Record metrics history for active tests (for charts)
        const isActive = appState.activeTests.has(test.testId);
        console.log(`🔍 Test ${test.testId} active: ${isActive}, has metrics: ${metrics.totalRequests !== undefined}`);
        if (isActive && metrics.totalRequests !== undefined) {
            metricsHistoryCollector.recordMetrics(test.testId, metrics);
        }
        
        // Update test info
        document.getElementById('currentTestId').textContent = test.testId;
        document.getElementById('testStatus').textContent = test.status || 'RUNNING';
        
        const phaseEl = document.getElementById('testPhase');
        phaseEl.textContent = test.type === 'DISTRIBUTED' ? 'Distributed' : 'Single-Node';
        phaseEl.className = test.type === 'DISTRIBUTED' ? 'badge bg-info' : 'badge bg-primary';
        
        // Update metrics
        document.getElementById('totalRequests').textContent = this.formatNumber(metrics.totalRequests || 0);
        document.getElementById('currentTps').textContent = (metrics.totalTps || metrics.currentTps || 0).toFixed(1);
        document.getElementById('activeTasks').textContent = this.formatNumber(metrics.totalActiveTasks || metrics.activeTasks || 0);
        document.getElementById('pendingTasks').textContent = this.formatNumber(metrics.pendingTasks || 0);
        
        const successRate = metrics.successRate !== undefined 
            ? metrics.successRate 
            : (metrics.totalRequests > 0 ? (metrics.successfulRequests / metrics.totalRequests * 100) : 0);
        document.getElementById('successRate').textContent = successRate.toFixed(2) + '%';
        
        // Latency percentiles - ALL values are already in milliseconds
        // Despite the name, avgLatencyMs and latencyPercentiles.p50 etc. are all in the same unit (ms)
        const avgLatency = metrics.avgLatencyMs || 0;
        document.getElementById('avgLatency').textContent = this.formatLatency(avgLatency);
        document.getElementById('p50').textContent = this.formatLatency(latency.p50Ms || latency.p50 || 0);
        document.getElementById('p75').textContent = this.formatLatency(latency.p75Ms || latency.p75 || 0);
        document.getElementById('p90').textContent = this.formatLatency(latency.p90Ms || latency.p90 || 0);
        document.getElementById('p95').textContent = this.formatLatency(latency.p95Ms || latency.p95 || 0);
        document.getElementById('p99').textContent = this.formatLatency(latency.p99Ms || latency.p99 || 0);
        document.getElementById('p999').textContent = this.formatLatency(latency.p999Ms || latency['p99.9'] || 0);
        
        // Update charts with historical data if available
        if (typeof window.updateCharts === 'function' && metrics.totalRequests !== undefined) {
            const history = metricsHistoryCollector.getHistory(test.testId);
            console.log(`Updating charts for test ${test.testId} with ${history.length} historical points`);
            window.updateCharts(metrics, test.type === 'DISTRIBUTED' ? 'distributed' : 'single', history);
        } else {
            if (typeof window.updateCharts !== 'function') {
                console.warn('updateCharts function not available');
            }
            if (metrics.totalRequests === undefined) {
                console.warn('No totalRequests in metrics, skipping chart update');
            }
        }
        
        // Worker breakdown for distributed tests
        if (test.type === 'DISTRIBUTED' && test.workerMetrics) {
            this.renderWorkerBreakdown(test.workerMetrics);
        }
    }
    
    renderWorkerBreakdown(workerMetrics) {
        // TODO: Implement detailed worker breakdown panel
        console.log('Worker metrics:', workerMetrics);
    }
    
    showMetricsPanel() {
        document.getElementById('noTestMessage')?.classList.add('d-none');
        document.getElementById('metricsPanel')?.classList.remove('d-none');
        
        // Force chart resize after panel becomes visible
        // Use setTimeout to ensure DOM has updated and panel is fully visible
        setTimeout(() => {
            if (window.tpsChart) {
                console.log('Resizing TPS chart after panel shown...');
                window.tpsChart.resize();
            }
            if (window.latencyChart) {
                console.log('Resizing Latency chart after panel shown...');
                window.latencyChart.resize();
            }
        }, 100);
        
        // Initialize charts if not already initialized
        if (typeof window.initializeCharts === 'function') {
            if (!window.tpsChart || !window.latencyChart) {
                console.log('Initializing charts...');
                window.initializeCharts();
                // Resize again after initialization
                setTimeout(() => {
                    if (window.tpsChart) window.tpsChart.resize();
                    if (window.latencyChart) window.latencyChart.resize();
                }, 100);
            }
        }
    }
    
    hideMetricsPanel() {
        document.getElementById('noTestMessage')?.classList.remove('d-none');
        document.getElementById('metricsPanel')?.classList.add('d-none');
        
        // Disable both stop buttons
        const stopBtn = document.getElementById('stopBtn');
        if (stopBtn) stopBtn.disabled = true;
        const stopDistBtn = document.getElementById('stopDistBtn');
        if (stopDistBtn) stopDistBtn.disabled = true;
    }
    
    initializeWebSocket() {
        // WebSocket connection for real-time single-node metrics
        // SockJS handles protocol internally, just provide the path
        const wsUrl = '/ws';
        
        console.log('Initializing WebSocket connection to:', wsUrl);
        
        try {
            this.websocket = new SockJS(wsUrl);
            const stompClient = Stomp.over(this.websocket);
            
            // Reduce STOMP debug output
            stompClient.debug = null;
            
            stompClient.connect({}, () => {
                console.log('✓ WebSocket connected successfully');
                appState.websocketConnection = stompClient;
                this.updateConnectionStatus(true);
            }, (error) => {
                console.error('✗ WebSocket connection error:', error);
                this.updateConnectionStatus(false);
            });
        } catch (error) {
            console.error('✗ Failed to initialize WebSocket:', error);
            this.updateConnectionStatus(false);
        }
    }
    
    subscribeToWebSocket(testId) {
        if (!appState.websocketConnection) {
            console.error('❌ WebSocket not connected, cannot subscribe to test:', testId);
            return;
        }
        
        // Unsubscribe from previous test if any
        if (this.currentSubscription) {
            console.log('📴 Unsubscribing from previous test');
            this.currentSubscription.unsubscribe();
        }
        
        // Subscribe to metrics topic for this test
        const topic = `/topic/metrics/${testId}`;
        console.log('📡 Subscribing to WebSocket topic:', topic);
        
        this.currentSubscription = appState.websocketConnection.subscribe(topic, (message) => {
            console.log('📨 RAW WebSocket message received:', message.body);
            const metrics = JSON.parse(message.body);
            console.log('✅ Parsed WebSocket metrics:', JSON.stringify(metrics, null, 2));
            
            // Update test metrics in state
            const test = appState.activeTests.get(testId);
            if (test) {
                console.log(`📝 Updating metrics for test ${testId} in activeTests`);
                test.metrics = metrics;
                
                // If this is the selected test, update display
                if (appState.selectedTest === testId) {
                    console.log(`🔄 Rendering metrics for selected test ${testId}`);
                    this.renderMetrics(test);
                } else {
                    console.log(`⏭️ Test ${testId} not selected (selected=${appState.selectedTest}), skipping render`);
                }
            } else {
                console.error(`❌ Test ${testId} not found in activeTests!`);
                console.log('Active tests:', Array.from(appState.activeTests.keys()));
            }
        });
        
        console.log('✅ WebSocket subscription created for test:', testId);
    }
    
    updateConnectionStatus(connected) {
        const statusEl = document.getElementById('connection-status');
        if (statusEl) {
            statusEl.innerHTML = connected
                ? '<span class="badge bg-success">Connected</span>'
                : '<span class="badge bg-danger">Disconnected</span>';
        }
    }
    
    async loadWorkers() {
        try {
            const data = await this.api.getWorkers();
            const workerList = document.getElementById('workerList');
            
            if (!workerList) return;
            
            if (data.totalCount === 0) {
                workerList.innerHTML = '<p class="text-muted">No workers registered</p>';
                return;
            }
            
            workerList.innerHTML = `
                <div class="mb-2">
                    <strong>${data.healthyCount}</strong> / ${data.totalCount} workers healthy
                </div>
            `;
            
            (data.workers || []).forEach(worker => {
                const workerCard = document.createElement('div');
                workerCard.className = 'card mb-2';
                workerCard.innerHTML = `
                    <div class="card-body py-2">
                        <div class="d-flex justify-content-between align-items-center">
                            <div>
                                <strong>${worker.workerId}</strong>
                                <br>
                                <small class="text-muted">${worker.hostname || 'unknown'}</small>
                            </div>
                            <div class="text-end">
                                <span class="badge ${worker.healthy ? 'bg-success' : 'bg-danger'}">
                                    ${worker.healthStatus || (worker.healthy ? 'HEALTHY' : 'UNHEALTHY')}
                                </span>
                                <br>
                                <small class="text-muted">${worker.currentLoad || 0}/${worker.maxCapacity || 0}</small>
                            </div>
                        </div>
                    </div>
                `;
                workerList.appendChild(workerCard);
            });
        } catch (error) {
            console.error('Error loading workers:', error);
        }
    }
    
    startPolling() {
        // Initial load
        this.refreshAllTests();
        this.loadWorkers();
        
        // Poll every 2 seconds
        appState.pollingInterval = setInterval(() => {
            this.refreshAllTests();
        }, 2000);
    }
    
    // Formatting helpers
    formatNumber(num) {
        return new Intl.NumberFormat().format(num || 0);
    }
    
    formatLatency(ms) {
        if (ms === undefined || ms === null || ms === 0) return '-';
        return ms.toFixed(2) + 'ms';
    }
}

// ========================================
// INITIALIZATION
// ========================================

// Initialize on page load
window.addEventListener('DOMContentLoaded', () => {
    console.log('Initializing Unified Dashboard...');
    window.testController = new TestController();
});
