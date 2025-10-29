// Main dashboard logic
window.currentTest = null;
let statusPollingInterval = null;
let previousMetrics = null; // Track previous metrics for diagnostics

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
        sustainDurationSeconds: parseInt(document.getElementById('sustainDuration').value) || 0,
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
        
        window.currentTest = {
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
    if (!window.currentTest) {
        return;
    }
    
    try {
        const response = await fetch('/api/tests/' + window.currentTest.testId, {
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
        const pendingTasksEl = document.getElementById('pendingTasks');
        const avgLatencyEl = document.getElementById('avgLatency');
        
        console.log('DOM elements found:', {
            totalRequests: !!totalRequestsEl,
            currentTps: !!currentTpsEl,
            activeTasks: !!activeTasksEl,
            pendingTasks: !!pendingTasksEl,
            avgLatency: !!avgLatencyEl
        });
        
        if (totalRequestsEl) totalRequestsEl.textContent = formatNumber(metrics.totalRequests || 0);
        if (currentTpsEl) currentTpsEl.textContent = formatNumber(metrics.currentTps || 0);
        if (activeTasksEl) activeTasksEl.textContent = formatNumber(metrics.activeTasks || 0);
        if (pendingTasksEl) pendingTasksEl.textContent = formatNumber(metrics.pendingTasks || 0);
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
    
    // Update elapsed time and determine phase
    let testPhase = 'RAMP_UP';
    if (currentTest) {
        const elapsed = Math.floor((new Date() - currentTest.startTime) / 1000);
        document.getElementById('elapsedTime').textContent = formatDuration(elapsed);
        
        // Calculate and update test phase
        testPhase = updateTestPhase(elapsed, currentTest.config);
    }
    
    // Update diagnostics panel
    updateDiagnostics(metrics);
    
    // Update charts with phase information
    if (typeof updateCharts === 'function') {
        updateCharts(metrics, testPhase);
    }
    
    // Store for next calculation
    previousMetrics = metrics;
    } catch (error) {
        console.error('Error in updateMetricsDisplay:', error);
    }
}

// Update diagnostics panel with detailed metrics
function updateDiagnostics(metrics) {
    try {
        // Virtual Users / Active Tasks
        const virtualUsers = metrics.activeTasks || 0;
        document.getElementById('diagVirtualUsers').textContent = formatNumber(virtualUsers);
        
        // Tasks per user per second
        const tps = metrics.currentTps || 0;
        const tasksPerUser = virtualUsers > 0 ? (tps / virtualUsers).toFixed(2) : '0.00';
        document.getElementById('diagTasksPerUser').textContent = tasksPerUser;
        
        // Submitted and Completed (calculate from total)
        const totalRequests = metrics.totalRequests || 0;
        const activeTasks = metrics.activeTasks || 0;
        const pendingTasks = metrics.pendingTasks || 0;
        
        document.getElementById('diagSubmitted').textContent = formatNumber(totalRequests + activeTasks + pendingTasks);
        document.getElementById('diagCompleted').textContent = formatNumber(totalRequests);
        
        // Queue Depth (pending tasks)
        const queueDepth = pendingTasks;
        const queueEl = document.getElementById('diagQueueDepth');
        queueEl.textContent = formatNumber(queueDepth);
        if (queueDepth === 0) {
            queueEl.className = 'badge bg-success';
        } else if (queueDepth < 100) {
            queueEl.className = 'badge bg-warning';
        } else {
            queueEl.className = 'badge bg-danger';
        }
        
        // Latency Ratio (P99/P50)
        if (metrics.latencyPercentiles && metrics.latencyPercentiles.p50 > 0) {
            const p50 = metrics.latencyPercentiles.p50;
            const p99 = metrics.latencyPercentiles.p99;
            const ratio = (p99 / p50).toFixed(2);
            document.getElementById('diagLatencyRatio').textContent = ratio + 'x';
        } else {
            document.getElementById('diagLatencyRatio').textContent = '-';
        }
        
        // TPS Efficiency (how well VajraEdge is keeping up)
        // If pending tasks are building up, efficiency drops
        const efficiency = queueDepth === 0 ? 100 : Math.max(0, 100 - (queueDepth / virtualUsers) * 100);
        document.getElementById('diagEfficiency').textContent = efficiency.toFixed(1) + '%';
        
        // Bottleneck Indicator
        const bottleneckEl = document.getElementById('diagBottleneck');
        const avgLatency = metrics.avgLatencyMs || 0;
        
        console.log('Bottleneck analysis:', {
            queueDepth,
            virtualUsers,
            tps,
            avgLatency,
            hasPrevious: !!previousMetrics,
            prevTps: previousMetrics?.currentTps,
            prevUsers: previousMetrics?.activeTasks
        });
        
        // Priority 1: Check for VajraEdge bottleneck (queue buildup)
        if (queueDepth > virtualUsers * 2 && queueDepth > 50) {
            // Severe queue buildup = VajraEdge bottleneck
            bottleneckEl.textContent = 'VajraEdge (Queue Buildup)';
            bottleneckEl.className = 'badge bg-danger';
        } else if (queueDepth > virtualUsers && queueDepth > 20) {
            // Moderate queue buildup
            bottleneckEl.textContent = 'VajraEdge (Growing Queue)';
            bottleneckEl.className = 'badge bg-warning';
        } 
        // Priority 2: Check for service saturation (needs sufficient data)
        else if (previousMetrics && virtualUsers >= 10) {
            const prevTps = previousMetrics.currentTps || 0;
            const prevUsers = previousMetrics.activeTasks || 0;
            const prevLatency = previousMetrics.avgLatencyMs || 0;
            
            const userIncrease = virtualUsers - prevUsers;
            const tpsIncrease = tps - prevTps;
            const latencyIncrease = avgLatency - prevLatency;
            
            // Calculate TPS per user to detect saturation
            const currentTpsPerUser = virtualUsers > 0 ? tps / virtualUsers : 0;
            const prevTpsPerUser = prevUsers > 0 ? prevTps / prevUsers : 0;
            const tpsPerUserDrop = prevTpsPerUser > 0 ? (prevTpsPerUser - currentTpsPerUser) / prevTpsPerUser : 0;
            
            console.log('Service saturation check:', { 
                userIncrease, 
                tpsIncrease, 
                latencyIncrease,
                currentTpsPerUser: currentTpsPerUser.toFixed(2),
                prevTpsPerUser: prevTpsPerUser.toFixed(2),
                tpsPerUserDrop: (tpsPerUserDrop * 100).toFixed(1) + '%'
            });
            
            // Service is saturated if:
            // 1. Users increased significantly but TPS didn't keep pace
            // 2. TPS per user is dropping (efficiency going down)
            // 3. Latency is increasing
            if (userIncrease >= 3) {
                if (tpsIncrease < userIncrease * 0.5 || tpsPerUserDrop > 0.15) {
                    // TPS not keeping up with user growth = Service saturated
                    bottleneckEl.textContent = 'HTTP Service (Saturated)';
                    bottleneckEl.className = 'badge bg-warning';
                } else if (latencyIncrease > 100 && avgLatency > 500) {
                    // Latency climbing = Service under stress
                    bottleneckEl.textContent = 'HTTP Service (High Latency)';
                    bottleneckEl.className = 'badge bg-warning';
                } else if (avgLatency < 300 && queueDepth < 10) {
                    // Good latency, no queue = Healthy
                    bottleneckEl.textContent = 'None (Healthy)';
                    bottleneckEl.className = 'badge bg-success';
                } else {
                    bottleneckEl.textContent = 'Analyzing...';
                    bottleneckEl.className = 'badge bg-secondary';
                }
            } else if (avgLatency > 1000) {
                // High latency even with stable users = Service bottleneck
                bottleneckEl.textContent = 'HTTP Service (High Latency)';
                bottleneckEl.className = 'badge bg-warning';
            } else if (queueDepth < 5 && avgLatency < 300) {
                // No queue, good latency = Healthy
                bottleneckEl.textContent = 'None (Healthy)';
                bottleneckEl.className = 'badge bg-success';
            } else {
                bottleneckEl.textContent = 'Analyzing...';
                bottleneckEl.className = 'badge bg-secondary';
            }
        } 
        // Priority 3: Initial state (not enough data yet)
        else if (virtualUsers < 10 || !previousMetrics) {
            bottleneckEl.textContent = 'Warming Up...';
            bottleneckEl.className = 'badge bg-info';
        } else {
            bottleneckEl.textContent = 'Analyzing...';
            bottleneckEl.className = 'badge bg-secondary';
        }
        
    } catch (error) {
        console.error('Error updating diagnostics:', error);
    }
}

// Update test phase indicator
function updateTestPhase(elapsedSeconds, config) {
    try {
        const phaseEl = document.getElementById('testPhase');
        if (!phaseEl || !config) return 'RAMP_UP';
        
        const rampStrategy = config.rampStrategyType || 'STEP';
        const sustainDuration = config.sustainDurationSeconds || 0;
        let rampDuration = 0;
        
        // Calculate ramp duration based on strategy
        if (rampStrategy === 'LINEAR') {
            rampDuration = config.rampDurationSeconds || 60;
        } else {
            // STEP strategy
            const startConcurrency = config.startingConcurrency || 10;
            const maxConcurrency = config.maxConcurrency || 100;
            const rampStep = config.rampStep || 10;
            const rampInterval = config.rampIntervalSeconds || 30;
            
            const usersToAdd = maxConcurrency - startConcurrency;
            const stepsNeeded = Math.ceil(usersToAdd / rampStep);
            rampDuration = stepsNeeded * rampInterval;
        }
        
        // Determine current phase
        let currentPhase = 'RAMP_UP';
        
        if (elapsedSeconds < rampDuration) {
            const progress = ((elapsedSeconds / rampDuration) * 100).toFixed(0);
            phaseEl.textContent = `Ramp-Up (${progress}%)`;
            phaseEl.className = 'badge bg-primary';
            currentPhase = 'RAMP_UP';
        } else if (sustainDuration > 0 && elapsedSeconds < (rampDuration + sustainDuration)) {
            const sustainElapsed = elapsedSeconds - rampDuration;
            const remainingSustain = sustainDuration - sustainElapsed;
            phaseEl.textContent = `Sustain (${remainingSustain}s left)`;
            phaseEl.className = 'badge bg-success';
            currentPhase = 'SUSTAIN';
        } else {
            phaseEl.textContent = 'Completed';
            phaseEl.className = 'badge bg-secondary';
            currentPhase = 'COMPLETED';
        }
        
        return currentPhase;
        
    } catch (error) {
        console.error('Error updating test phase:', error);
        return 'RAMP_UP';
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
                    window.currentTest = { testId: testId };
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
