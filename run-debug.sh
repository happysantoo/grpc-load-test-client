#!/bin/bash

# Script to run VajraEdge with enhanced logging for debugging metrics

echo "======================================"
echo "Starting VajraEdge with DEBUG logging"
echo "======================================"
echo ""
echo "Watch for these log messages:"
echo "  - 'Recorded result for task' (every 100 tasks)"
echo "  - 'Broadcasted metrics for test' (every 500ms)"
echo "  - 'Calculated percentiles from X latency samples'"
echo ""
echo "Press Ctrl+C to stop"
echo ""

./gradlew bootRun --quiet --console=plain 2>&1 | grep -E "(Recorded result|Broadcasted metrics|Calculated percentiles|Starting test|MetricsCollector|Test started)"
