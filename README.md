<div align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="logo_dark.png">
    <source media="(prefers-color-scheme: light)" srcset="logo_bright.png">
    <img src="logo_bright.png" alt="VajraEdge Logo" width="400"/>
  </picture>
  
  
  ### High-Performance Load Testing Framework
  
  [![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/projects/jdk/21/)
  [![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.7-brightgreen.svg)](https://spring.io/projects/spring-boot)
  [![Virtual Threads](https://img.shields.io/badge/Virtual%20Threads-Enabled-blue.svg)](https://openjdk.org/jeps/444)
  
  **Modern • Production-Ready • Real-Time Metrics**
  
</div>

---

## 🚀 What is VajraEdge?

VajraEdge is a modern, production-ready performance testing framework built with **Java 21**, **Spring Boot 3.5.7**, and **virtual threads**. Test any workload with a beautiful web dashboard showing real-time metrics, charts, and latency percentiles.

**VajraEdge** combines cutting-edge technology with simplicity, delivering enterprise-grade load testing capabilities with the ease of use you expect from modern tools.

## ✨ Key Features

- ⚡ **Virtual Threads**: Efficient concurrency using Java 21's Project Loom
- 📊 **Real-Time Dashboard**: Live charts and metrics updating every 500ms
- 🎯 **Smart Scaling**: Configurable ramp-up, sustaining phase, and target TPS
- 🔌 **WebSocket Updates**: No polling, instant metrics via STOMP over WebSocket
- 🧩 **Simple Task Interface**: Benchmark anything by implementing one method
- 🎨 **Modern UI**: Bootstrap 5 + Chart.js with responsive design

## 🏃 Quick Start

### Prerequisites
- Java 21 or higher
- Gradle (wrapper included)

### Run the Application

```bash
# Build the project
./gradlew clean build

# Start the server
./gradlew bootRun

# Access the dashboard
open http://localhost:8080
```

That's it! The dashboard will open in your browser.

## 📖 Using the Dashboard

### 1. Configure Your Test

Fill in the test configuration form:
- **Target TPS**: Transactions per second (1-100,000)
- **Max Concurrency**: Maximum parallel tasks (1-50,000)
- **Test Duration**: How long to run (seconds)
- **Ramp-Up Duration**: Gradual increase period (seconds)
- **Task Type**: SLEEP or CPU
- **Task Parameter**: Sleep/CPU duration (milliseconds)

### 2. Start the Test

Click "Start Test" - you'll immediately see:
- Real-time TPS graph
- Latency percentiles chart (P50, P95, P99)
- Current metrics (active tasks, success rate, etc.)
- Detailed percentile table

### 3. Monitor in Real-Time

Watch as metrics update every 500ms:
- Green "Connected" badge shows WebSocket is active
- Charts scroll as new data arrives
- Percentile table updates with latest latencies

### 4. Stop When Done

Click "Stop Test" or wait for the test to complete automatically.

## 🎯 Example Test Scenarios

### Load Test (Sustained TPS)
```
Target TPS: 1000
Max Concurrency: 100
Duration: 60 seconds
Ramp-Up: 10 seconds
Task: SLEEP (100ms)
```

### Stress Test (High Concurrency)
```
Target TPS: 5000
Max Concurrency: 1000
Duration: 120 seconds
Ramp-Up: 20 seconds
Task: CPU (50ms)
```

### Spike Test (Quick Ramp)
```
Target TPS: 2000
Max Concurrency: 200
Duration: 30 seconds
Ramp-Up: 2 seconds
Task: SLEEP (50ms)
```

## 🧪 REST API

### Start a Test
```bash
curl -X POST http://localhost:8080/api/tests \
  -H "Content-Type: application/json" \
  -d '{
    "targetTps": 100,
    "maxConcurrency": 50,
    "testDurationSeconds": 60,
    "rampUpDurationSeconds": 10,
    "taskType": "SLEEP",
    "taskParameter": 100
  }'
```

### Get Test Status
```bash
curl http://localhost:8080/api/tests/{testId}
```

### Stop a Test
```bash
curl -X DELETE http://localhost:8080/api/tests/{testId}
```

### List Active Tests
```bash
curl http://localhost:8080/api/tests
```

## 🔧 Extending the Framework

### Add Your Own Task Type

Implement the `Task` interface:

```java
public class HttpTask implements Task {
    private final String url;
    
    public HttpTask(String url) {
        this.url = url;
    }
    
    @Override
    public TaskResult execute() throws Exception {
        long start = System.nanoTime();
        try {
            // Your HTTP call here
            HttpResponse response = httpClient.send(...);
            long latency = System.nanoTime() - start;
            
            return new SimpleTaskResult(
                response.statusCode() == 200,
                latency,
                null
            );
        } catch (Exception e) {
            long latency = System.nanoTime() - start;
            return new SimpleTaskResult(false, latency, e.getMessage());
        }
    }
}
```

### Register in TestExecutionService

Add to the `createTaskFactory()` method:

```java
case "HTTP" -> taskId -> new HttpTask("https://api.example.com/endpoint");
```

Now you can test HTTP endpoints from the dashboard!

## 📊 Metrics Explained

### Real-Time Metrics
- **Current TPS**: Actual transactions per second right now
- **Active Tasks**: Tasks currently executing
- **Total Requests**: All requests submitted
- **Success Rate**: Percentage of successful requests

### Latency Percentiles
- **P50 (Median)**: 50% of requests are faster than this
- **P95**: 95% of requests are faster than this
- **P99**: Only 1% of requests are slower than this
- **P99.9**: Extremely high percentile for outliers

## 🏗️ Architecture

```
┌─────────────────────────────────────────┐
│         Web Dashboard (Browser)         │
│  Bootstrap + Chart.js + WebSocket       │
└───────────┬─────────────────────────────┘
            │ WebSocket (STOMP)
            │ REST API
┌───────────▼─────────────────────────────┐
│      Spring Boot Application            │
│  ┌──────────────────────────────────┐   │
│  │ Controllers (REST endpoints)     │   │
│  └──────────┬───────────────────────┘   │
│  ┌──────────▼───────────────────────┐   │
│  │ Services (Business logic)        │   │
│  └──────────┬───────────────────────┘   │
│  ┌──────────▼───────────────────────┐   │
│  │ PerformanceTestRunner            │   │
│  │  - RateController (TPS control)  │   │
│  │  - MetricsCollector (stats)      │   │
│  └──────────┬───────────────────────┘   │
│  ┌──────────▼───────────────────────┐   │
│  │ VirtualThreadTaskExecutor        │   │
│  │  - Virtual threads (Java 21)     │   │
│  │  - Concurrency limiting          │   │
│  └──────────┬───────────────────────┘   │
│  ┌──────────▼───────────────────────┐   │
│  │ Tasks (Your benchmark logic)     │   │
│  └──────────────────────────────────┘   │
└─────────────────────────────────────────┘
```

## 📁 Project Structure

```
src/main/java/com/vajraedge/perftest/
├── Application.java                 # Spring Boot main class
├── config/                          # Configuration beans
│   ├── TestConfiguration.java
│   ├── WebConfig.java
│   └── WebSocketConfig.java
├── controller/                      # REST endpoints
│   ├── HealthController.java
│   └── TestController.java
├── core/                           # Framework core
│   ├── Task.java                   # Task interface
│   ├── TaskResult.java
│   ├── SimpleTaskResult.java
│   ├── TaskFactory.java
│   └── TaskExecutor.java
├── dto/                            # Data transfer objects
│   ├── TestConfigRequest.java
│   ├── TestStatusResponse.java
│   └── MetricsResponse.java
├── executor/                       # Task execution
│   └── VirtualThreadTaskExecutor.java
├── metrics/                        # Metrics collection
│   ├── MetricsCollector.java
│   ├── MetricsSnapshot.java
│   └── PercentileStats.java
├── rate/                          # Rate control
│   └── RateController.java
├── runner/                        # Test orchestration
│   ├── PerformanceTestRunner.java
│   └── TestResult.java
├── service/                       # Business logic
│   ├── TestExecutionService.java
│   └── MetricsService.java
└── websocket/                     # WebSocket handling
    └── MetricsWebSocketHandler.java

src/main/resources/
├── application.properties         # App configuration
└── static/                        # Web dashboard
    ├── index.html
    ├── css/
    │   └── dashboard.css
    └── js/
        ├── dashboard.js
        ├── charts.js
        └── websocket.js
```

## 🛠️ Technologies

### Backend
- **Java 21** - Virtual threads, pattern matching
- **Spring Boot 3.5.7** - Framework
- **Spring WebSocket** - Real-time updates
- **Apache Commons Math** - Percentile calculations
- **Jackson** - JSON serialization

### Frontend
- **Bootstrap 5.3.3** - UI framework
- **Chart.js 4.4.7** - Graphs
- **SockJS 1.6.1** - WebSocket fallback
- **STOMP 2.3.4** - Messaging protocol
- **jQuery 3.7.1** - DOM manipulation

### Build
- **Gradle 8.x** - Build tool
- **Spring Boot Gradle Plugin** - Packaging

## 📚 Documentation

- [Phase 2 Summary](documents/PHASE_2_COMPLETION_SUMMARY.md) - REST API implementation
- [Phase 3 & 4 Summary](documents/PHASE_3_4_COMPLETION_SUMMARY.md) - WebSocket & UI
- [Wishlist Status](documents/WISHLIST_COMPLETION_STATUS.md) - Requirements tracking
- [Framework README](documents/FRAMEWORK_README.md) - Detailed architecture

## 🎓 Learn More

### Virtual Threads (Project Loom)
Java 21's virtual threads allow thousands of concurrent tasks without platform thread overhead. Perfect for I/O-bound performance testing.

### WebSocket vs Polling
The dashboard uses WebSocket for efficient real-time updates. No need to poll the server every second - metrics are pushed automatically every 500ms.

### Percentiles vs Averages
Average latency can be misleading. P95 and P99 percentiles show you the worst-case experience for your users.

## 🤝 Contributing

To add a new task type:
1. Implement the `Task` interface
2. Add factory case in `TestExecutionService`
3. Update UI dropdown in `index.html`
4. Test via the dashboard

## 📝 License

This is an example project for educational purposes.

## 🙋 Support

For issues or questions, check the documentation in the `documents/` folder.

---

**Built with ❤️ by VajraEdge using Java 21 and Spring Boot 3.5.7**

**Access your dashboard**: http://localhost:8080
