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
    activeTests: new Map(),      // ALL tests (both types): Map<testId, Test>
    selectedTest: null,          // Currently viewing testId
    pollingInterval: null,       // Polling timer
    websocketConnection: null    // WebSocket for single-node metrics
};

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
                sustainDuration: parseInt(document.getElementById('sustainDuration')?.value || '60'),
                targetTps: parseInt(document.getElementById('targetTpsLimit')?.value || '0'),
                taskType: document.getElementById('taskType')?.value || 'HTTP',
                taskParameters: this.buildTaskParameters(document.getElementById('taskType')?.value || 'HTTP')
            };
            console.log('Built single-node config:', config);
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
                taskParameters: this.buildTaskParameters(document.getElementById('distTaskType')?.value || 'HTTP', true)
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
            
            params.url = document.getElementById(urlId)?.value || 'http://localhost:8080/actuator/health';
            params.method = document.getElementById(methodId)?.value || 'GET';
            params.timeoutSeconds = parseInt(document.getElementById(timeoutId)?.value || '30');
            
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
            params.sleepDurationMs = parseInt(document.getElementById(sleepId)?.value || '1000');
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
        
        // Update state
        const newTestsMap = new Map();
        tests.forEach(test => {
            newTestsMap.set(test.testId, test);
        });
        
        appState.activeTests = newTestsMap;
        
        // Render
        this.renderTestList();
        
        // Update selected test metrics if still active
        if (appState.selectedTest && appState.activeTests.has(appState.selectedTest)) {
            this.updateMetrics(appState.selectedTest);
        } else if (appState.selectedTest) {
            // Selected test no longer active
            appState.selectedTest = null;
            this.hideMetricsPanel();
        }
    }
    
    renderTestList() {
        const container = document.getElementById('activeTestsList');
        
        if (appState.activeTests.size === 0) {
            container.innerHTML = '<p class="text-muted">No active tests</p>';
            return;
        }
        
        container.innerHTML = '';
        
        appState.activeTests.forEach((test, testId) => {
            const item = this.createTestListItem(test);
            container.appendChild(item);
        });
    }
    
    createTestListItem(test) {
        const item = document.createElement('div');
        const isSelected = test.testId === appState.selectedTest;
        
        item.className = 'list-group-item list-group-item-action' + 
            (isSelected ? ' active' : '');
        
        const badge = test.type === 'DISTRIBUTED' ? '🌐' : '💻';
        const metrics = test.metrics || {};
        const tps = metrics.totalTps?.toFixed(0) || metrics.currentTps?.toFixed(0) || '0';
        const requests = metrics.totalRequests || 0;
        
        item.innerHTML = `
            <div class="d-flex w-100 justify-content-between">
                <h6 class="mb-1">${badge} ${test.testId.substring(0, 12)}</h6>
                <small class="status-running">●</small>
            </div>
            <small class="text-muted">
                ${test.type === 'DISTRIBUTED' ? 'Distributed' : 'Single-Node'} | 
                ${test.taskType} | 
                ${tps} TPS | 
                ${this.formatNumber(requests)} reqs
            </small>
        `;
        
        item.addEventListener('click', () => this.selectTest(test.testId));
        
        return item;
    }
    
    selectTest(testId) {
        const test = appState.activeTests.get(testId);
        if (!test) return;
        
        appState.selectedTest = testId;
        this.renderTestList(); // Re-render to update highlighting
        this.updateMetrics(testId);
        
        // Enable appropriate stop button based on test type
        if (test.type === 'SINGLE_NODE') {
            const stopBtn = document.getElementById('stopBtn');
            if (stopBtn) stopBtn.disabled = false;
        } else {
            const stopDistBtn = document.getElementById('stopDistBtn');
            if (stopDistBtn) stopDistBtn.disabled = false;
        }
    }
    
    updateMetrics(testId) {
        const test = appState.activeTests.get(testId);
        if (!test) return;
        
        // Show metrics panel
        this.showMetricsPanel();
        
        // Render unified metrics
        this.renderMetrics(test);
        
        // Subscribe to real-time updates based on type
        if (test.type === 'SINGLE_NODE') {
            this.subscribeToWebSocket(testId);
        }
        // Distributed tests get metrics from polling (already in test.metrics)
    }
    
    renderMetrics(test) {
        const metrics = test.metrics || {};
        const latency = metrics.latency || {};
        
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
        
        // Latency percentiles
        document.getElementById('avgLatency').textContent = this.formatLatency(latency.avgMs || latency.p50Ms);
        document.getElementById('p50').textContent = this.formatLatency(latency.p50Ms);
        document.getElementById('p75').textContent = this.formatLatency(latency.p75Ms);
        document.getElementById('p90').textContent = this.formatLatency(latency.p90Ms);
        document.getElementById('p95').textContent = this.formatLatency(latency.p95Ms);
        document.getElementById('p99').textContent = this.formatLatency(latency.p99Ms);
        document.getElementById('p999').textContent = this.formatLatency(latency.p999Ms);
        
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
        
        try {
            this.websocket = new SockJS(wsUrl);
            const stompClient = Stomp.over(this.websocket);
            
            stompClient.connect({}, () => {
                console.log('WebSocket connected');
                appState.websocketConnection = stompClient;
                this.updateConnectionStatus(true);
            }, (error) => {
                console.error('WebSocket error:', error);
                this.updateConnectionStatus(false);
            });
        } catch (error) {
            console.error('Failed to initialize WebSocket:', error);
            this.updateConnectionStatus(false);
        }
    }
    
    subscribeToWebSocket(testId) {
        if (!appState.websocketConnection) {
            console.warn('WebSocket not connected');
            return;
        }
        
        // Subscribe to metrics topic for this test
        const topic = `/topic/metrics/${testId}`;
        
        appState.websocketConnection.subscribe(topic, (message) => {
            const metrics = JSON.parse(message.body);
            
            // Update test metrics in state
            const test = appState.activeTests.get(testId);
            if (test) {
                test.metrics = metrics;
                
                // If this is the selected test, update display
                if (appState.selectedTest === testId) {
                    this.renderMetrics(test);
                }
            }
        });
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
