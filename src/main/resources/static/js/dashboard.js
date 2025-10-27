// Main dashboard logic
let currentTest = null;
let statusPollingInterval = null;

// Handle test mode change
document.getElementById('testMode').addEventListener('change', function() {
    const mode = this.value;
    const tpsLimitField = document.getElementById('tpsLimitField');
    
    if (mode === 'RATE_LIMITED') {
        tpsLimitField.classList.remove('d-none');
    } else {
        tpsLimitField.classList.add('d-none');
    }
});

// Handle ramp strategy change
document.getElementById('rampStrategy').addEventListener('change', function() {
    const strategy = this.value;
    const stepFields = document.getElementById('stepStrategyFields');
    const linearFields = document.getElementById('linearStrategyFields');
    
    if (strategy === 'STEP') {
        stepFields.classList.remove('d-none');
        linearFields.classList.add('d-none');
    } else {
        stepFields.classList.add('d-none');
        linearFields.classList.remove('d-none');
    }
});

// Update task parameter help text based on task type
document.getElementById('taskType').addEventListener('change', function() {
    const taskType = this.value;
    const taskParameterInput = document.getElementById('taskParameter');
    const helpText = document.getElementById('taskParameterHelp');
    
    if (taskType === 'HTTP') {
        taskParameterInput.value = 'http://localhost:8081/api/products';
        taskParameterInput.type = 'text';
        helpText.textContent = 'URL to test (e.g., http://localhost:8081/api/products)';
    } else {
        taskParameterInput.value = '100';
        taskParameterInput.type = 'number';
        helpText.textContent = 'Sleep/CPU duration in ms';
    }
});

// Handle form submission to start a test
document.getElementById('testConfigForm').addEventListener('submit', async function(e) {
    e.preventDefault();
    
    const taskType = document.getElementById('taskType').value;
    const taskParameterValue = document.getElementById('taskParameter').value;
    const testMode = document.getElementById('testMode').value;
    const rampStrategy = document.getElementById('rampStrategy').value;
    
    // Build config based on selected mode and strategy
    const config = {
        mode: testMode,
        startingConcurrency: parseInt(document.getElementById('startingConcurrency').value),
        maxConcurrency: parseInt(document.getElementById('maxConcurrency').value),
        rampStrategyType: rampStrategy,
        testDurationSeconds: parseInt(document.getElementById('testDuration').value),
        taskType: taskType,
        taskParameter: taskType === 'HTTP' ? taskParameterValue : parseInt(taskParameterValue)
    };
    
    // Add strategy-specific fields
    if (rampStrategy === 'STEP') {
        config.rampStep = parseInt(document.getElementById('rampStep').value);
        config.rampIntervalSeconds = parseInt(document.getElementById('rampInterval').value);
    } else {
        config.rampDurationSeconds = parseInt(document.getElementById('rampDuration').value);
    }
    
    // Add TPS limit for hybrid mode
    if (testMode === 'RATE_LIMITED') {
        const maxTpsLimit = document.getElementById('maxTpsLimit').value;
        if (maxTpsLimit) {
            config.maxTpsLimit = parseInt(maxTpsLimit);
        }
    }
    
    try {
        document.getElementById('startBtn').disabled = true;
        document.getElementById('startSpinner').classList.remove('d-none');
        
        const response = await fetch('/api/tests', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(config)
        });
        
        if (!response.ok) {
            throw new Error('Failed to start test: ' + response.statusText);
        }
        
        const result = await response.json();
        console.log('Test started:', result);
        
        currentTest = {
            testId: result.testId,
            config: config,
            startTime: new Date()
        };
        
        // Show metrics panel
        document.getElementById('noTestMessage').classList.add('d-none');
        document.getElementById('metricsPanel').classList.remove('d-none');
        
        // Initialize or resize charts now that panel is visible
        console.log('Metrics panel now visible, initializing charts...');
        if (!window.tpsChart || !window.latencyChart) {
            // Charts not initialized yet, do it now
            initializeCharts();
        } else {
            // Charts exist but might need resizing
            window.tpsChart.resize();
            window.latencyChart.resize();
        }
        
        // Update UI
        document.getElementById('currentTestId').textContent = result.testId;
        document.getElementById('testStatus').textContent = 'RUNNING';
        document.getElementById('stopBtn').disabled = false;
        
        // Reset charts
        resetCharts();
        
        // Subscribe to WebSocket updates
        subscribeToMetrics(result.testId);
        
        // Start polling for status updates (fallback if WebSocket fails)
        startStatusPolling(result.testId);
        
        // Load active tests
        loadActiveTests();
        
    } catch (error) {
        console.error('Error starting test:', error);
        alert('Failed to start test: ' + error.message);
        document.getElementById('startBtn').disabled = false;
        document.getElementById('startSpinner').classList.add('d-none');
    }
});

// Handle stop button
document.getElementById('stopBtn').addEventListener('click', async function() {
    if (!currentTest) {
        return;
    }
    
    try {
        const response = await fetch('/api/tests/' + currentTest.testId, {
            method: 'DELETE'
        });
        
        if (!response.ok) {
            throw new Error('Failed to stop test: ' + response.statusText);
        }
        
        const result = await response.json();
        console.log('Test stopped:', result);
        
        document.getElementById('testStatus').textContent = 'STOPPED';
        document.getElementById('stopBtn').disabled = true;
        document.getElementById('startBtn').disabled = false;
        document.getElementById('startSpinner').classList.add('d-none');
        
        stopStatusPolling();
        unsubscribeFromMetrics();
        
        loadActiveTests();
        
    } catch (error) {
        console.error('Error stopping test:', error);
        alert('Failed to stop test: ' + error.message);
    }
});

