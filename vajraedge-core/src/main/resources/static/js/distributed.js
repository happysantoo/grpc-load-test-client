/**
 * Distributed Testing UI Logic
 */

let currentDistributedTestId = null;
let workerRefreshInterval = null;

// Initialize distributed testing on page load
$(document).ready(function() {
    setupDistributedFormHandlers();
    loadWorkers();
    
    // Refresh workers every 5 seconds when on distributed tab
    $('#distributed-tab').on('shown.bs.tab', function() {
        loadWorkers();
        workerRefreshInterval = setInterval(loadWorkers, 5000);
    });
    
    $('#distributed-tab').on('hidden.bs.tab', function() {
        if (workerRefreshInterval) {
            clearInterval(workerRefreshInterval);
            workerRefreshInterval = null;
        }
    });
});

/**
 * Setup form handlers for distributed testing
 */
function setupDistributedFormHandlers() {
    // Task type change handler - show/hide parameter sections
    $('#distTaskType').on('change', function() {
        const taskType = $(this).val();
        if (taskType === 'HTTP') {
            $('#httpParams').removeClass('d-none');
            $('#sleepParams').addClass('d-none');
        } else if (taskType === 'SLEEP') {
            $('#httpParams').addClass('d-none');
            $('#sleepParams').removeClass('d-none');
        }
    });
    
    // Start distributed test
    $('#distributedTestForm').on('submit', function(e) {
        e.preventDefault();
        startDistributedTest();
    });
    
    // Stop distributed test
    $('#stopDistBtn').on('click', function() {
        stopDistributedTest();
    });
    
    // Refresh workers
    $('#refreshWorkersBtn').on('click', function() {
        loadWorkers();
    });
}

/**
 * Start a distributed test
 */
function startDistributedTest() {
    const taskType = $('#distTaskType').val();
    
    // Collect task-specific parameters
    const taskParameters = {};
    if (taskType === 'HTTP') {
        taskParameters.url = $('#httpUrl').val();
        taskParameters.method = $('#httpMethod').val();
        taskParameters.timeout = $('#httpTimeout').val();
        
        // Add custom headers if provided
        const headers = $('#httpHeaders').val().trim();
        if (headers) {
            try {
                JSON.parse(headers); // Validate JSON
                taskParameters.headers = headers;
            } catch (e) {
                showNotification('danger', 'Invalid JSON in custom headers');
                return;
            }
        }
    } else if (taskType === 'SLEEP') {
        taskParameters.duration = $('#sleepDuration').val();
    }
    
    const request = {
        taskType: taskType,
        targetTps: parseInt($('#targetTps').val()),
        durationSeconds: parseInt($('#distDuration').val()),
        rampUpSeconds: parseInt($('#distRampUp').val()),
        maxConcurrency: parseInt($('#distMaxConcurrency').val()),
        minWorkers: parseInt($('#minWorkers').val()),
        taskParameters: taskParameters
    };
    
    // Show spinner
    $('#startDistSpinner').removeClass('d-none');
    $('#startDistBtn').prop('disabled', true);
    
    $.ajax({
        url: '/api/tests/distributed',
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify(request),
        success: function(response) {
            if (response.success) {
                currentDistributedTestId = response.testId;
                $('#startDistBtn').prop('disabled', true);
                $('#stopDistBtn').prop('disabled', false);
                
                showNotification('success', 'Distributed test started: ' + response.message);
                
                // Start monitoring test
                monitorDistributedTest(response.testId);
            } else {
                showNotification('danger', 'Failed to start test: ' + response.message);
                $('#startDistBtn').prop('disabled', false);
            }
        },
        error: function(xhr) {
            const error = xhr.responseJSON?.message || 'Failed to start distributed test';
            showNotification('danger', error);
            $('#startDistBtn').prop('disabled', false);
        },
        complete: function() {
            $('#startDistSpinner').addClass('d-none');
        }
    });
}

/**
 * Stop distributed test
 */
function stopDistributedTest() {
    if (!currentDistributedTestId) {
        return;
    }
    
    $.ajax({
        url: `/api/tests/distributed/${currentDistributedTestId}?graceful=true`,
        method: 'DELETE',
        success: function(response) {
            showNotification('info', 'Distributed test stopped');
            $('#startDistBtn').prop('disabled', false);
            $('#stopDistBtn').prop('disabled', true);
            currentDistributedTestId = null;
        },
        error: function(xhr) {
            showNotification('danger', 'Failed to stop test');
        }
    });
}

/**
 * Monitor distributed test status
 */
function monitorDistributedTest(testId) {
    const interval = setInterval(function() {
        $.ajax({
            url: `/api/tests/distributed/${testId}`,
            method: 'GET',
            success: function(response) {
                updateDistributedMetrics(response);
            },
            error: function() {
                clearInterval(interval);
            }
        });
    }, 1000);
    
    // Stop monitoring after test duration + 10 seconds
    const duration = parseInt($('#distDuration').val());
    setTimeout(function() {
        clearInterval(interval);
        $('#startDistBtn').prop('disabled', false);
        $('#stopDistBtn').prop('disabled', true);
    }, (duration + 10) * 1000);
}

