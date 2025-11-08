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
  [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
  [![JitPack](https://jitpack.io/v/happysantoo/vajraedge.svg)](https://jitpack.io/#happysantoo/vajraedge)
  
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
- 🔍 **Pre-Flight Validation**: Automatic system health checks before test execution
- 🔧 **Modular Architecture**: Separate SDK, Core, Worker, and Plugins modules
- 📦 **Lightweight SDK**: 9KB JAR with zero dependencies for custom plugins

## 📦 Using the SDK

Add VajraEdge SDK to your project to build custom task plugins or workers:

### Gradle
```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.happysantoo.vajraedge:vajraedge-sdk:0.9.0'
}
```

### Maven
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.happysantoo.vajraedge</groupId>
        <artifactId>vajraedge-sdk</artifactId>
        <version>0.9.0</version>
    </dependency>
</dependencies>
```

**Latest Version:** [![JitPack](https://jitpack.io/v/happysantoo/vajraedge.svg)](https://jitpack.io/#happysantoo/vajraedge)

See [Building Custom Workers](#building-custom-workers) section below for usage examples.

## 🏗️ Architecture

VajraEdge uses a modular architecture designed for extensibility and distributed testing:

```
vajraedge/
├── vajraedge-sdk/          # Core SDK (9KB, zero dependencies)
│   └── Task interfaces, annotations, metadata
├── vajraedge-core/         # Main controller application (Spring Boot)
│   └── REST API, WebSocket, metrics, validation
├── vajraedge-worker/       # Worker template for distributed testing
│   └── Task executor, gRPC client, metrics reporter
└── vajraedge-plugins/      # Example plugin implementations
    └── HTTP, gRPC, Database task examples
```

### Module Details

**vajraedge-sdk** (9KB JAR)
- Pure Java 21, zero external dependencies
- Core interfaces: `Task`, `TaskPlugin`, `TaskResult`
- Annotations: `@VajraTask`, `@TaskParameter`
- Use this to build custom plugins or workers

**vajraedge-core** (46MB JAR with Spring Boot)
- Main controller application with web dashboard
- REST API for test management
- Real-time metrics via WebSocket
- Pre-flight validation
- Plugin discovery and registration

**vajraedge-worker** (16KB JAR)
- Template for building custom workers
- Virtual thread task executor (10K+ concurrent tasks)
- gRPC client for controller communication
- Configurable via CLI or environment variables
- Deploy standalone or in containers

**vajraedge-plugins** (17KB JAR)
- Example implementations: HTTP GET/POST, Sleep
- Reference implementations: gRPC, PostgreSQL
- Shows best practices for plugin development

### Building Custom Workers

1. Create new Gradle project
2. Add SDK dependency (via JitPack):
```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.happysantoo.vajraedge:vajraedge-sdk:0.9.0'
}
```

3. Implement your custom plugins:
```java
@VajraTask(name = "MY_TASK", category = "CUSTOM")
public class MyCustomTask implements TaskPlugin {
    @Override
    public TaskResult execute() throws Exception {
        // Your task logic here
    }
}
```

4. Use worker template or integrate with controller

See [Worker README](vajraedge-worker/README.md) and [Plugin README](vajraedge-plugins/README.md) for details.

## 🔍 Pre-Flight Validation

VajraEdge includes intelligent pre-flight validation that automatically checks your system before running tests. This prevents test failures and provides actionable feedback.

### What Gets Validated?

**Service Health Check** ✅
- Verifies Spring Boot application is fully started
- Ensures WebSocket endpoint is available
- Confirms all required services are healthy

**Configuration Check** ⚙️
- Validates test parameters are within safe limits
- Maximum concurrency: 50,000 tasks
- Maximum TPS: 100,000 requests/second
- Test duration: 1 second to 24 hours
- Checks for reasonable ramp-up duration relative to test length

**Resource Check** 💾
- Monitors available system memory
- Warns if less than 500MB free heap space
- Suggests increasing JVM memory if needed

**Network Check** 🌐
- For HTTP tasks: validates URL format
- Tests connectivity to target endpoint
- Warns if endpoint is unreachable or slow

### Validation Results

**PASS** ✅ - All checks passed, test will start automatically
**WARN** ⚠️ - Minor issues detected, you can proceed with confirmation
**FAIL** ❌ - Critical issues found, test is blocked until fixed

### Using Validation in the UI

1. Configure your test parameters in the dashboard
2. Click "Start Test"
3. Validation runs automatically (progress spinner shown)
4. Review the validation results panel:
   - Overall status with color-coded alert
   - Individual check results with expandable details
   - Action buttons based on status

**If validation passes**: Test starts automatically
**If warnings detected**: Review warnings and click "Proceed Anyway" if acceptable
**If validation fails**: Fix the issues and try again

### Validation API

You can also run validation independently via REST API:

```bash
curl -X POST http://localhost:8080/api/validation \
  -H "Content-Type: application/json" \
  -d '{
    "targetTps": 1000,
    "maxConcurrency": 100,
    "testDurationSeconds": 60,
    "rampUpDurationSeconds": 10,
    "taskType": "SLEEP",
    "taskParameter": 100
  }'
```

**Response** (status=PASS):
```json
{
  "status": "PASS",
  "canProceed": true,
  "summary": "All validation checks passed successfully",
  "checkResults": [
    {
      "checkName": "Service Health Check",
      "status": "PASS",
      "message": "All services are healthy and ready",
      "details": ["Spring Boot application: HEALTHY", "WebSocket endpoint: AVAILABLE"],
      "durationMs": 45
    },
    {
      "checkName": "Configuration Check",
      "status": "PASS",
      "message": "Test configuration is valid",
      "details": ["Concurrency: 100 (within limit of 50000)", "TPS: 1000 (within limit of 100000)"],
      "durationMs": 12
    }
  ]
}
```

**Response** (status=WARN):
```json
{
  "status": "WARN",
  "canProceed": true,
  "summary": "Validation passed with warnings. Review before proceeding.",
  "checkResults": [
    {
      "checkName": "Resource Check",
      "status": "WARN",
      "message": "Available heap memory is below recommended threshold",
      "details": ["Free heap: 350MB", "Recommended: 500MB+", "Consider increasing JVM memory"],
      "durationMs": 8
    }
  ]
}
```

**Response** (status=FAIL):
```json
{
  "status": "FAIL",
  "canProceed": false,
  "summary": "Validation failed. Fix critical issues before proceeding.",
  "checkResults": [
    {
      "checkName": "Configuration Check",
      "status": "FAIL",
      "message": "Test configuration exceeds system limits",
      "details": ["Max concurrency 100000 exceeds limit of 50000", "Reduce maxConcurrency parameter"],
      "durationMs": 5
    }
  ]
}
```

### Validation Limits

| Parameter | Minimum | Maximum | Recommendation |
|-----------|---------|---------|----------------|
| Concurrency | 1 | 50,000 | Start with 10-100 |
| Target TPS | 1 | 100,000 | Depends on task latency |
| Test Duration | 1 second | 24 hours | 60-300 seconds typical |
| Ramp-Up Duration | 0 seconds | Test duration | 10-20% of test duration |
| Available Heap | 500MB+ | - | 1GB+ recommended |

### Adding Custom Validation Checks

You can add your own validation checks by implementing the `ValidationCheck` interface:

```java
@Component
public class CustomCheck implements ValidationCheck {
    
    @Override
    public CheckResult validate(ValidationContext context) {
        // Your validation logic here
        TestConfigRequest config = context.getConfig();
        
        if (/* some condition */) {
            return CheckResult.fail(
                "Custom Check",
                "Check failed: reason",
                List.of("Detail 1", "Detail 2")
            );
        }
        
        return CheckResult.pass("Custom Check", "Check passed");
    }
}
```

The framework will automatically discover and run your check.

## 🎭 Test Suites

Test Suites enable complex, multi-scenario performance testing with support for sequential/parallel execution, task mixes, and data correlation.

### Overview

A **Test Suite** contains multiple **Test Scenarios** that can execute:
- **Sequentially**: One after another (setup → test → teardown)
- **In Parallel**: All scenarios run simultaneously

### Key Capabilities

**Task Mix (Weighted Distribution)**
```json
{
  "taskMix": {
    "weights": {
      "HTTP_GET": 70,
      "HTTP_POST": 20,
      "HTTP_DELETE": 10
    }
  }
}
```
Simulates realistic load patterns where different operations occur with different frequencies.

**Data Correlation**
```java
// Scenario 1: Create users
context.addToPool("userIds", "user-001");

// Scenario 2: Use those users
String userId = context.getFromPool("userIds");
```
Share data between scenarios for complex testing flows (e.g., create → read → update → delete).

### API Endpoints

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/api/suites/start` | Start a new test suite |
| GET | `/api/suites/{suiteId}/status` | Get real-time status |
| GET | `/api/suites/{suiteId}/results` | Get final results |
| DELETE | `/api/suites/{suiteId}/stop` | Stop running suite |

### Example: Sequential User Journey

```json
POST /api/suites/start
{
  "suiteId": "user-journey-suite",
  "name": "Complete User Journey",
  "executionMode": "SEQUENTIAL",
  "useCorrelation": true,
  "scenarios": [
    {
      "scenarioId": "create-users",
      "name": "User Registration",
      "config": {
        "taskType": "HTTP_POST",
        "taskParameter": "https://api.example.com/users",
        "maxConcurrency": 10,
        "testDurationSeconds": 30
      }
    },
    {
      "scenarioId": "user-login",
      "name": "User Authentication",
      "config": {
        "taskType": "HTTP_POST",
        "taskParameter": "https://api.example.com/login",
        "maxConcurrency": 50,
        "testDurationSeconds": 60
      }
    }
  ]
}
```

### Example: Parallel Load with Task Mix

```json
POST /api/suites/start
{
  "suiteId": "mixed-load",
  "name": "Realistic E-Commerce Load",
  "executionMode": "PARALLEL",
  "scenarios": [
    {
      "scenarioId": "read-heavy",
      "name": "Browse-Heavy Users",
      "config": {
        "taskType": "HTTP_GET",
        "maxConcurrency": 100,
        "testDurationSeconds": 300
      },
      "taskMix": {
        "weights": {
          "LIST_PRODUCTS": 70,
          "VIEW_PRODUCT": 20,
          "SEARCH": 10
        }
      }
    },
    {
      "scenarioId": "write-heavy",
      "name": "Purchase-Heavy Users",
      "config": {
        "taskType": "HTTP_POST",
        "maxConcurrency": 20,
        "testDurationSeconds": 300
      },
      "taskMix": {
        "weights": {
          "ADD_TO_CART": 50,
          "CHECKOUT": 30,
          "UPDATE_CART": 20
        }
      }
    }
  ]
}
```

**Result**: Simultaneous execution of browse-heavy and purchase-heavy patterns, mimicking real e-commerce traffic.

### Suite Status Response

```json
GET /api/suites/{suiteId}/status
{
  "suiteId": "user-journey-suite",
  "status": "RUNNING",
  "executionMode": "SEQUENTIAL",
  "totalScenarios": 2,
  "completedScenarios": 1,
  "successfulScenarios": 1,
  "progress": 50.0,
  "scenarios": [
    {
      "scenarioId": "create-users",
      "status": "COMPLETED",
      "durationMillis": 30542,
      "metrics": {
        "totalRequests": 1250,
        "successfulRequests": 1248,
        "currentTps": 41.2
      }
    },
    {
      "scenarioId": "user-login",
      "status": "RUNNING"
    }
  ]
}
```

For detailed documentation, see [TASK_11_SUMMARY.md](documents/TASK_11_SUMMARY.md).

## 🏃 Quick Start

### Prerequisites
- Java 21 or higher
- Gradle 8.5+ (wrapper included)

### Run the Controller Application

```bash
# Build all modules (SDK, Core, Worker, Plugins)
./gradlew build

# Start the controller with web dashboard
./gradlew :vajraedge-core:bootRun

# Access the dashboard
open http://localhost:8080
```

That's it! The dashboard will open in your browser.

### Build Individual Modules

```bash
# Build just the SDK
./gradlew :vajraedge-sdk:build

# Build worker template
./gradlew :vajraedge-worker:build

# Build plugins
./gradlew :vajraedge-plugins:build

# List all modules
./gradlew projects
```

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

### Run Pre-Flight Validation (Recommended First)
```bash
curl -X POST http://localhost:8080/api/validation \
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

**Response**:
```json
{
  "status": "PASS",
  "canProceed": true,
  "summary": "All validation checks passed successfully",
  "checkResults": [...]
}
```

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

**Note**: Pre-flight validation runs automatically before test start. Tests with FAIL status are blocked.

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
│   ├── TestController.java
│   └── ValidationController.java
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
├── validation/                    # Pre-flight validation
│   ├── CheckResult.java          # Individual check result
│   ├── ValidationCheck.java      # Validation check interface
│   ├── ValidationContext.java    # Validation context wrapper
│   ├── ValidationResult.java     # Overall validation result
│   ├── PreFlightValidator.java   # Validation orchestrator
│   └── checks/                   # Validation check implementations
│       ├── ConfigurationCheck.java
│       ├── NetworkCheck.java
│       ├── ResourceCheck.java
│       └── ServiceHealthCheck.java
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

Contributions are welcome! VajraEdge is open source under the Apache 2.0 license.

To add a new task type:
1. Implement the `Task` interface
2. Add factory case in `TestExecutionService`
3. Update UI dropdown in `index.html`
4. Test via the dashboard

## 📝 License

Copyright 2025 VajraEdge Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

## 🙋 Support

For issues or questions:
- Check the documentation in the `documents/` folder
- Open an issue on GitHub
- Review the examples in `vajraedge-plugins/`

---

**Built with ❤️ by VajraEdge using Java 21 and Spring Boot 3.5.7**

**Access your dashboard**: http://localhost:8080
