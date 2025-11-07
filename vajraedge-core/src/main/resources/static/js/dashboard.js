// Main dashboard logic
window.currentTest = null;
let statusPollingInterval = null;
let previousMetrics = null; // Track previous metrics for diagnostics
let availablePlugins = {}; // Store loaded plugins

// Load available plugins on page load
document.addEventListener('DOMContentLoaded', function() {
    loadAvailablePlugins();
});

async function loadAvailablePlugins() {
    try {
        const response = await fetch('/api/tests/plugins');
        if (!response.ok) {
            console.error('Failed to load plugins:', response.statusText);
            return;
        }
        
        const data = await response.json();
        availablePlugins = data.plugins;
        
        // Populate task type dropdown
        populateTaskTypeDropdown();
        
        console.log('Loaded', data.totalCount, 'plugins');
    } catch (error) {
        console.error('Error loading plugins:', error);
    }
}

function populateTaskTypeDropdown() {
    const taskTypeSelect = document.getElementById('taskType');
    taskTypeSelect.innerHTML = ''; // Clear existing options
    
    // Add plugins grouped by category
    for (const [category, plugins] of Object.entries(availablePlugins)) {
        const optgroup = document.createElement('optgroup');
        optgroup.label = category;
        
        plugins.forEach(plugin => {
            const option = document.createElement('option');
            option.value = plugin.name;
            option.textContent = plugin.displayName;
            option.setAttribute('data-plugin', JSON.stringify(plugin));
            optgroup.appendChild(option);
        });
        
        taskTypeSelect.appendChild(optgroup);
    }
    
    // Add legacy hard-coded tasks if plugins don't cover them
    const legacyGroup = document.createElement('optgroup');
    legacyGroup.label = 'LEGACY';
    
    const hasHttpGet = Object.values(availablePlugins).flat().some(p => p.name === 'HTTP_GET');
    const hasSleep = Object.values(availablePlugins).flat().some(p => p.name === 'SLEEP');
    
    if (!hasSleep) {
        legacyGroup.appendChild(createOption('SLEEP', 'Sleep Task (Legacy)'));
    }
    legacyGroup.appendChild(createOption('CPU', 'CPU Task'));
    if (!hasHttpGet) {
        legacyGroup.appendChild(createOption('HTTP', 'HTTP Request (Legacy)'));
    }
    
    taskTypeSelect.appendChild(legacyGroup);
    
    // Trigger change event to update parameter fields
    taskTypeSelect.dispatchEvent(new Event('change'));
}

function createOption(value, text) {
    const option = document.createElement('option');
    option.value = value;
    option.textContent = text;
    return option;
}

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

// Handle task type change to show dynamic parameters
document.getElementById('taskType').addEventListener('change', function() {
    const selectedOption = this.options[this.selectedIndex];
    const pluginData = selectedOption.getAttribute('data-plugin');
    
    if (pluginData) {
        const plugin = JSON.parse(pluginData);
        renderPluginParameters(plugin);
    } else {
        renderLegacyParameters(this.value);
    }
});

