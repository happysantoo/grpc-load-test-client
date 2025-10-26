// WebSocket connection management
let stompClient = null;
let currentTestId = null;
let metricsSubscription = null;
let statusSubscription = null;

// Initialize WebSocket connection
function connect() {
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    
    // Disable debug logging in production
    stompClient.debug = null;
    
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
function subscribeToMetrics(testId) {
    if (!stompClient || !stompClient.connected) {
        console.warn('WebSocket not connected');
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
        const metrics = JSON.parse(message.body);
        console.log('Received metrics update:', metrics);
        updateMetricsDisplay(metrics);
        updateCharts(metrics);
    });
    
    // Subscribe to status updates
    statusSubscription = stompClient.subscribe('/topic/status/' + testId, function(message) {
        const statusUpdate = JSON.parse(message.body);
        handleStatusUpdate(statusUpdate);
    });
    
    console.log('Subscribed to metrics for test:', testId);
}

// Unsubscribe from current test
function unsubscribeFromMetrics() {
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