// Update metrics display from WebSocket data
window.updateMetricsDisplay = function updateMetricsDisplay(metrics) {
    console.log('Updating metrics display:', metrics);
    
    try {
        // Update counters
        const totalRequestsEl = document.getElementById('totalRequests');
        const currentTpsEl = document.getElementById('currentTps');
        const activeTasksEl = document.getElementById('activeTasks');
        const avgLatencyEl = document.getElementById('avgLatency');
        
        console.log('DOM elements found:', {
            totalRequests: !!totalRequestsEl,
            currentTps: !!currentTpsEl,
            activeTasks: !!activeTasksEl,
            avgLatency: !!avgLatencyEl
        });
        
        if (totalRequestsEl) totalRequestsEl.textContent = formatNumber(metrics.totalRequests || 0);
        if (currentTpsEl) currentTpsEl.textContent = formatNumber(metrics.currentTps || 0);
        if (activeTasksEl) activeTasksEl.textContent = formatNumber(metrics.activeTasks || 0);
        if (avgLatencyEl) avgLatencyEl.textContent = formatLatency(metrics.avgLatencyMs || 0);
    
    // Update success rate
    if (metrics.successRate !== undefined && metrics.successRate !== null) {
        document.getElementById('successRate').textContent = formatPercentage(metrics.successRate);
    }
    
    // Update percentiles table
    if (metrics.latencyPercentiles) {
        console.log('Updating percentiles:', metrics.latencyPercentiles);
        document.getElementById('p50').textContent = formatLatency(metrics.latencyPercentiles.p50);
        document.getElementById('p75').textContent = formatLatency(metrics.latencyPercentiles.p75);
        document.getElementById('p90').textContent = formatLatency(metrics.latencyPercentiles.p90);
        document.getElementById('p95').textContent = formatLatency(metrics.latencyPercentiles.p95);
        document.getElementById('p99').textContent = formatLatency(metrics.latencyPercentiles.p99);
        document.getElementById('p999').textContent = formatLatency(metrics.latencyPercentiles['p99.9']);
    } else {
        console.warn('No latency percentiles in metrics');
    }
    
    // Update elapsed time
    if (currentTest) {
        const elapsed = Math.floor((new Date() - currentTest.startTime) / 1000);
        document.getElementById('elapsedTime').textContent = formatDuration(elapsed);
    }
    } catch (error) {
        console.error('Error in updateMetricsDisplay:', error);
    }
}

// Start polling for test status (fallback)
function startStatusPolling(testId) {
    stopStatusPolling();
    
    statusPollingInterval = setInterval(async () => {
        try {
            const response = await fetch('/api/tests/' + testId);
            if (response.ok) {
                const status = await response.json();
                
                if (status.status === 'COMPLETED' || status.status === 'STOPPED') {
                    document.getElementById('testStatus').textContent = status.status;
                    document.getElementById('stopBtn').disabled = true;
                    document.getElementById('startBtn').disabled = false;
                    document.getElementById('startSpinner').classList.add('d-none');
                    stopStatusPolling();
                    unsubscribeFromMetrics();
                    loadActiveTests();
                }
            }
        } catch (error) {
            console.error('Error polling status:', error);
        }
    }, 2000);
}

// Stop status polling
function stopStatusPolling() {
    if (statusPollingInterval) {
        clearInterval(statusPollingInterval);
        statusPollingInterval = null;
    }
}

// Load and display active tests
async function loadActiveTests() {
    console.log('loadActiveTests() called');
    try {
        const response = await fetch('/api/tests');
        if (!response.ok) {
            throw new Error('Failed to load active tests');
        }
        
        const data = await response.json();
        console.log('Active tests response:', data);
        const listContainer = document.getElementById('activeTestsList');
        
        if (data.count === 0) {
            console.log('No active tests');
            listContainer.innerHTML = '<p class="text-muted">No active tests</p>';
        } else {
            console.log('Found', data.count, 'active test(s)');
            listContainer.innerHTML = '';
            // Backend returns Map<String, String> where value is just the status
            Object.entries(data.activeTests).forEach(([testId, status]) => {
                console.log('Adding test to list:', testId, status);
                const item = document.createElement('div');
                item.className = 'list-group-item list-group-item-action';
                item.innerHTML = `
                    <div class="d-flex w-100 justify-content-between">
                        <h6 class="mb-1">${testId.substring(0, 8)}</h6>
                        <small class="status-running">●</small>
                    </div>
                    <small class="text-muted">${status}</small>
                `;
                item.addEventListener('click', () => {
                    currentTest = { testId: testId };
                    subscribeToMetrics(testId);
                    document.getElementById('noTestMessage').classList.add('d-none');
                    document.getElementById('metricsPanel').classList.remove('d-none');
                });
                listContainer.appendChild(item);
            });
        }
    } catch (error) {
        console.error('Error loading active tests:', error);
    }
}

// Formatting helpers
function formatNumber(num) {
    return new Intl.NumberFormat().format(num);
}

function formatLatency(ms) {
    if (ms === undefined || ms === null) return '-';
    return ms.toFixed(2) + 'ms';
}

function formatPercentage(percent) {
    if (percent === undefined || percent === null) return '-';
    return percent.toFixed(2) + '%';
}

function formatDuration(seconds) {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return mins > 0 ? `${mins}m ${secs}s` : `${secs}s`;
}

// Load active tests on page load
window.addEventListener('load', function() {
    loadActiveTests();
    
    // Refresh active tests every 5 seconds
    setInterval(loadActiveTests, 5000);
});