function renderPluginParameters(plugin) {
    const container = document.getElementById('taskParametersContainer');
    container.innerHTML = ''; // Clear existing fields
    
    if (!plugin.parameters || plugin.parameters.length === 0) {
        return;
    }
    
    plugin.parameters.forEach(param => {
        const fieldGroup = document.createElement('div');
        fieldGroup.className = 'mb-3';
        
        const label = document.createElement('label');
        label.className = 'form-label';
        label.textContent = param.description || param.name;
        if (param.required) {
            const badge = document.createElement('span');
            badge.className = 'badge bg-danger ms-1';
            badge.textContent = 'Required';
            label.appendChild(badge);
        }
        fieldGroup.appendChild(label);
        
        let input;
        if (param.type === 'INTEGER') {
            input = document.createElement('input');
            input.type = 'number';
            input.className = 'form-control';
            input.id = 'param_' + param.name;
            input.name = param.name;
            input.required = param.required;
            if (param.defaultValue !== null && param.defaultValue !== undefined) {
                input.value = param.defaultValue;
            }
            if (param.minValue !== null) input.min = param.minValue;
            if (param.maxValue !== null) input.max = param.maxValue;
        } else if (param.type === 'BOOLEAN') {
            input = document.createElement('select');
            input.className = 'form-select';
            input.id = 'param_' + param.name;
            input.name = param.name;
            input.required = param.required;
            input.innerHTML = `
                <option value="true" ${param.defaultValue === true ? 'selected' : ''}>True</option>
                <option value="false" ${param.defaultValue === false ? 'selected' : ''}>False</option>
            `;
        } else {
            input = document.createElement('input');
            input.type = 'text';
            input.className = 'form-control';
            input.id = 'param_' + param.name;
            input.name = param.name;
            input.required = param.required;
            if (param.defaultValue !== null && param.defaultValue !== undefined) {
                input.value = param.defaultValue;
            }
            if (param.validationPattern) {
                input.pattern = param.validationPattern;
            }
        }
        
        fieldGroup.appendChild(input);
        
        // Add help text
        if (param.description) {
            const helpText = document.createElement('div');
            helpText.className = 'form-text';
            helpText.textContent = param.description;
            fieldGroup.appendChild(helpText);
        }
        
        container.appendChild(fieldGroup);
    });
}

function renderLegacyParameters(taskType) {
    const container = document.getElementById('taskParametersContainer');
    container.innerHTML = '';
    
    if (taskType === 'SLEEP') {
        container.innerHTML = `
            <div class="mb-3">
                <label for="param_duration" class="form-label">Sleep Duration (ms)</label>
                <input type="number" class="form-control" id="param_duration" name="duration" 
                       value="100" min="1" max="60000" required>
                <div class="form-text">Duration to sleep in milliseconds</div>
            </div>
        `;
    } else if (taskType === 'HTTP') {
        container.innerHTML = `
            <div class="mb-3">
                <label for="param_url" class="form-label">URL</label>
                <input type="url" class="form-control" id="param_url" name="url" 
                       value="http://localhost:8080/actuator/health" required>
                <div class="form-text">Target URL for HTTP requests</div>
            </div>
        `;
    } else if (taskType === 'CPU') {
        // CPU task has no parameters
        container.innerHTML = `
            <div class="alert alert-info">
                <small>CPU task has no configurable parameters</small>
            </div>
        `;
    }
}

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

// Update task parameter fields based on selected task type
document.getElementById('taskType').addEventListener('change', function() {
    const taskType = this.value;
    const selectedOption = this.options[this.selectedIndex];
    const pluginData = selectedOption.getAttribute('data-plugin');
    
    if (pluginData) {
        // Plugin-based task - render dynamic parameter fields
        const plugin = JSON.parse(pluginData);
        renderPluginParameterFields(plugin);
    } else {
        // Legacy hard-coded task - use simple parameter field
        renderLegacyParameterField(taskType);
    }
});

function renderPluginParameterFields(plugin) {
    const container = document.getElementById('taskParametersContainer');
    container.innerHTML = ''; // Clear existing fields
    
    if (!plugin.parameters || plugin.parameters.length === 0) {
        container.innerHTML = '<div class="alert alert-info">No parameters required</div>';
        return;
    }
    
    plugin.parameters.forEach(param => {
        const fieldGroup = document.createElement('div');
        fieldGroup.className = 'mb-3';
        
        const label = document.createElement('label');
        label.className = 'form-label';
        label.htmlFor = 'param_' + param.name;
        label.textContent = param.name;
        
        if (param.required) {
            const requiredBadge = document.createElement('span');
            requiredBadge.className = 'badge bg-danger ms-1';
            requiredBadge.textContent = 'Required';
            label.appendChild(requiredBadge);
        }
        
        const input = createInputForParameter(param);
        input.id = 'param_' + param.name;
        input.setAttribute('data-param-name', param.name);
        
        const helpText = document.createElement('div');
        helpText.className = 'form-text';
        helpText.textContent = param.description || '';
        
        fieldGroup.appendChild(label);
        fieldGroup.appendChild(input);
        fieldGroup.appendChild(helpText);
        container.appendChild(fieldGroup);
    });
}

