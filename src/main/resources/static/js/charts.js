// Chart.js instances
let tpsChart = null;
let latencyChart = null;

// Data buffers for charts (keep last 60 data points)
const MAX_DATA_POINTS = 60;
let tpsData = [];
let latencyData = {
    p50: [],
    p95: [],
    p99: []
};

// Initialize charts
window.initializeCharts = function initializeCharts() {
    console.log('Initializing charts...');
    
    // Check if Chart.js is loaded
    if (typeof Chart === 'undefined') {
        console.error('Chart.js is not loaded!');
        return;
    }
    
    console.log('Chart.js version:', Chart.version);
    
    try {
        // TPS Chart
        const tpsCanvas = document.getElementById('tpsChart');
        if (!tpsCanvas) {
            console.error('tpsChart canvas not found!');
            return;
        }
        console.log('Found tpsChart canvas:', tpsCanvas);
        
        const tpsCtx = tpsCanvas.getContext('2d');
        tpsChart = new Chart(tpsCtx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [{
                label: 'TPS',
                data: [],
                borderColor: 'rgb(75, 192, 192)',
                backgroundColor: 'rgba(75, 192, 192, 0.1)',
                tension: 0.4,
                fill: true
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: true,
            plugins: {
                legend: {
                    display: true
                }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    title: {
                        display: true,
                        text: 'Transactions/sec'
                    }
                },
                x: {
                    title: {
                        display: true,
                        text: 'Time'
                    }
                }
            }
        }
    });
    
    console.log('TPS chart created successfully');
    window.tpsChart = tpsChart;  // Export globally

    // Latency Chart
    const latencyCanvas = document.getElementById('latencyChart');
    if (!latencyCanvas) {
        console.error('latencyChart canvas not found!');
        return;
    }
    console.log('Found latencyChart canvas:', latencyCanvas);
    
    const latencyCtx = latencyCanvas.getContext('2d');
    latencyChart = new Chart(latencyCtx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [
                {
                    label: 'P50',
                    data: [],
                    borderColor: 'rgb(54, 162, 235)',
                    backgroundColor: 'rgba(54, 162, 235, 0.1)',
                    tension: 0.4
                },
                {
                    label: 'P95',
                    data: [],
                    borderColor: 'rgb(255, 206, 86)',
                    backgroundColor: 'rgba(255, 206, 86, 0.1)',
                    tension: 0.4
                },
                {
                    label: 'P99',
                    data: [],
                    borderColor: 'rgb(255, 99, 132)',
                    backgroundColor: 'rgba(255, 99, 132, 0.1)',
                    tension: 0.4
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: true,
            plugins: {
                legend: {
                    display: true
                }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    title: {
                        display: true,
                        text: 'Latency (ms)'
                    }
                },
                x: {
                    title: {
                        display: true,
                        text: 'Time'
                    }
                }
            }
        }
    });
    
    console.log('Latency chart created successfully');
    window.latencyChart = latencyChart;  // Export globally
    console.log('Both charts initialized:', {tpsChart: !!tpsChart, latencyChart: !!latencyChart});
    
    } catch (error) {
        console.error('Error initializing charts:', error);
    }
}

// Update charts with new metrics
window.updateCharts = function updateCharts(metrics) {
    console.log('updateCharts called with:', metrics);
    
    try {
    if (!tpsChart || !latencyChart) {
        console.warn('Charts not initialized yet, attempting initialization...');
        initializeCharts();
        if (!tpsChart || !latencyChart) {
            console.error('Failed to initialize charts');
            return;
        }
    }

    const timestamp = new Date().toLocaleTimeString();

    // Update TPS chart
    tpsChart.data.labels.push(timestamp);
    tpsChart.data.datasets[0].data.push(metrics.currentTps || 0);

    // Keep only last MAX_DATA_POINTS
    if (tpsChart.data.labels.length > MAX_DATA_POINTS) {
        tpsChart.data.labels.shift();
        tpsChart.data.datasets[0].data.shift();
    }

    tpsChart.update('none'); // Update without animation for smoother updates

    // Update Latency chart
    if (metrics.latencyPercentiles) {
        latencyChart.data.labels.push(timestamp);
        latencyChart.data.datasets[0].data.push(metrics.latencyPercentiles.p50 || 0);
        latencyChart.data.datasets[1].data.push(metrics.latencyPercentiles.p95 || 0);
        latencyChart.data.datasets[2].data.push(metrics.latencyPercentiles.p99 || 0);

        // Keep only last MAX_DATA_POINTS
        if (latencyChart.data.labels.length > MAX_DATA_POINTS) {
            latencyChart.data.labels.shift();
            latencyChart.data.datasets.forEach(dataset => dataset.data.shift());
        }

        latencyChart.update('none');
    } else {
        console.warn('No latency percentiles in metrics:', metrics);
    }
    } catch (error) {
        console.error('Error in updateCharts:', error);
    }
}

// Reset charts when starting a new test
window.resetCharts = function resetCharts() {
    if (tpsChart) {
        tpsChart.data.labels = [];
        tpsChart.data.datasets[0].data = [];
        tpsChart.update();
    }

    if (latencyChart) {
        latencyChart.data.labels = [];
        latencyChart.data.datasets.forEach(dataset => dataset.data = []);
        latencyChart.update();
    }

    tpsData = [];
    latencyData = { p50: [], p95: [], p99: [] };
}

// Initialize charts on page load
window.addEventListener('load', function() {
    console.log('Page loaded, attempting to initialize charts...');
    
    // Try to initialize charts
    initializeCharts();
    
    // If charts didn't initialize, retry after a short delay
    if (!tpsChart || !latencyChart) {
        console.warn('Charts not initialized on first try, retrying in 500ms...');
        setTimeout(function() {
            initializeCharts();
        }, 500);
    }
});
