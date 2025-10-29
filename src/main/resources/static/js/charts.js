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

// Track test phase for color coding
let currentPhase = 'RAMP_UP'; // RAMP_UP or SUSTAIN

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
                pointBackgroundColor: [],
                pointBorderColor: [],
                pointRadius: 4,
                pointHoverRadius: 6,
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
                    pointBackgroundColor: [],
                    pointBorderColor: [],
                    pointRadius: 4,
                    pointHoverRadius: 6,
                    tension: 0.4
                },
                {
                    label: 'P95',
                    data: [],
                    borderColor: 'rgb(255, 206, 86)',
                    backgroundColor: 'rgba(255, 206, 86, 0.1)',
                    pointBackgroundColor: [],
                    pointBorderColor: [],
                    pointRadius: 4,
                    pointHoverRadius: 6,
                    tension: 0.4
                },
                {
                    label: 'P99',
                    data: [],
                    borderColor: 'rgb(255, 99, 132)',
                    backgroundColor: 'rgba(255, 99, 132, 0.1)',
                    pointBackgroundColor: [],
                    pointBorderColor: [],
                    pointRadius: 4,
                    pointHoverRadius: 6,
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
window.updateCharts = function updateCharts(metrics, phase) {
    console.log('updateCharts called with:', metrics, 'phase:', phase);
    
    try {
    if (!tpsChart || !latencyChart) {
        console.warn('Charts not initialized yet, attempting initialization...');
        initializeCharts();
        if (!tpsChart || !latencyChart) {
            console.error('Failed to initialize charts');
            return;
        }
    }

    // Update current phase
    if (phase) {
        currentPhase = phase;
    }

    const timestamp = new Date().toLocaleTimeString();
    
    // Define colors based on phase
    const isSustain = currentPhase === 'SUSTAIN';
    const tpsPointColor = isSustain ? 'rgb(40, 167, 69)' : 'rgb(75, 192, 192)'; // Green for sustain, teal for ramp
    const p50Color = isSustain ? 'rgb(23, 162, 184)' : 'rgb(54, 162, 235)'; // Darker blue for sustain
    const p95Color = isSustain ? 'rgb(255, 193, 7)' : 'rgb(255, 206, 86)'; // Darker yellow for sustain
    const p99Color = isSustain ? 'rgb(220, 53, 69)' : 'rgb(255, 99, 132)'; // Darker red for sustain

    // Update TPS chart
    tpsChart.data.labels.push(timestamp);
    tpsChart.data.datasets[0].data.push(metrics.currentTps || 0);
    tpsChart.data.datasets[0].pointBackgroundColor.push(tpsPointColor);
    tpsChart.data.datasets[0].pointBorderColor.push(tpsPointColor);

    // Keep only last MAX_DATA_POINTS
    if (tpsChart.data.labels.length > MAX_DATA_POINTS) {
        tpsChart.data.labels.shift();
        tpsChart.data.datasets[0].data.shift();
        tpsChart.data.datasets[0].pointBackgroundColor.shift();
        tpsChart.data.datasets[0].pointBorderColor.shift();
    }

    tpsChart.update('none'); // Update without animation for smoother updates

    // Update Latency chart
    if (metrics.latencyPercentiles) {
        latencyChart.data.labels.push(timestamp);
        latencyChart.data.datasets[0].data.push(metrics.latencyPercentiles.p50 || 0);
        latencyChart.data.datasets[0].pointBackgroundColor.push(p50Color);
        latencyChart.data.datasets[0].pointBorderColor.push(p50Color);
        
        latencyChart.data.datasets[1].data.push(metrics.latencyPercentiles.p95 || 0);
        latencyChart.data.datasets[1].pointBackgroundColor.push(p95Color);
        latencyChart.data.datasets[1].pointBorderColor.push(p95Color);
        
        latencyChart.data.datasets[2].data.push(metrics.latencyPercentiles.p99 || 0);
        latencyChart.data.datasets[2].pointBackgroundColor.push(p99Color);
        latencyChart.data.datasets[2].pointBorderColor.push(p99Color);

        // Keep only last MAX_DATA_POINTS
        if (latencyChart.data.labels.length > MAX_DATA_POINTS) {
            latencyChart.data.labels.shift();
            latencyChart.data.datasets.forEach(dataset => {
                dataset.data.shift();
                dataset.pointBackgroundColor.shift();
                dataset.pointBorderColor.shift();
            });
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
    currentPhase = 'RAMP_UP'; // Reset to ramp-up phase
    
    if (tpsChart) {
        tpsChart.data.labels = [];
        tpsChart.data.datasets[0].data = [];
        tpsChart.data.datasets[0].pointBackgroundColor = [];
        tpsChart.data.datasets[0].pointBorderColor = [];
        tpsChart.update();
    }

    if (latencyChart) {
        latencyChart.data.labels = [];
        latencyChart.data.datasets.forEach(dataset => {
            dataset.data = [];
            dataset.pointBackgroundColor = [];
            dataset.pointBorderColor = [];
        });
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