function createInputForParameter(param) {
    let input;
    
    switch (param.type) {
        case 'INTEGER':
            input = document.createElement('input');
            input.type = 'number';
            input.className = 'form-control';
            input.value = param.defaultValue || '';
            if (param.minValue !== null) input.min = param.minValue;
            if (param.maxValue !== null) input.max = param.maxValue;
            input.required = param.required;
            break;
            
        case 'BOOLEAN':
            input = document.createElement('select');
            input.className = 'form-select';
            input.innerHTML = '<option value="true">True</option><option value="false">False</option>';
            input.value = param.defaultValue || 'false';
            input.required = param.required;
            break;
            
        case 'STRING':
        default:
            input = document.createElement('input');
            input.type = 'text';
            input.className = 'form-control';
            input.value = param.defaultValue || '';
            if (param.validationPattern) {
                input.pattern = param.validationPattern;
            }
            input.required = param.required;
            break;
    }
    
    return input;
}

function renderLegacyParameterField(taskType) {
    const container = document.getElementById('taskParametersContainer');
    container.innerHTML = '';
    
    const fieldGroup = document.createElement('div');
    fieldGroup.className = 'mb-3';
    
    const label = document.createElement('label');
    label.className = 'form-label';
    label.htmlFor = 'taskParameter';
    label.textContent = 'Task Parameter';
    
    const input = document.createElement('input');
    input.id = 'taskParameter';
    input.className = 'form-control';
    
    const helpText = document.createElement('div');
    helpText.className = 'form-text';
    helpText.id = 'taskParameterHelp';
    
    if (taskType === 'HTTP') {
        input.value = 'http://localhost:8081/api/products';
        input.type = 'text';
        helpText.textContent = 'URL to test (e.g., http://localhost:8081/api/products)';
    } else {
        input.value = '100';
        input.type = 'number';
        helpText.textContent = 'Sleep/CPU duration in ms';
    }
    
    input.required = true;
    
    fieldGroup.appendChild(label);
    fieldGroup.appendChild(input);
    fieldGroup.appendChild(helpText);
    container.appendChild(fieldGroup);
}

function collectTaskParameters() {
    const container = document.getElementById('taskParametersContainer');
    const paramInputs = container.querySelectorAll('input, select, textarea');
    
    if (paramInputs.length === 0) {
        return null; // No parameters configured
    }
    
    // Collect all parameters into a map
    const parameters = {};
    paramInputs.forEach(input => {
        if (!input.name) return; // Skip inputs without name attribute
        
        const paramName = input.name;
        let value = input.value;
        
        // Convert to appropriate type based on input type
        if (input.type === 'number') {
            value = input.value ? parseInt(input.value) : null;
        } else if (input.type === 'checkbox') {
            value = input.checked;
        } else if (input.tagName === 'SELECT' && (value === 'true' || value === 'false')) {
            value = value === 'true';
        }
        
        if (value !== null && value !== '') {
            parameters[paramName] = value;
        }
    });
    
    // Return map if we have parameters, otherwise null
    return Object.keys(parameters).length > 0 ? parameters : null;
}

// ========================================
// PRE-FLIGHT VALIDATION FUNCTIONS
// ========================================

// Run pre-flight validation checks
async function runValidation(config) {
    try {
        const response = await fetch('/api/validation', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(config)
        });
        
        if (!response.ok) {
            throw new Error('Validation API failed: ' + response.statusText);
        }
        
        return await response.json();
    } catch (error) {
        console.error('Error running validation:', error);
        throw error;
    }
}

