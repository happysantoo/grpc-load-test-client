// WebSocket connection management
let stompClient = null;
let currentTestId = null;
let metricsSubscription = null;
let statusSubscription = null;

// Initialize WebSocket connection
function connect() {
    console.log('Attempting to connect to WebSocket...');
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    
    // Enable debug logging temporarily
    stompClient.debug = function(str) {
        console.log('STOMP: ' + str);
    };
    
    stompClient.connect({}, function(frame) {
        console.log('Connected: ' + frame);
        updateConnectionStatus(true);
    }, function(error) {
        console.error('WebSocket error:', error);
        updateConnectionStatus(false);
        // Attempt to reconnect after 5 seconds
        setTimeout(connect, 5000);
    });
}

// Disconnect WebSocket
function disconnect() {
    if (stompClient !== null) {
        stompClient.disconnect();
    }
    updateConnectionStatus(false);
    console.log('Disconnected');
}

// Subscribe to metrics for a specific test
window.subscribeToMetrics = function subscribeToMetrics(testId) {
    console.log('subscribeToMetrics called with testId:', testId);
    
    if (!stompClient || !stompClient.connected) {
        console.warn('WebSocket not connected, waiting...');
        // Retry after a short delay
        setTimeout(function() {
            subscribeToMetrics(testId);
        }, 500);
        return;
    }
    
    // Unsubscribe from previous test if any
    if (metricsSubscription) {
        metricsSubscription.unsubscribe();
    }
    if (statusSubscription) {
        statusSubscription.unsubscribe();
    }
    
    currentTestId = testId;
    
    // Subscribe to metrics updates
    metricsSubscription = stompClient.subscribe('/topic/metrics/' + testId, function(message) {
        try {
            const metrics = JSON.parse(message.body);
            console.log('Received metrics update:', metrics);
            
            // Determine current test phase
            let testPhase = 'RAMP_UP';
            if (window.currentTest && window.currentTest.startTime) {
                const elapsed = Math.floor((new Date() - window.currentTest.startTime) / 1000);
                const config = window.currentTest.config;
                
                if (config) {
                    const rampStrategy = config.rampStrategyType || 'STEP';
                    const sustainDuration = config.sustainDurationSeconds || 0;
                    let rampDuration = 0;
                    
                    // Calculate ramp duration based on strategy
                    if (rampStrategy === 'LINEAR') {
                        rampDuration = config.rampDurationSeconds || 60;
                    } else {
                        const startConcurrency = config.startingConcurrency || 10;
                        const maxConcurrency = config.maxConcurrency || 100;
                        const rampStep = config.rampStep || 10;
                        const rampInterval = config.rampIntervalSeconds || 30;
                        const usersToAdd = maxConcurrency - startConcurrency;
                        const stepsNeeded = Math.ceil(usersToAdd / rampStep);
                        rampDuration = stepsNeeded * rampInterval;
                    }
                    
                    // Determine phase
                    if (sustainDuration > 0 && elapsed >= rampDuration && elapsed < (rampDuration + sustainDuration)) {
                        testPhase = 'SUSTAIN';
                    }
                }
            }
            
            if (typeof updateMetricsDisplay === 'function') {
                updateMetricsDisplay(metrics);
            } else {
                console.error('updateMetricsDisplay is not defined!');
            }
            
            if (typeof updateCharts === 'function') {
                updateCharts(metrics, testPhase);
            } else {
                console.error('updateCharts is not defined!');
            }
        } catch (error) {
            console.error('Error processing metrics:', error);
        }
    });
    
    // Subscribe to status updates
    statusSubscription = stompClient.subscribe('/topic/status/' + testId, function(message) {
        const statusUpdate = JSON.parse(message.body);
        handleStatusUpdate(statusUpdate);
    });
    
    console.log('Subscribed to metrics for test:', testId);
}

// Unsubscribe from current test
window.unsubscribeFromMetrics = function unsubscribeFromMetrics() {
    if (metricsSubscription) {
        metricsSubscription.unsubscribe();
        metricsSubscription = null;
    }
    if (statusSubscription) {
        statusSubscription.unsubscribe();
        statusSubscription = null;
    }
    currentTestId = null;
}

// Update connection status indicator
function updateConnectionStatus(connected) {
    const statusElement = document.getElementById('connection-status');
    if (connected) {
        statusElement.innerHTML = '<span class="badge bg-success">Connected</span>';
    } else {
        statusElement.innerHTML = '<span class="badge bg-danger">Disconnected</span>';
    }
}

// Handle status updates (start, stop, complete)
function handleStatusUpdate(statusUpdate) {
    console.log('Status update:', statusUpdate);
    
    if (statusUpdate.status === 'COMPLETED' || statusUpdate.status === 'STOPPED') {
        // Test finished, update UI
        document.getElementById('testStatus').textContent = statusUpdate.status;
        document.getElementById('startBtn').disabled = false;
        document.getElementById('stopBtn').disabled = true;
        document.getElementById('startSpinner').classList.add('d-none');
    }
}

// Initialize connection on page load
window.addEventListener('load', function() {
    connect();
});

// Disconnect on page unload
window.addEventListener('beforeunload', function() {
    disconnect();
});
