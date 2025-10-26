// Main dashboard logic
let currentTest = null;
let statusPollingInterval = null;

// Handle form submission to start a test
document.getElementById('testConfigForm').addEventListener('submit', async function(e) {
    e.preventDefault();
    
    const config = {
        targetTps: parseInt(document.getElementById('targetTps').value),
        maxConcurrency: parseInt(document.getElementById('maxConcurrency').value),
        testDurationSeconds: parseInt(document.getElementById('testDuration').value),
        rampUpDurationSeconds: parseInt(document.getElementById('rampUpDuration').value),
        taskType: document.getElementById('taskType').value,
        taskParameter: parseInt(document.getElementById('taskParameter').value)
    };
    
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
        
        // Update UI
        document.getElementById('currentTestId').textContent = result.testId.substring(0, 8);
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
function updateMetricsDisplay(metrics) {
    // Update counters
    document.getElementById('totalRequests').textContent = formatNumber(metrics.totalRequests || 0);
    document.getElementById('currentTps').textContent = formatNumber(metrics.currentTps || 0);
    document.getElementById('activeTasks').textContent = formatNumber(metrics.activeTasks || 0);
    document.getElementById('avgLatency').textContent = formatLatency(metrics.avgLatencyMs || 0);
    
    // Update success rate
    if (metrics.successRate !== undefined && metrics.successRate !== null) {
        document.getElementById('successRate').textContent = formatPercentage(metrics.successRate);
    }
    
    // Update percentiles table
    if (metrics.latencyPercentiles) {
        document.getElementById('p50').textContent = formatLatency(metrics.latencyPercentiles.p50);
        document.getElementById('p75').textContent = formatLatency(metrics.latencyPercentiles.p75);
        document.getElementById('p90').textContent = formatLatency(metrics.latencyPercentiles.p90);
        document.getElementById('p95').textContent = formatLatency(metrics.latencyPercentiles.p95);
        document.getElementById('p99').textContent = formatLatency(metrics.latencyPercentiles.p99);
        document.getElementById('p999').textContent = formatLatency(metrics.latencyPercentiles['p99.9']);
    }
    
    // Update elapsed time
    if (currentTest) {
        const elapsed = Math.floor((new Date() - currentTest.startTime) / 1000);
        document.getElementById('elapsedTime').textContent = formatDuration(elapsed);
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
    try {
        const response = await fetch('/api/tests');
        if (!response.ok) {
            throw new Error('Failed to load active tests');
        }
        
        const data = await response.json();
        const listContainer = document.getElementById('activeTestsList');
        
        if (data.count === 0) {
            listContainer.innerHTML = '<p class="text-muted">No active tests</p>';
        } else {
            listContainer.innerHTML = '';
            Object.entries(data.activeTests).forEach(([testId, test]) => {
                const item = document.createElement('div');
                item.className = 'list-group-item list-group-item-action';
                item.innerHTML = `
                    <div class="d-flex w-100 justify-content-between">
                        <h6 class="mb-1">${testId.substring(0, 8)}</h6>
                        <small class="status-running">●</small>
                    </div>
                    <small class="text-muted">${test.config.taskType} - ${test.config.targetTps} TPS</small>
                `;
                item.addEventListener('click', () => {
                    currentTest = { testId: testId, config: test.config };
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