// Display validation results in the UI
function displayValidationResults(validationResult) {
    const panel = document.getElementById('validationPanel');
    const progress = document.getElementById('validationProgress');
    const summary = document.getElementById('validationSummary');
    const alert = document.getElementById('validationAlert');
    const icon = document.getElementById('validationIcon');
    const statusText = document.getElementById('validationStatusText');
    const summaryText = document.getElementById('validationSummaryText');
    const checkResults = document.getElementById('checkResults');
    const actions = document.getElementById('validationActions');
    
    // Show panel and hide progress
    panel.style.display = 'block';
    progress.style.display = 'none';
    summary.style.display = 'block';
    
    // Set alert styling based on status
    const status = validationResult.status;
    alert.className = 'alert mb-3';
    
    if (status === 'PASS') {
        alert.classList.add('alert-validation-pass');
        icon.textContent = '✅';
        statusText.textContent = 'All Checks Passed';
    } else if (status === 'WARN') {
        alert.classList.add('alert-validation-warn');
        icon.textContent = '⚠️';
        statusText.textContent = 'Validation Passed with Warnings';
    } else { // FAIL
        alert.classList.add('alert-validation-fail');
        icon.textContent = '❌';
        statusText.textContent = 'Validation Failed';
    }
    
    // Set summary text
    const checks = validationResult.checkResults || [];
    const passCount = checks.filter(c => c.status === 'PASS').length;
    const warnCount = checks.filter(c => c.status === 'WARN').length;
    const failCount = checks.filter(c => c.status === 'FAIL').length;
    const skipCount = checks.filter(c => c.status === 'SKIP').length;
    
    let summaryParts = [];
    if (passCount > 0) summaryParts.push(`${passCount} passed`);
    if (warnCount > 0) summaryParts.push(`${warnCount} warnings`);
    if (failCount > 0) summaryParts.push(`${failCount} failed`);
    if (skipCount > 0) summaryParts.push(`${skipCount} skipped`);
    
    summaryText.textContent = `${checks.length} checks completed: ${summaryParts.join(', ')}`;
    
    // Display individual check results
    checkResults.innerHTML = '';
    checks.forEach((check, index) => {
        const checkItem = createCheckResultElement(check, index);
        checkResults.appendChild(checkItem);
    });
    
    // Display action buttons based on status
    actions.innerHTML = '';
    
    if (status === 'FAIL') {
        actions.innerHTML = `
            <div class="d-grid gap-2">
                <button type="button" class="btn btn-danger" disabled>
                    ❌ Cannot Start Test - Fix Issues First
                </button>
                <button type="button" class="btn btn-secondary" onclick="hideValidationPanel()">
                    Close
                </button>
            </div>
        `;
    } else if (status === 'WARN') {
        actions.innerHTML = `
            <div class="d-grid gap-2">
                <button type="button" class="btn btn-warning" onclick="proceedWithTest()">
                    ⚠️ Proceed Anyway
                </button>
                <button type="button" class="btn btn-secondary" onclick="hideValidationPanel()">
                    Cancel
                </button>
            </div>
        `;
    } else { // PASS
        actions.innerHTML = `
            <div class="d-grid gap-2">
                <button type="button" class="btn btn-success" onclick="proceedWithTest()">
                    ✅ Start Test
                </button>
                <button type="button" class="btn btn-secondary" onclick="hideValidationPanel()">
                    Cancel
                </button>
            </div>
        `;
    }
}

