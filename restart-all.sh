#!/bin/bash

echo "🛑 Stopping all processes..."
pkill -9 -f gradle
pkill -9 -f vajraedge
sleep 2

echo "🧹 Cleaning gradle daemons..."
cd /Users/santhoshkuppusamy/IdeaProjects/vajraedge
./gradlew --stop

echo "🔨 Building project..."
./gradlew clean build -x test
if [ $? -ne 0 ]; then
    echo "❌ Build failed!"
    exit 1
fi

echo "🚀 Starting controller..."
nohup ./gradlew :vajraedge-core:bootRun > /tmp/controller.log 2>&1 &
CONTROLLER_PID=$!
echo "Controller started with PID: $CONTROLLER_PID"

echo "⏳ Waiting for controller to start (20 seconds)..."
sleep 20

echo "🏥 Checking controller health..."
if curl -s http://127.0.0.1:8080/actuator/health | grep -q "UP"; then
    echo "✅ Controller is UP and healthy!"
    echo "🌐 Dashboard available at: http://127.0.0.1:8080"
else
    echo "❌ Controller failed to start. Check logs at /tmp/controller.log"
    tail -50 /tmp/controller.log
    exit 1
fi

echo ""
echo "✅ All services started successfully!"
echo "📊 Open dashboard: http://127.0.0.1:8080"
echo "📝 Controller logs: tail -f /tmp/controller.log"