/**
 * Update distributed metrics display
 */
function updateDistributedMetrics(data) {
    const metrics = data.metrics;
    const testInfo = data.testInfo;
    
    if (metrics) {
        // Update metrics cards
        $('#totalRequests').text(metrics.totalRequests?.toLocaleString() || '0');
        $('#successfulRequests').text(metrics.successfulRequests?.toLocaleString() || '0');
        $('#failedRequests').text(metrics.failedRequests?.toLocaleString() || '0');
        $('#currentTps').text(metrics.currentTps?.toFixed(2) || '0.00');
        
        // Update percentiles
        if (metrics.latency) {
            $('#p50').text(metrics.latency.p50 || '-');
            $('#p95').text(metrics.latency.p95 || '-');
            $('#p99').text(metrics.latency.p99 || '-');
        }
        
        // Update success rate
        if (metrics.totalRequests > 0) {
            const successRate = (metrics.successfulRequests / metrics.totalRequests * 100).toFixed(2);
            $('#successRate').text(successRate + '%');
        }
    }
}

/**
 * Load and display workers
 */
function loadWorkers() {
    $.ajax({
        url: '/api/workers',
        method: 'GET',
        success: function(response) {
            displayWorkers(response.workers);
            updateWorkerSummary(response);
        },
        error: function() {
            $('#workerList').html('<p class="text-danger">Failed to load workers</p>');
        }
    });
}

/**
 * Display workers in the UI
 */
function displayWorkers(workers) {
    if (!workers || workers.length === 0) {
        $('#workerList').html('<p class="text-muted">No workers registered</p>');
        return;
    }
    
    let html = '';
    workers.forEach(function(worker) {
        const statusBadge = getWorkerStatusBadge(worker.healthStatus);
        const loadPercentage = worker.loadPercentage?.toFixed(1) || '0.0';
        
        html += `
            <div class="card mb-2">
                <div class="card-body p-2">
                    <div class="d-flex justify-content-between align-items-center">
                        <div>
                            <strong>${worker.workerId}</strong>
                            <br>
                            <small class="text-muted">${worker.hostname}</small>
                        </div>
                        <div class="text-end">
                            ${statusBadge}
                            <br>
                            <small>Load: ${loadPercentage}%</small>
                        </div>
                    </div>
                    <div class="mt-2">
                        <div class="progress" style="height: 5px;">
                            <div class="progress-bar ${getProgressBarClass(worker.healthStatus)}" 
                                 role="progressbar" 
                                 style="width: ${loadPercentage}%">
                            </div>
                        </div>
                    </div>
                    <div class="mt-1">
                        <small class="text-muted">
                            Capacity: ${worker.currentLoad}/${worker.maxCapacity} | 
                            Tasks: ${worker.supportedTaskTypes?.join(', ') || 'None'}
                        </small>
                    </div>
                </div>
            </div>
        `;
    });
    
    $('#workerList').html(html);
}

/**
 * Update worker summary
 */
function updateWorkerSummary(response) {
    const summary = `${response.healthyCount}/${response.totalCount} healthy`;
    $('#workerPanel .card-header h5').html(`👷 Worker Nodes <small class="text-white">(${summary})</small>`);
}

/**
 * Get status badge HTML for worker
 */
function getWorkerStatusBadge(status) {
    const badges = {
        'HEALTHY': '<span class="badge bg-success">Healthy</span>',
        'AT_CAPACITY': '<span class="badge bg-warning">At Capacity</span>',
        'OVERLOADED': '<span class="badge bg-danger">Overloaded</span>',
        'UNHEALTHY': '<span class="badge bg-danger">Unhealthy</span>',
        'DISCONNECTED': '<span class="badge bg-secondary">Disconnected</span>'
    };
    return badges[status] || '<span class="badge bg-secondary">Unknown</span>';
}

/**
 * Get progress bar class based on status
 */
function getProgressBarClass(status) {
    const classes = {
        'HEALTHY': 'bg-success',
        'AT_CAPACITY': 'bg-warning',
        'OVERLOADED': 'bg-danger',
        'UNHEALTHY': 'bg-danger',
        'DISCONNECTED': 'bg-secondary'
    };
    return classes[status] || 'bg-secondary';
}

/**
 * Show notification
 */
function showNotification(type, message) {
    // Use existing notification system or create a simple toast
    console.log(`[${type.toUpperCase()}] ${message}`);
    
    // Optional: Show Bootstrap toast
    const toastHtml = `
        <div class="toast align-items-center text-white bg-${type} border-0" role="alert">
            <div class="d-flex">
                <div class="toast-body">${message}</div>
                <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
            </div>
        </div>
    `;
    
    // Append to body and show (requires toast container in HTML)
    const $toast = $(toastHtml);
    $('body').append($toast);
    const toast = new bootstrap.Toast($toast[0]);
    toast.show();
    
    // Remove after hidden
    $toast.on('hidden.bs.toast', function() {
        $toast.remove();
    });
}