// Create HTML element for a single check result
function createCheckResultElement(check, index) {
    const div = document.createElement('div');
    div.className = `validation-check-item status-${check.status.toLowerCase()}`;
    div.id = `check-${index}`;
    
    // Get icon based on status
    let icon = '✅';
    if (check.status === 'WARN') icon = '⚠️';
    else if (check.status === 'FAIL') icon = '❌';
    else if (check.status === 'SKIP') icon = '⏭️';
    
    // Format duration
    const duration = check.durationMs ? `${check.durationMs}ms` : '';
    
    // Create header (always visible)
    const header = document.createElement('div');
    header.className = 'validation-check-header';
    header.innerHTML = `
        <span class="validation-check-icon">${icon}</span>
        <span class="validation-check-name">${check.checkName}</span>
        ${duration ? `<span class="validation-check-duration">${duration}</span>` : ''}
        ${check.details && check.details.length > 0 ? '<span class="validation-check-toggle">▼</span>' : ''}
    `;
    
    // Create message (always visible)
    const message = document.createElement('div');
    message.className = 'validation-check-message';
    message.textContent = check.message;
    
    // Create collapsible details section (if details exist)
    const details = document.createElement('div');
    details.className = 'validation-check-details';
    
    if (check.details && check.details.length > 0) {
        const ul = document.createElement('ul');
        check.details.forEach(detail => {
            const li = document.createElement('li');
            li.textContent = detail;
            ul.appendChild(li);
        });
        details.appendChild(ul);
        
        // Add click handler to toggle details
        header.style.cursor = 'pointer';
        header.addEventListener('click', () => {
            details.classList.toggle('show');
            const toggle = header.querySelector('.validation-check-toggle');
            if (toggle) {
                toggle.classList.toggle('expanded');
            }
        });
    }
    
    div.appendChild(header);
    div.appendChild(message);
    if (check.details && check.details.length > 0) {
        div.appendChild(details);
    }
    
    return div;
}

// Show validation progress
function showValidationProgress() {
    const panel = document.getElementById('validationPanel');
    const progress = document.getElementById('validationProgress');
    const summary = document.getElementById('validationSummary');
    
    panel.style.display = 'block';
    progress.style.display = 'block';
    summary.style.display = 'none';
}

// Hide validation panel
window.hideValidationPanel = function() {
    document.getElementById('validationPanel').style.display = 'none';
};

// Proceed with test start (called from validation action buttons)
window.proceedWithTest = async function() {
    hideValidationPanel();
    
    // Re-enable start button and trigger actual test start
    document.getElementById('startBtn').disabled = false;
    document.getElementById('startSpinner').classList.remove('d-none');
    
    // Call the actual test start function
    await startTestExecution(window.pendingTestConfig);
};

// Actual test start logic (extracted from form handler)
async function startTestExecution(config) {
    try {
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
        
        // Initialize or resize charts
        console.log('Metrics panel now visible, initializing charts...');
        if (!window.tpsChart || !window.latencyChart) {
            initializeCharts();
        } else {
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
        
        // Start polling for status updates
        startStatusPolling(result.testId);
        
        // Load active tests
        loadActiveTests();
        
    } catch (error) {
        console.error('Error starting test:', error);
        alert('Failed to start test: ' + error.message);
    } finally {
        document.getElementById('startBtn').disabled = false;
        document.getElementById('startSpinner').classList.add('d-none');
    }
}

// ========================================
// FORM SUBMISSION HANDLER (WITH VALIDATION)
// ========================================

// Handle form submission to start a test
document.getElementById('testConfigForm').addEventListener('submit', async function(e) {
    e.preventDefault();
    
    const taskType = document.getElementById('taskType').value;
    const taskParameter = collectTaskParameters();
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
        taskParameter: taskParameter
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
        
        // Store config for later use if validation passes with warnings
        window.pendingTestConfig = config;
        
        // Step 1: Run pre-flight validation
        console.log('Running pre-flight validation...');
        showValidationProgress();
        
        const validationResult = await runValidation(config);
        console.log('Validation result:', validationResult);
        
        // Step 2: Display validation results
        displayValidationResults(validationResult);
        
        // Step 3: Handle validation status
        if (validationResult.status === 'FAIL') {
            // Validation failed - do not start test
            console.log('Validation failed, test blocked');
            document.getElementById('startBtn').disabled = false;
            document.getElementById('startSpinner').classList.add('d-none');
            return; // Stop here, user must fix issues
        }
        
        if (validationResult.status === 'WARN') {
            // Validation passed with warnings - wait for user decision
            console.log('Validation passed with warnings, waiting for user decision');
            document.getElementById('startBtn').disabled = false;
            document.getElementById('startSpinner').classList.add('d-none');
            return; // User will click "Proceed Anyway" button if they want to continue
        }
        
        // Step 4: Validation passed - proceed automatically
        console.log('Validation passed, proceeding with test start');
        await startTestExecution(config);
        
    } catch (error) {
        console.error('Error during test start process:', error);
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
