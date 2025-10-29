# Wishlist Items 7-9: Implementation Plan

**Date**: October 28, 2025  
**Author**: GitHub Copilot (Analysis for Santhosh Kuppusamy)  
**Status**: Planning Phase

---

## Executive Summary

This document provides a comprehensive plan of attack for implementing the remaining wishlist items (7, 8, 9) for VajraEdge. These items represent the evolution from a standalone load testing tool to a **production-grade framework** with validation, extensibility, and distributed capabilities.

### Current State Assessment

**✅ Completed (Items 1-6)**:
- Java 21 with virtual threads ✅
- Systematic scaling (warmup, ramp, sustain) ✅  
- Real-time UI with graphs and metrics ✅
- Spring Boot controller layer ✅
- Simple Task interface ✅

**🎯 Remaining (Items 7-9)**:
- **Item 7**: Pre-flight validation
- **Item 8**: SDK/Plugin architecture
- **Item 9**: Distributed testing architecture

---

## Item 7: Pre-Flight Validation Plan

### 📋 Requirement
> "Before initial run, we need a validation plan to make sure the services are functional."

### 🎯 Objective
Implement a comprehensive health check and validation system that runs **before** starting a load test to ensure:
1. Target service is reachable and responding
2. Test configuration is valid and safe
3. VajraEdge has sufficient resources
4. Network connectivity is stable

### 🏗️ Architecture Design

#### Component Structure
```
com.vajraedge.perftest.validation/
├── PreFlightValidator.java          (Main orchestrator)
├── ValidationResult.java             (Result container)
├── ValidationContext.java            (Validation parameters)
├── checks/
│   ├── ServiceHealthCheck.java       (Target service validation)
│   ├── ConfigurationCheck.java       (Config sanity validation)
│   ├── ResourceCheck.java            (Memory/CPU check)
│   └── NetworkCheck.java             (Connectivity validation)
└── report/
    └── ValidationReport.java         (HTML/JSON report)
```

#### Validation Flow
```
User clicks "Start Test"
    ↓
PreFlightValidator.validate()
    ↓
├─→ ServiceHealthCheck
│   ├─→ Ping target endpoint
│   ├─→ Verify response code (200)
│   ├─→ Measure baseline latency
│   └─→ Check response format
│
├─→ ConfigurationCheck  
│   ├─→ Validate TPS limits
│   ├─→ Validate duration ranges
│   ├─→ Check concurrency limits
│   └─→ Verify task parameters
│
├─→ ResourceCheck
│   ├─→ Check available memory
│   ├─→ Check available threads
│   ├─→ Verify disk space (for logs)
│   └─→ Check CPU availability
│
└─→ NetworkCheck
    ├─→ DNS resolution
    ├─→ TCP connection test
    └─→ Firewall/proxy detection
    ↓
ValidationResult
    ├─→ PASS → Start test
    ├─→ WARN → Show warnings, allow override
    └─→ FAIL → Block test, show errors
```

### 📝 Implementation Details

#### 1. **PreFlightValidator** (Main Orchestrator)
```java
@Service
public class PreFlightValidator {
    
    private final List<ValidationCheck> checks;
    
    public ValidationResult validate(ValidationContext context) {
        ValidationResult.Builder builder = ValidationResult.builder();
        
        for (ValidationCheck check : checks) {
            CheckResult result = check.execute(context);
            builder.addCheckResult(result);
            
            if (result.isCritical() && !result.isPassed()) {
                builder.criticalFailure();
                break; // Stop on critical failure
            }
        }
        
        return builder.build();
    }
}
```

#### 2. **ServiceHealthCheck** (Target Service Validation)
```java
@Component
public class ServiceHealthCheck implements ValidationCheck {
    
    @Override
    public CheckResult execute(ValidationContext context) {
        String taskType = context.getTaskType();
        Object taskParameter = context.getTaskParameter();
        
        if ("HTTP".equalsIgnoreCase(taskType)) {
            return validateHttpEndpoint(taskParameter.toString());
        }
        
        // Other task types might not need health checks
        return CheckResult.skip("Not applicable for task type: " + taskType);
    }
    
    private CheckResult validateHttpEndpoint(String url) {
        try {
            // Send a single test request
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
                
            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            // Check status code
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return CheckResult.pass("Service is healthy")
                    .withMetadata("statusCode", response.statusCode())
                    .withMetadata("responseTime", "< 5s");
            } else {
                return CheckResult.warn("Service returned non-2xx status")
                    .withMetadata("statusCode", response.statusCode());
            }
            
        } catch (Exception e) {
            return CheckResult.fail("Service is unreachable: " + e.getMessage())
                .critical() // This is a critical failure
                .withRecommendation("Check URL, network, and service availability");
        }
    }
}
```

#### 3. **ConfigurationCheck** (Config Validation)
```java
@Component
public class ConfigurationCheck implements ValidationCheck {
    
    @Override
    public CheckResult execute(ValidationContext context) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        // Check TPS vs Concurrency ratio
        int maxConcurrency = context.getMaxConcurrency();
        int targetTps = context.getTargetTps();
        
        if (maxConcurrency > targetTps * 10) {
            warnings.add("Concurrency is very high relative to TPS. " +
                "Consider reducing concurrency for efficiency.");
        }
        
        // Check duration
        long durationSeconds = context.getDurationSeconds();
        if (durationSeconds > 3600) { // 1 hour
            warnings.add("Test duration exceeds 1 hour. " +
                "Consider shorter tests for initial runs.");
        }
        
        // Check for realistic TPS
        if (targetTps > 100000) {
            errors.add("TPS exceeds realistic limits (max: 100,000). " +
                "This may cause resource exhaustion.");
        }
        
        if (!errors.isEmpty()) {
            return CheckResult.fail("Configuration validation failed")
                .withErrors(errors);
        } else if (!warnings.isEmpty()) {
            return CheckResult.warn("Configuration has warnings")
                .withWarnings(warnings);
        } else {
            return CheckResult.pass("Configuration is valid");
        }
    }
}
```

#### 4. **ResourceCheck** (System Resources)
```java
@Component
public class ResourceCheck implements ValidationCheck {
    
    private final Runtime runtime = Runtime.getRuntime();
    
    @Override
    public CheckResult execute(ValidationContext context) {
        // Check available memory
        long maxMemory = runtime.maxMemory();
        long freeMemory = runtime.freeMemory();
        long totalMemory = runtime.totalMemory();
        long availableMemory = maxMemory - (totalMemory - freeMemory);
        
        long requiredMemory = estimateMemoryRequirement(context);
        
        if (availableMemory < requiredMemory) {
            return CheckResult.fail(
                String.format("Insufficient memory. Available: %d MB, Required: %d MB",
                    availableMemory / 1_000_000,
                    requiredMemory / 1_000_000))
                .withRecommendation("Increase JVM heap size with -Xmx flag");
        }
        
        // Check virtual thread support (Java 21+)
        String javaVersion = System.getProperty("java.version");
        if (!javaVersion.startsWith("21")) {
            return CheckResult.warn("Java 21 recommended for virtual threads. " +
                "Current version: " + javaVersion);
        }
        
        return CheckResult.pass("Resources are sufficient")
            .withMetadata("availableMemoryMB", availableMemory / 1_000_000)
            .withMetadata("javaVersion", javaVersion);
    }
    
    private long estimateMemoryRequirement(ValidationContext context) {
        // Rough estimate: 1MB per concurrent virtual user + overhead
        return context.getMaxConcurrency() * 1_000_000L + 100_000_000L;
    }
}
```

### 🎨 UI Integration

#### Dashboard Changes
```html
<!-- Validation Status Panel -->
<div class="card mb-3" id="validationPanel" style="display: none;">
    <div class="card-header bg-info text-white">
        <h5>Pre-Flight Validation</h5>
    </div>
    <div class="card-body">
        <div class="validation-checks">
            <!-- Populated dynamically -->
        </div>
        <div class="validation-actions mt-3">
            <button id="proceedBtn" class="btn btn-success">Proceed</button>
            <button id="abortBtn" class="btn btn-danger">Abort</button>
        </div>
    </div>
</div>
```

#### JavaScript Flow
```javascript
async function startTestWithValidation(config) {
    // Show validation panel
    showValidationPanel();
    
    // Call validation endpoint
    const validationResult = await fetch('/api/validation', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(config)
    }).then(r => r.json());
    
    // Display results
    displayValidationResults(validationResult);
    
    // Handle user decision
    if (validationResult.status === 'PASS') {
        // Auto-proceed
        await startTest(config);
    } else if (validationResult.status === 'WARN') {
        // Show warnings, wait for user confirmation
        await waitForUserDecision();
    } else {
        // Block test
        showErrorMessage(validationResult.errors);
    }
}
```

### 🚀 API Endpoints

```java
@RestController
@RequestMapping("/api/validation")
public class ValidationController {
    
    @Autowired
    private PreFlightValidator validator;
    
    @PostMapping
    public ResponseEntity<ValidationResult> validate(
            @RequestBody @Valid TestConfigRequest config) {
        
        ValidationContext context = ValidationContext.from(config);
        ValidationResult result = validator.validate(context);
        
        return ResponseEntity.ok(result);
    }
}
```

### ⏱️ Effort Estimation

| Task | Effort | Priority |
|------|--------|----------|
| Core validator framework | 4 hours | HIGH |
| ServiceHealthCheck | 3 hours | HIGH |
| ConfigurationCheck | 2 hours | MEDIUM |
| ResourceCheck | 2 hours | MEDIUM |
| NetworkCheck | 2 hours | LOW |
| UI integration | 3 hours | HIGH |
| Tests | 4 hours | HIGH |
| **Total** | **20 hours** | |

### ✅ Acceptance Criteria

1. ✅ Validation runs automatically before test starts
2. ✅ HTTP endpoints are validated with actual request
3. ✅ Configuration limits are checked and warnings shown
4. ✅ Critical failures block test execution
5. ✅ Warnings allow override with confirmation
6. ✅ Validation results are displayed in UI
7. ✅ Validation results are logged
8. ✅ Tests cover all validation scenarios

---

## Item 8: SDK/Plugin Architecture

### 📋 Requirement
> "To qualify as a framework, there needs to be development of a thin SDK where someone can pull the SDK and create tasks that can be registered by the presence of them in the classpath (call it a lib extension)."

### 🎯 Objective
Transform VajraEdge from an application to a **framework** with:
1. Thin SDK for task development
2. Classpath-based auto-discovery
3. Plugin registration without code changes
4. Clear extension API

### 🏗️ Architecture Design

#### Module Structure
```
vajraedge-sdk/                    (Thin SDK - minimal dependencies)
├── core/
│   ├── Task.java                 (Already exists)
│   ├── TaskResult.java           (Already exists)
│   ├── TaskMetadata.java         (NEW - plugin metadata)
│   └── TaskPlugin.java           (NEW - plugin marker interface)
│
├── annotations/
│   ├── @VajraTask.java           (NEW - marks task implementations)
│   ├── @TaskParameter.java       (NEW - parameter metadata)
│   └── @TaskMetrics.java         (NEW - custom metrics)
│
└── spi/
    └── TaskProvider.java         (NEW - SPI for discovery)

vajraedge-core/                   (Main application)
├── plugin/
│   ├── PluginRegistry.java       (NEW - manages plugins)
│   ├── PluginScanner.java        (NEW - classpath scanner)
│   └── PluginLoader.java         (NEW - dynamic loading)
│
└── (existing packages)

vajraedge-plugins/                (Example plugins)
├── http-plugin/
├── grpc-plugin/
├── database-plugin/
└── kafka-plugin/
```

### 📝 Implementation Details

#### 1. **Task Plugin Marker**
```java
// vajraedge-sdk/src/main/java/com/vajraedge/sdk/TaskPlugin.java
package com.vajraedge.sdk;

/**
 * Marker interface for VajraEdge task plugins.
 * Implement this along with Task to be auto-discovered.
 */
public interface TaskPlugin extends Task {
    
    /**
     * Get metadata about this task type.
     */
    TaskMetadata getMetadata();
    
    /**
     * Validate parameters before execution.
     * @throws IllegalArgumentException if parameters are invalid
     */
    default void validateParameters(Map<String, Object> parameters) {
        // Override if validation needed
    }
}
```

#### 2. **Task Metadata**
```java
// vajraedge-sdk/src/main/java/com/vajraedge/sdk/TaskMetadata.java
package com.vajraedge.sdk;

public record TaskMetadata(
    String name,              // e.g., "HTTP_GET"
    String displayName,       // e.g., "HTTP GET Request"
    String description,       // e.g., "Performs HTTP GET request"
    String category,          // e.g., "HTTP", "DATABASE", "MESSAGING"
    List<ParameterDef> parameters,
    Map<String, String> metadata
) {
    
    public record ParameterDef(
        String name,
        String type,          // "STRING", "INTEGER", "BOOLEAN", etc.
        boolean required,
        Object defaultValue,
        String description
    ) {}
}
```

#### 3. **Annotation-Based Configuration**
```java
// vajraedge-sdk/src/main/java/com/vajraedge/sdk/annotations/VajraTask.java
package com.vajraedge.sdk.annotations;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface VajraTask {
    String name();
    String displayName() default "";
    String description() default "";
    String category() default "CUSTOM";
}
```

#### 4. **Example Plugin Implementation**
```java
// vajraedge-plugins/http-plugin/src/main/java/com/vajraedge/plugins/http/HttpGetTask.java
package com.vajraedge.plugins.http;

import com.vajraedge.sdk.*;
import com.vajraedge.sdk.annotations.*;

@VajraTask(
    name = "HTTP_GET",
    displayName = "HTTP GET Request",
    description = "Performs an HTTP GET request to the specified URL",
    category = "HTTP"
)
public class HttpGetTask implements TaskPlugin {
    
    private final String url;
    private final HttpClient httpClient;
    
    public HttpGetTask(Map<String, Object> parameters) {
        this.url = (String) parameters.get("url");
        this.httpClient = HttpClient.newHttpClient();
        validateParameters(parameters);
    }
    
    @Override
    public TaskResult execute() throws Exception {
        long start = System.nanoTime();
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();
            
        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());
            
        long latency = System.nanoTime() - start;
        
        return SimpleTaskResult.success(
            System.currentTimeMillis(),
            latency,
            response.body().length()
        );
    }
    
    @Override
    public TaskMetadata getMetadata() {
        return new TaskMetadata(
            "HTTP_GET",
            "HTTP GET Request",
            "Performs an HTTP GET request",
            "HTTP",
            List.of(
                new TaskMetadata.ParameterDef(
                    "url",
                    "STRING",
                    true,
                    "http://localhost:8081/api/products",
                    "Target URL for the request"
                )
            ),
            Map.of("version", "1.0.0")
        );
    }
    
    @Override
    public void validateParameters(Map<String, Object> parameters) {
        if (!parameters.containsKey("url")) {
            throw new IllegalArgumentException("URL parameter is required");
        }
        
        String url = (String) parameters.get("url");
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("URL must start with http:// or https://");
        }
    }
}
```

#### 5. **Service Provider Interface (SPI)**
```java
// vajraedge-sdk/src/main/java/com/vajraedge/sdk/spi/TaskProvider.java
package com.vajraedge.sdk.spi;

public interface TaskProvider {
    
    /**
     * Get all task types provided by this plugin.
     */
    List<Class<? extends TaskPlugin>> getTaskTypes();
    
    /**
     * Create a task instance from parameters.
     */
    TaskPlugin createTask(String taskType, Map<String, Object> parameters);
}
```

```java
// vajraedge-plugins/http-plugin/src/main/java/com/vajraedge/plugins/http/HttpTaskProvider.java
package com.vajraedge.plugins.http;

public class HttpTaskProvider implements TaskProvider {
    
    @Override
    public List<Class<? extends TaskPlugin>> getTaskTypes() {
        return List.of(
            HttpGetTask.class,
            HttpPostTask.class,
            HttpPutTask.class
        );
    }
    
    @Override
    public TaskPlugin createTask(String taskType, Map<String, Object> parameters) {
        return switch (taskType) {
            case "HTTP_GET" -> new HttpGetTask(parameters);
            case "HTTP_POST" -> new HttpPostTask(parameters);
            case "HTTP_PUT" -> new HttpPutTask(parameters);
            default -> throw new IllegalArgumentException("Unknown task type: " + taskType);
        };
    }
}
```

```
// vajraedge-plugins/http-plugin/src/main/resources/META-INF/services/com.vajraedge.sdk.spi.TaskProvider
com.vajraedge.plugins.http.HttpTaskProvider
```

#### 6. **Plugin Registry & Scanner**
```java
// vajraedge-core/src/main/java/com/vajraedge/perftest/plugin/PluginRegistry.java
package com.vajraedge.perftest.plugin;

@Service
public class PluginRegistry {
    
    private final Map<String, TaskProvider> providers = new ConcurrentHashMap<>();
    private final Map<String, TaskMetadata> metadata = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void discoverPlugins() {
        ServiceLoader<TaskProvider> loader = ServiceLoader.load(TaskProvider.class);
        
        for (TaskProvider provider : loader) {
            registerProvider(provider);
        }
        
        log.info("Discovered {} task providers with {} task types",
            providers.size(),
            metadata.size());
    }
    
    private void registerProvider(TaskProvider provider) {
        for (Class<? extends TaskPlugin> taskClass : provider.getTaskTypes()) {
            try {
                // Get metadata from annotation
                VajraTask annotation = taskClass.getAnnotation(VajraTask.class);
                if (annotation != null) {
                    String taskName = annotation.name();
                    providers.put(taskName, provider);
                    
                    // Get metadata from instance (for parameter info)
                    TaskPlugin instance = taskClass.getDeclaredConstructor().newInstance();
                    metadata.put(taskName, instance.getMetadata());
                    
                    log.info("Registered task: {} ({})", 
                        taskName, annotation.displayName());
                }
            } catch (Exception e) {
                log.warn("Failed to register task: {}", taskClass.getName(), e);
            }
        }
    }
    
    public TaskPlugin createTask(String taskType, Map<String, Object> parameters) {
        TaskProvider provider = providers.get(taskType);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown task type: " + taskType);
        }
        return provider.createTask(taskType, parameters);
    }
    
    public List<TaskMetadata> getAvailableTasks() {
        return new ArrayList<>(metadata.values());
    }
}
```

### 🎨 UI Integration

#### Dynamic Task Type Dropdown
```html
<select class="form-select" id="taskType" required>
    <!-- Populated dynamically from /api/tasks/types -->
</select>
```

```javascript
// Load available task types on page load
async function loadTaskTypes() {
    const response = await fetch('/api/tasks/types');
    const taskTypes = await response.json();
    
    const select = document.getElementById('taskType');
    taskTypes.forEach(task => {
        const option = document.createElement('option');
        option.value = task.name;
        option.textContent = task.displayName;
        option.dataset.parameters = JSON.stringify(task.parameters);
        select.appendChild(option);
    });
}
```

#### Dynamic Parameter Form
```javascript
// Update parameter fields based on selected task type
document.getElementById('taskType').addEventListener('change', function() {
    const option = this.selectedOptions[0];
    const parameters = JSON.parse(option.dataset.parameters);
    
    // Render parameter form
    const paramContainer = document.getElementById('taskParameters');
    paramContainer.innerHTML = '';
    
    parameters.forEach(param => {
        const formGroup = createParameterInput(param);
        paramContainer.appendChild(formGroup);
    });
});
```

### 🚀 API Endpoints

```java
@RestController
@RequestMapping("/api/tasks")
public class TaskTypeController {
    
    @Autowired
    private PluginRegistry pluginRegistry;
    
    @GetMapping("/types")
    public List<TaskMetadata> getAvailableTaskTypes() {
        return pluginRegistry.getAvailableTasks();
    }
}
```

### 📦 Maven/Gradle Setup

#### SDK Module (Published to Maven Central)
```gradle
// vajraedge-sdk/build.gradle
plugins {
    id 'java-library'
    id 'maven-publish'
}

group = 'com.vajraedge'
version = '1.0.0'

java {
    sourceCompatibility = '21'
    targetCompatibility = '21'
}

dependencies {
    // Zero dependencies for SDK!
}

publishing {
    publications {
        maven(MavenPublication) {
            from components.java
        }
    }
}
```

#### Plugin Development (Third-party)
```gradle
// Example: my-custom-plugin/build.gradle
plugins {
    id 'java'
}

dependencies {
    implementation 'com.vajraedge:vajraedge-sdk:1.0.0'
    
    // Plugin-specific dependencies
    implementation 'org.postgresql:postgresql:42.6.0'
}
```

### ⏱️ Effort Estimation

| Task | Effort | Priority |
|------|--------|----------|
| SDK module setup | 3 hours | HIGH |
| TaskPlugin interface | 2 hours | HIGH |
| TaskMetadata & annotations | 3 hours | MEDIUM |
| SPI implementation | 4 hours | HIGH |
| PluginRegistry | 4 hours | HIGH |
| UI dynamic loading | 3 hours | HIGH |
| Example plugins (HTTP, DB) | 6 hours | MEDIUM |
| Documentation | 4 hours | HIGH |
| Tests | 5 hours | HIGH |
| **Total** | **34 hours** | |

### ✅ Acceptance Criteria

1. ✅ SDK can be used independently (zero dependencies)
2. ✅ Plugins are discovered via SPI automatically
3. ✅ No code changes needed to add new task types
4. ✅ UI dynamically loads available tasks
5. ✅ Parameter forms are generated automatically
6. ✅ Plugin validation prevents bad plugins from crashing system
7. ✅ Documentation explains plugin development
8. ✅ Example plugins demonstrate patterns

---

## Item 9: Distributed Testing Architecture

### 📋 Requirement
> "Alternatively, we need to think about a distributed testing environment where a task can be distributed to a bunch of workers. Workers can be connected to a master via GRPC. Propose a design and architecture plan considering various design aspects."

### 🎯 Objective
Design and implement a **distributed load testing architecture** where:
1. Master node orchestrates the test
2. Worker nodes execute tasks
3. gRPC for communication
4. Horizontal scalability
5. Fault tolerance

### 🏗️ Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                      WEB DASHBOARD                          │
│                    (Browser - WebSocket)                    │
└──────────────────────┬──────────────────────────────────────┘
                       │ REST API + WebSocket
┌──────────────────────▼──────────────────────────────────────┐
│                     MASTER NODE                             │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  Spring Boot Application                               │ │
│  │  - TestOrchestrator                                    │ │
│  │  - WorkerManager                                       │ │
│  │  - MetricsAggregator                                   │ │
│  │  - WorkerHealthMonitor                                 │ │
│  └─────────────┬──────────────────────────────────────────┘ │
│                │ gRPC Server (Port 9090)                    │
└────────────────┼────────────────────────────────────────────┘
                 │
      ┌──────────┼──────────┐
      │          │          │
      │ gRPC     │ gRPC     │ gRPC
      │          │          │
┌─────▼───┐ ┌───▼────┐ ┌──▼─────┐
│ WORKER  │ │ WORKER │ │ WORKER │
│  NODE 1 │ │ NODE 2 │ │ NODE N │
│         │ │        │ │        │
│ - Task  │ │ - Task │ │ - Task │
│   Exec  │ │   Exec │ │   Exec │
│ - Local │ │ - Local│ │ - Local│
│   Metrics│ │   Metrics│ │   Metrics│
└─────────┘ └────────┘ └────────┘
```

### 📝 Key Design Decisions

#### 1. **Communication Protocol: gRPC**
**Why gRPC?**
- ✅ Binary protocol (faster than JSON/REST)
- ✅ HTTP/2 multiplexing
- ✅ Bidirectional streaming
- ✅ Strong typing with protobuf
- ✅ Built-in load balancing
- ✅ Connection pooling

#### 2. **Work Distribution Strategy**
**Options Evaluated**:
- **Round Robin**: Simple, but doesn't account for worker load
- **Least Connections**: Better, but requires state tracking
- **Weighted Round Robin**: Considers worker capacity
- **Dynamic Load Balancing**: Best, adjusts based on real-time metrics

**Chosen: Dynamic Load Balancing**
- Workers report capacity and current load
- Master assigns work based on available capacity
- Handles heterogeneous worker pool

#### 3. **Fault Tolerance**
**Failure Scenarios**:
- Worker crashes mid-test
- Network partition
- Master crashes
- Slow/unresponsive worker

**Solutions**:
- **Health Checks**: Periodic heartbeat (every 5s)
- **Task Reassignment**: Failed tasks redistributed
- **Worker Blacklisting**: Temporarily remove failing workers
- **Graceful Degradation**: Test continues with remaining workers
- **Master HA**: Future enhancement (out of scope for v1)

#### 4. **Metrics Aggregation**
**Challenge**: Combine metrics from distributed workers
**Solution**:
- Workers send metrics every 1 second
- Master aggregates using MetricsAggregator
- Real-time percentile calculation
- Time-series alignment

### 📋 Proto Definitions

```protobuf
// vajraedge-proto/src/main/proto/vajraedge.proto
syntax = "proto3";

package vajraedge.distributed;

option java_package = "com.vajraedge.perftest.proto";
option java_multiple_files = true;

// Service definition
service WorkerService {
    // Worker registration
    rpc RegisterWorker(WorkerInfo) returns (RegistrationResponse);
    
    // Heartbeat to check worker health
    rpc Heartbeat(HeartbeatRequest) returns (HeartbeatResponse);
    
    // Assign tasks to worker
    rpc AssignTasks(TaskAssignment) returns (TaskAck);
    
    // Stop test on worker
    rpc StopTest(StopRequest) returns (StopResponse);
    
    // Stream metrics from worker to master
    rpc StreamMetrics(stream WorkerMetrics) returns (MetricsAck);
}

// Worker information
message WorkerInfo {
    string worker_id = 1;
    string hostname = 2;
    string ip_address = 3;
    int32 max_concurrency = 4;
    int32 available_cores = 5;
    int64 available_memory_mb = 6;
    string version = 7;
}

message RegistrationResponse {
    bool accepted = 1;
    string message = 2;
    int32 heartbeat_interval_seconds = 3;
    int32 metrics_interval_seconds = 4;
}

// Heartbeat
message HeartbeatRequest {
    string worker_id = 1;
    int32 active_tasks = 2;
    double cpu_usage_percent = 3;
    double memory_usage_percent = 4;
}

message HeartbeatResponse {
    bool healthy = 1;
    string message = 2;
}

// Task assignment
message TaskAssignment {
    string test_id = 1;
    string task_type = 2;
    map<string, string> parameters = 3;
    int32 target_concurrency = 4;
    int32 target_tps = 5;
    int64 duration_seconds = 6;
    RampStrategy ramp_strategy = 7;
}

message RampStrategy {
    enum Type {
        STEP = 0;
        LINEAR = 1;
    }
    Type type = 1;
    int32 ramp_step = 2;
    int32 ramp_interval_seconds = 3;
    int32 ramp_duration_seconds = 4;
}

message TaskAck {
    bool accepted = 1;
    string message = 2;
}

// Stop request
message StopRequest {
    string test_id = 1;
}

message StopResponse {
    bool stopped = 1;
    string message = 2;
}

// Metrics
message WorkerMetrics {
    string worker_id = 1;
    string test_id = 2;
    int64 timestamp = 3;
    
    int64 total_requests = 4;
    int64 successful_requests = 5;
    int64 failed_requests = 6;
    
    double current_tps = 7;
    int32 active_tasks = 8;
    
    LatencyStats latency = 9;
}

message LatencyStats {
    double average_ms = 1;
    double p50_ms = 2;
    double p95_ms = 3;
    double p99_ms = 4;
    double min_ms = 5;
    double max_ms = 6;
}

message MetricsAck {
    bool received = 1;
}
```

### 🔧 Master Node Implementation

#### 1. **WorkerManager**
```java
@Service
public class WorkerManager {
    
    private final Map<String, WorkerNode> workers = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastHeartbeat = new ConcurrentHashMap<>();
    
    @Scheduled(fixedRate = 5000) // Every 5 seconds
    public void checkWorkerHealth() {
        Instant now = Instant.now();
        Duration timeout = Duration.ofSeconds(15);
        
        workers.keySet().forEach(workerId -> {
            Instant lastSeen = lastHeartbeat.get(workerId);
            if (lastSeen == null || 
                Duration.between(lastSeen, now).compareTo(timeout) > 0) {
                
                log.warn("Worker {} is unresponsive, marking as unhealthy", workerId);
                WorkerNode worker = workers.get(workerId);
                worker.setHealthy(false);
                
                // Reassign tasks from this worker
                reassignTasksFromWorker(workerId);
            }
        });
    }
    
    public void registerWorker(WorkerInfo info) {
        WorkerNode worker = new WorkerNode(
            info.getWorkerId(),
            info.getHostname(),
            info.getIpAddress(),
            info.getMaxConcurrency()
        );
        
        workers.put(info.getWorkerId(), worker);
        lastHeartbeat.put(info.getWorkerId(), Instant.now());
        
        log.info("Registered worker: {} at {}:{}", 
            worker.getWorkerId(),
            worker.getHostname(),
            worker.getIpAddress());
    }
    
    public WorkerNode selectWorkerForTask() {
        return workers.values().stream()
            .filter(WorkerNode::isHealthy)
            .min(Comparator.comparingInt(WorkerNode::getCurrentLoad))
            .orElseThrow(() -> new RuntimeException("No healthy workers available"));
    }
    
    public void updateHeartbeat(String workerId, HeartbeatRequest request) {
        lastHeartbeat.put(workerId, Instant.now());
        
        WorkerNode worker = workers.get(workerId);
        if (worker != null) {
            worker.setActiveTasks(request.getActiveTasks());
            worker.setCpuUsage(request.getCpuUsagePercent());
            worker.setMemoryUsage(request.getMemoryUsagePercent());
        }
    }
}
```

#### 2. **TestOrchestrator**
```java
@Service
public class TestOrchestrator {
    
    @Autowired
    private WorkerManager workerManager;
    
    @Autowired
    private MetricsAggregator metricsAggregator;
    
    public String startDistributedTest(TestConfigRequest config) {
        String testId = UUID.randomUUID().toString();
        
        // Calculate distribution
        int totalConcurrency = config.getMaxConcurrency();
        List<WorkerNode> healthyWorkers = workerManager.getHealthyWorkers();
        
        if (healthyWorkers.isEmpty()) {
            throw new RuntimeException("No healthy workers available");
        }
        
        // Distribute load across workers
        int concurrencyPerWorker = totalConcurrency / healthyWorkers.size();
        int tpsPerWorker = config.getTargetTps() / healthyWorkers.size();
        
        // Send task assignments to each worker
        for (WorkerNode worker : healthyWorkers) {
            TaskAssignment assignment = TaskAssignment.newBuilder()
                .setTestId(testId)
                .setTaskType(config.getTaskType())
                .putAllParameters(convertParameters(config.getTaskParameter()))
                .setTargetConcurrency(concurrencyPerWorker)
                .setTargetTps(tpsPerWorker)
                .setDurationSeconds(config.getTestDurationSeconds())
                .setRampStrategy(convertRampStrategy(config))
                .build();
                
            sendTaskAssignment(worker, assignment);
        }
        
        return testId;
    }
    
    private void sendTaskAssignment(WorkerNode worker, TaskAssignment assignment) {
        try {
            // gRPC call to worker
            WorkerServiceBlockingStub stub = worker.getStub();
            TaskAck ack = stub.assignTasks(assignment);
            
            if (!ack.getAccepted()) {
                log.error("Worker {} rejected assignment: {}", 
                    worker.getWorkerId(), ack.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to assign tasks to worker {}", worker.getWorkerId(), e);
        }
    }
}
```

#### 3. **MetricsAggregator**
```java
@Service
public class MetricsAggregator {
    
    private final Map<String, List<WorkerMetrics>> workerMetricsMap = 
        new ConcurrentHashMap<>();
    
    public void receiveMetrics(WorkerMetrics metrics) {
        workerMetricsMap
            .computeIfAbsent(metrics.getTestId(), k -> new CopyOnWriteArrayList<>())
            .add(metrics);
    }
    
    public MetricsSnapshot aggregateMetrics(String testId) {
        List<WorkerMetrics> allMetrics = workerMetricsMap.getOrDefault(
            testId, Collections.emptyList());
        
        if (allMetrics.isEmpty()) {
            return MetricsSnapshot.empty();
        }
        
        // Aggregate across all workers
        long totalRequests = allMetrics.stream()
            .mapToLong(WorkerMetrics::getTotalRequests)
            .sum();
            
        long successfulRequests = allMetrics.stream()
            .mapToLong(WorkerMetrics::getSuccessfulRequests)
            .sum();
            
        double totalTps = allMetrics.stream()
            .mapToDouble(WorkerMetrics::getCurrentTps)
            .sum();
            
        int totalActiveTasks = allMetrics.stream()
            .mapToInt(WorkerMetrics::getActiveTasks)
            .sum();
        
        // Combine latency percentiles (weighted average)
        double avgP50 = calculateWeightedPercentile(allMetrics, 
            m -> m.getLatency().getP50Ms());
        double avgP95 = calculateWeightedPercentile(allMetrics,
            m -> m.getLatency().getP95Ms());
        double avgP99 = calculateWeightedPercentile(allMetrics,
            m -> m.getLatency().getP99Ms());
        
        return MetricsSnapshot.builder()
            .totalRequests(totalRequests)
            .successfulRequests(successfulRequests)
            .currentTps(totalTps)
            .activeTasks(totalActiveTasks)
            .p50(avgP50)
            .p95(avgP95)
            .p99(avgP99)
            .build();
    }
    
    private double calculateWeightedPercentile(
            List<WorkerMetrics> metrics,
            Function<WorkerMetrics, Double> extractor) {
        
        long totalWeight = metrics.stream()
            .mapToLong(WorkerMetrics::getTotalRequests)
            .sum();
        
        if (totalWeight == 0) return 0;
        
        return metrics.stream()
            .mapToDouble(m -> extractor.apply(m) * 
                (double) m.getTotalRequests() / totalWeight)
            .sum();
    }
}
```

### 🔧 Worker Node Implementation

```java
// Worker main application
@SpringBootApplication
public class WorkerApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
    
    @Bean
    public WorkerServiceImpl workerService() {
        return new WorkerServiceImpl();
    }
    
    @Bean
    public Server grpcServer(WorkerServiceImpl service) throws IOException {
        return ServerBuilder.forPort(9090)
            .addService(service)
            .build()
            .start();
    }
}

// Worker gRPC service implementation
@Slf4j
public class WorkerServiceImpl extends WorkerServiceGrpc.WorkerServiceImplBase {
    
    private final TestExecutionService testExecutionService;
    private final MetricsCollector metricsCollector;
    private final Map<String, ScheduledFuture<?>> metricsStreamers = 
        new ConcurrentHashMap<>();
    
    @Override
    public void assignTasks(TaskAssignment request, 
                           StreamObserver<TaskAck> responseObserver) {
        try {
            // Convert to internal format
            TestConfigRequest config = convertToConfig(request);
            
            // Start local test execution
            String testId = testExecutionService.startTest(config);
            
            // Start streaming metrics to master
            startMetricsStreaming(testId, request.getTestId());
            
            responseObserver.onNext(TaskAck.newBuilder()
                .setAccepted(true)
                .setMessage("Tasks accepted")
                .build());
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            log.error("Failed to accept tasks", e);
            responseObserver.onNext(TaskAck.newBuilder()
                .setAccepted(false)
                .setMessage(e.getMessage())
                .build());
            responseObserver.onCompleted();
        }
    }
    
    private void startMetricsStreaming(String localTestId, String masterTestId) {
        // Stream metrics every 1 second
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
            () -> streamMetricsToMaster(localTestId, masterTestId),
            0, 1, TimeUnit.SECONDS
        );
        
        metricsStreamers.put(masterTestId, future);
    }
    
    private void streamMetricsToMaster(String localTestId, String masterTestId) {
        MetricsSnapshot snapshot = metricsCollector.getSnapshot();
        
        WorkerMetrics metrics = WorkerMetrics.newBuilder()
            .setWorkerId(getWorkerId())
            .setTestId(masterTestId)
            .setTimestamp(System.currentTimeMillis())
            .setTotalRequests(snapshot.getTotalRequests())
            .setSuccessfulRequests(snapshot.getSuccessfulRequests())
            .setFailedRequests(snapshot.getTotalRequests() - 
                snapshot.getSuccessfulRequests())
            .setCurrentTps(snapshot.getCurrentTps())
            .setActiveTasks(snapshot.getActiveTasks())
            .setLatency(convertLatencyStats(snapshot))
            .build();
            
        // Send to master via gRPC
        masterStub.streamMetrics(metrics);
    }
}
```

### ⏱️ Effort Estimation

| Task | Effort | Priority |
|------|--------|----------|
| Proto definitions | 4 hours | HIGH |
| Master: WorkerManager | 6 hours | HIGH |
| Master: TestOrchestrator | 6 hours | HIGH |
| Master: MetricsAggregator | 5 hours | HIGH |
| Worker: gRPC service | 6 hours | HIGH |
| Worker: Task execution | 4 hours | MEDIUM |
| Health monitoring | 4 hours | HIGH |
| Fault tolerance | 6 hours | MEDIUM |
| UI updates (show workers) | 4 hours | MEDIUM |
| Docker/K8s deployment | 8 hours | MEDIUM |
| Testing (integration) | 10 hours | HIGH |
| Documentation | 6 hours | HIGH |
| **Total** | **69 hours** | |

### ✅ Acceptance Criteria

1. ✅ Workers can register with master
2. ✅ Master distributes load across workers
3. ✅ Metrics are aggregated in real-time
4. ✅ Failed workers don't crash the test
5. ✅ Tasks are reassigned on worker failure
6. ✅ Health checks detect unresponsive workers
7. ✅ UI shows worker status
8. ✅ Tests can run with 1-100 workers
9. ✅ Linear scalability demonstrated
10. ✅ Documentation covers deployment

---

## Overall Implementation Plan

### Phase 1: Pre-Flight Validation (20 hours)
**Priority**: HIGH  
**Dependencies**: None  
**Deliverables**: 
- Validation framework
- Service health checks
- UI integration

### Phase 2: SDK/Plugin Architecture (34 hours)
**Priority**: HIGH  
**Dependencies**: None  
**Deliverables**:
- SDK module
- Plugin registry
- Example plugins
- Documentation

### Phase 3: Distributed Architecture (69 hours)
**Priority**: MEDIUM  
**Dependencies**: SDK complete  
**Deliverables**:
- gRPC services
- Worker nodes
- Fault tolerance
- Deployment guides

### Total Effort: 123 hours (~15 working days)

---

## Risk Assessment

### High Risk
1. **Distributed metrics aggregation complexity**
   - Mitigation: Start with simple aggregation, optimize later
   
2. **gRPC learning curve**
   - Mitigation: Use existing examples, protobuf best practices

### Medium Risk
1. **Plugin classpath conflicts**
   - Mitigation: Isolated classloaders per plugin
   
2. **Worker coordination edge cases**
   - Mitigation: Comprehensive integration tests

### Low Risk
1. **UI changes for dynamic plugins**
   - Mitigation: Well-understood JavaScript patterns

---

## Item 10: Authentication Support (NEW)

### 📋 Requirement
> "Support authentication mechanisms while hitting resources like HTTP, database, Kafka etc. The solution preferably should not be in the business of storing credentials anywhere."

### 🎯 Objective
Provide flexible, secure authentication for all task types **without** storing credentials in VajraEdge. Delegate credential management to:
- Environment variables
- External secret managers (AWS Secrets Manager, Azure Key Vault, HashiCorp Vault)
- Configuration files (user-managed, not in VajraEdge control)
- Runtime injection (passed at test start time)

### 🏗️ Architecture Design

#### Core Principle: **Zero Trust - Zero Storage**
VajraEdge acts as a **pass-through** for credentials, never persisting them to disk or database.

#### Component Structure
```
com.vajraedge.perftest.auth/
├── AuthProvider.java                 (Interface for auth strategies)
├── AuthContext.java                  (Credential container - in-memory only)
├── providers/
│   ├── NoAuthProvider.java           (Default - no authentication)
│   ├── BasicAuthProvider.java        (HTTP Basic Auth)
│   ├── BearerTokenProvider.java      (OAuth2/JWT tokens)
│   ├── ApiKeyProvider.java           (API key in header/query)
│   ├── OAuth2Provider.java           (OAuth2 client credentials flow)
│   ├── AwsSignatureProvider.java     (AWS SigV4)
│   ├── MutualTLSProvider.java        (Certificate-based)
│   └── DatabaseAuthProvider.java     (DB username/password)
└── resolver/
    ├── CredentialResolver.java       (Interface for credential sources)
    ├── EnvVarResolver.java           (Read from environment)
    ├── SecretManagerResolver.java    (AWS/Azure/Vault integration)
    └── RuntimeResolver.java          (Passed in API request)
```

### 📝 Implementation Details

#### 1. **AuthProvider Interface**

```java
package com.vajraedge.perftest.auth;

/**
 * Strategy for providing authentication to tasks.
 * Implementations should be stateless and thread-safe.
 */
public interface AuthProvider {
    
    /**
     * Apply authentication to the context.
     * 
     * @param context The authentication context with credentials
     * @return Authenticated context (e.g., with headers, tokens)
     * @throws AuthenticationException if auth fails
     */
    <T> T apply(AuthContext context, T target) throws AuthenticationException;
    
    /**
     * Validate that required credentials are present.
     */
    void validate(AuthContext context) throws AuthenticationException;
}
```

#### 2. **AuthContext** (In-Memory Credential Container)

```java
package com.vajraedge.perftest.auth;

import java.util.HashMap;
import java.util.Map;

/**
 * Container for authentication credentials.
 * NEVER persisted - exists only in memory during test execution.
 * 
 * WARNING: Do not log this object or its contents!
 */
public class AuthContext {
    
    private final Map<String, String> credentials;
    private final String authType;
    
    private AuthContext(String authType, Map<String, String> credentials) {
        this.authType = authType;
        this.credentials = new HashMap<>(credentials);
    }
    
    public static Builder builder(String authType) {
        return new Builder(authType);
    }
    
    public String getAuthType() {
        return authType;
    }
    
    public String getCredential(String key) {
        return credentials.get(key);
    }
    
    public boolean hasCredential(String key) {
        return credentials.containsKey(key);
    }
    
    @Override
    public String toString() {
        // NEVER expose credentials in toString!
        return "AuthContext{authType='" + authType + "', credentials=<REDACTED>}";
    }
    
    public static class Builder {
        private final String authType;
        private final Map<String, String> credentials = new HashMap<>();
        
        private Builder(String authType) {
            this.authType = authType;
        }
        
        public Builder credential(String key, String value) {
            if (value != null) {
                this.credentials.put(key, value);
            }
            return this;
        }
        
        public AuthContext build() {
            return new AuthContext(authType, credentials);
        }
    }
}
```

#### 3. **HTTP Basic Auth Example**

```java
package com.vajraedge.perftest.auth.providers;

import com.vajraedge.perftest.auth.AuthContext;
import com.vajraedge.perftest.auth.AuthProvider;
import com.vajraedge.perftest.auth.AuthenticationException;

import java.net.http.HttpRequest;
import java.util.Base64;

public class BasicAuthProvider implements AuthProvider {
    
    @Override
    public HttpRequest apply(AuthContext context, HttpRequest request) {
        String username = context.getCredential("username");
        String password = context.getCredential("password");
        
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
        
        return HttpRequest.newBuilder(request, (name, value) -> true)
            .header("Authorization", "Basic " + encoded)
            .build();
    }
    
    @Override
    public void validate(AuthContext context) throws AuthenticationException {
        if (!context.hasCredential("username") || !context.hasCredential("password")) {
            throw new AuthenticationException(
                "Basic auth requires 'username' and 'password' credentials");
        }
    }
}
```

#### 4. **Bearer Token (OAuth2/JWT) Provider**

```java
package com.vajraedge.perftest.auth.providers;

import com.vajraedge.perftest.auth.AuthContext;
import com.vajraedge.perftest.auth.AuthProvider;
import com.vajraedge.perftest.auth.AuthenticationException;

import java.net.http.HttpRequest;

public class BearerTokenProvider implements AuthProvider {
    
    @Override
    public HttpRequest apply(AuthContext context, HttpRequest request) {
        String token = context.getCredential("token");
        
        return HttpRequest.newBuilder(request, (name, value) -> true)
            .header("Authorization", "Bearer " + token)
            .build();
    }
    
    @Override
    public void validate(AuthContext context) throws AuthenticationException {
        if (!context.hasCredential("token")) {
            throw new AuthenticationException(
                "Bearer token auth requires 'token' credential");
        }
    }
}
```

#### 5. **API Key Provider**

```java
package com.vajraedge.perftest.auth.providers;

import com.vajraedge.perftest.auth.AuthContext;
import com.vajraedge.perftest.auth.AuthProvider;
import com.vajraedge.perftest.auth.AuthenticationException;

import java.net.http.HttpRequest;

/**
 * API Key authentication provider.
 * Supports both header-based and query-parameter-based API keys.
 */
public class ApiKeyProvider implements AuthProvider {
    
    @Override
    public HttpRequest apply(AuthContext context, HttpRequest request) {
        String apiKey = context.getCredential("apiKey");
        String headerName = context.getCredential("headerName");
        
        if (headerName != null) {
            // Header-based API key
            return HttpRequest.newBuilder(request, (name, value) -> true)
                .header(headerName, apiKey)
                .build();
        } else {
            // Query parameter-based (user handles in URL)
            return request;
        }
    }
    
    @Override
    public void validate(AuthContext context) throws AuthenticationException {
        if (!context.hasCredential("apiKey")) {
            throw new AuthenticationException("API key auth requires 'apiKey' credential");
        }
    }
}
```

#### 6. **OAuth2 Client Credentials Flow**

```java
package com.vajraedge.perftest.auth.providers;

import com.vajraedge.perftest.auth.AuthContext;
import com.vajraedge.perftest.auth.AuthProvider;
import com.vajraedge.perftest.auth.AuthenticationException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * OAuth2 Client Credentials flow provider.
 * Automatically fetches and refreshes access tokens.
 */
public class OAuth2Provider implements AuthProvider {
    
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private String cachedToken;
    private Instant tokenExpiry;
    
    @Override
    public HttpRequest apply(AuthContext context, HttpRequest request) 
            throws AuthenticationException {
        
        String token = getAccessToken(context);
        
        return HttpRequest.newBuilder(request, (name, value) -> true)
            .header("Authorization", "Bearer " + token)
            .build();
    }
    
    @Override
    public void validate(AuthContext context) throws AuthenticationException {
        if (!context.hasCredential("clientId") || 
            !context.hasCredential("clientSecret") ||
            !context.hasCredential("tokenUrl")) {
            throw new AuthenticationException(
                "OAuth2 requires 'clientId', 'clientSecret', and 'tokenUrl'");
        }
    }
    
    private synchronized String getAccessToken(AuthContext context) 
            throws AuthenticationException {
        
        // Return cached token if still valid
        if (cachedToken != null && tokenExpiry != null && 
            Instant.now().isBefore(tokenExpiry.minusSeconds(60))) {
            return cachedToken;
        }
        
        // Fetch new token
        try {
            String clientId = context.getCredential("clientId");
            String clientSecret = context.getCredential("clientSecret");
            String tokenUrl = context.getCredential("tokenUrl");
            String scope = context.getCredential("scope"); // Optional
            
            String credentials = clientId + ":" + clientSecret;
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
            
            String body = "grant_type=client_credentials";
            if (scope != null) {
                body += "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8);
            }
            
            HttpRequest tokenRequest = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Authorization", "Basic " + encoded)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            
            HttpResponse<String> response = HTTP_CLIENT.send(
                tokenRequest, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                throw new AuthenticationException(
                    "OAuth2 token fetch failed: " + response.statusCode());
            }
            
            JsonNode json = OBJECT_MAPPER.readTree(response.body());
            cachedToken = json.get("access_token").asText();
            int expiresIn = json.get("expires_in").asInt();
            tokenExpiry = Instant.now().plusSeconds(expiresIn);
            
            return cachedToken;
            
        } catch (Exception e) {
            throw new AuthenticationException("Failed to fetch OAuth2 token", e);
        }
    }
}
```

#### 7. **Database Authentication Provider**

```java
package com.vajraedge.perftest.auth.providers;

import com.vajraedge.perftest.auth.AuthContext;
import com.vajraedge.perftest.auth.AuthProvider;
import com.vajraedge.perftest.auth.AuthenticationException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

/**
 * Database authentication provider.
 * Injects username/password into JDBC connection.
 */
public class DatabaseAuthProvider implements AuthProvider {
    
    @Override
    public Connection apply(AuthContext context, Connection connection) 
            throws AuthenticationException {
        // Connection already established with credentials
        return connection;
    }
    
    @Override
    public void validate(AuthContext context) throws AuthenticationException {
        if (!context.hasCredential("username") || !context.hasCredential("password")) {
            throw new AuthenticationException(
                "Database auth requires 'username' and 'password'");
        }
    }
    
    /**
     * Create authenticated database connection.
     */
    public static Connection createConnection(AuthContext context, String jdbcUrl) 
            throws Exception {
        
        Properties props = new Properties();
        props.setProperty("user", context.getCredential("username"));
        props.setProperty("password", context.getCredential("password"));
        
        return DriverManager.getConnection(jdbcUrl, props);
    }
}
```

#### 8. **Credential Resolvers** (External Secret Management)

```java
package com.vajraedge.perftest.auth.resolver;

import com.vajraedge.perftest.auth.AuthContext;

/**
 * Interface for resolving credentials from external sources.
 */
public interface CredentialResolver {
    
    /**
     * Resolve credentials and populate AuthContext.
     * 
     * @param key The credential key/path
     * @return Resolved credential value
     */
    String resolve(String key) throws CredentialResolutionException;
}
```

```java
package com.vajraedge.perftest.auth.resolver;

/**
 * Resolves credentials from environment variables.
 */
public class EnvVarResolver implements CredentialResolver {
    
    @Override
    public String resolve(String key) throws CredentialResolutionException {
        String value = System.getenv(key);
        if (value == null) {
            throw new CredentialResolutionException(
                "Environment variable not found: " + key);
        }
        return value;
    }
}
```

```java
package com.vajraedge.perftest.auth.resolver;

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

/**
 * Resolves credentials from AWS Secrets Manager.
 */
public class AwsSecretsManagerResolver implements CredentialResolver {
    
    private final SecretsManagerClient client;
    
    public AwsSecretsManagerResolver() {
        this.client = SecretsManagerClient.builder().build();
    }
    
    @Override
    public String resolve(String secretId) throws CredentialResolutionException {
        try {
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                .secretId(secretId)
                .build();
            
            GetSecretValueResponse response = client.getSecretValue(request);
            return response.secretString();
            
        } catch (Exception e) {
            throw new CredentialResolutionException(
                "Failed to resolve AWS secret: " + secretId, e);
        }
    }
}
```

### 🔧 Integration with Existing Tasks

#### Enhanced HttpTask with Authentication

```java
package com.vajraedge.perftest.task;

import com.vajraedge.perftest.auth.AuthContext;
import com.vajraedge.perftest.auth.AuthProvider;
import com.vajraedge.perftest.auth.providers.NoAuthProvider;
import com.vajraedge.perftest.core.SimpleTaskResult;
import com.vajraedge.perftest.core.Task;
import com.vajraedge.perftest.core.TaskResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP task with optional authentication support.
 */
public class AuthenticatedHttpTask implements Task {
    
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    
    private final String url;
    private final AuthProvider authProvider;
    private final AuthContext authContext;
    
    public AuthenticatedHttpTask(String url) {
        this(url, new NoAuthProvider(), null);
    }
    
    public AuthenticatedHttpTask(String url, AuthProvider authProvider, AuthContext authContext) {
        this.url = url;
        this.authProvider = authProvider;
        this.authContext = authContext;
        
        // Validate auth configuration
        if (authContext != null && authProvider != null) {
            authProvider.validate(authContext);
        }
    }
    
    @Override
    public TaskResult execute() throws Exception {
        long startTime = System.nanoTime();
        long taskId = Thread.currentThread().threadId();
        
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(30));
            
            HttpRequest request = requestBuilder.build();
            
            // Apply authentication if configured
            if (authProvider != null && authContext != null) {
                request = authProvider.apply(authContext, request);
            }
            
            HttpResponse<String> response = HTTP_CLIENT.send(
                request, HttpResponse.BodyHandlers.ofString());
            
            long latencyNanos = System.nanoTime() - startTime;
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            
            if (success) {
                return SimpleTaskResult.success(taskId, latencyNanos, response.body().length());
            } else {
                return SimpleTaskResult.failure(
                    taskId, latencyNanos, "HTTP " + response.statusCode());
            }
            
        } catch (Exception e) {
            long latencyNanos = System.nanoTime() - startTime;
            return SimpleTaskResult.failure(taskId, latencyNanos, e.getMessage());
        }
    }
}
```

### 🎨 API Integration

#### Updated TestConfigRequest with Auth

```java
@NotNull
private String authType = "NONE"; // NONE, BASIC, BEARER, API_KEY, OAUTH2, MUTUAL_TLS

// Credential references (NOT actual credentials!)
private Map<String, String> credentialReferences = new HashMap<>();
// Example:
// {
//   "username": "env:API_USERNAME",      // Read from env var
//   "password": "aws:prod/api/password", // Read from AWS Secrets Manager
//   "token": "vault:secret/api-token"    // Read from HashiCorp Vault
// }
```

#### REST API Endpoint

```java
@RestController
@RequestMapping("/api/tests")
public class TestController {
    
    @PostMapping
    public ResponseEntity<TestStatusResponse> startTest(
            @RequestBody @Valid TestConfigRequest request) {
        
        // Resolve credentials at runtime (never store!)
        AuthContext authContext = resolveCredentials(
            request.getAuthType(), 
            request.getCredentialReferences()
        );
        
        // Pass to execution service (in-memory only)
        String testId = testExecutionService.startTest(request, authContext);
        
        // AuthContext gets garbage collected after test starts
        
        return ResponseEntity.ok(new TestStatusResponse(testId, "RUNNING"));
    }
    
    private AuthContext resolveCredentials(
            String authType, 
            Map<String, String> references) {
        
        AuthContext.Builder builder = AuthContext.builder(authType);
        
        for (Map.Entry<String, String> entry : references.entrySet()) {
            String key = entry.getKey();
            String reference = entry.getValue();
            
            String value = credentialResolver.resolve(reference);
            builder.credential(key, value);
        }
        
        return builder.build();
    }
}
```

### 🎨 UI Integration

#### Dashboard Authentication Panel

```javascript
// Auth configuration in UI
function buildAuthConfig() {
    const authType = document.getElementById('authType').value;
    
    const authConfig = {
        authType: authType,
        credentialReferences: {}
    };
    
    switch(authType) {
        case 'BASIC':
            authConfig.credentialReferences = {
                username: document.getElementById('usernameRef').value, // e.g., "env:API_USER"
                password: document.getElementById('passwordRef').value  // e.g., "vault:secret/api"
            };
            break;
        case 'BEARER':
            authConfig.credentialReferences = {
                token: document.getElementById('tokenRef').value // e.g., "env:API_TOKEN"
            };
            break;
        case 'API_KEY':
            authConfig.credentialReferences = {
                apiKey: document.getElementById('apiKeyRef').value,
                headerName: document.getElementById('headerName').value
            };
            break;
        case 'OAUTH2':
            authConfig.credentialReferences = {
                clientId: document.getElementById('clientIdRef').value,
                clientSecret: document.getElementById('clientSecretRef').value,
                tokenUrl: document.getElementById('tokenUrl').value,
                scope: document.getElementById('scope').value
            };
            break;
    }
    
    return authConfig;
}
```

```html
<!-- Authentication Configuration Panel -->
<div class="card mb-3">
    <div class="card-header">
        <h5>🔐 Authentication</h5>
    </div>
    <div class="card-body">
        <div class="mb-3">
            <label for="authType" class="form-label">Authentication Type</label>
            <select class="form-select" id="authType" onchange="updateAuthFields()">
                <option value="NONE">None</option>
                <option value="BASIC">HTTP Basic Auth</option>
                <option value="BEARER">Bearer Token (OAuth2/JWT)</option>
                <option value="API_KEY">API Key</option>
                <option value="OAUTH2">OAuth2 Client Credentials</option>
                <option value="MUTUAL_TLS">Mutual TLS</option>
            </select>
        </div>
        
        <!-- Basic Auth Fields -->
        <div id="basicAuthFields" class="d-none">
            <div class="mb-3">
                <label class="form-label">Username Reference</label>
                <input type="text" class="form-control" id="usernameRef" 
                       placeholder="env:API_USERNAME or aws:prod/username">
                <div class="form-text">
                    Format: env:VAR_NAME | aws:secret/path | vault:secret/path
                </div>
            </div>
            <div class="mb-3">
                <label class="form-label">Password Reference</label>
                <input type="text" class="form-control" id="passwordRef" 
                       placeholder="env:API_PASSWORD or aws:prod/password">
            </div>
        </div>
        
        <!-- Bearer Token Fields -->
        <div id="bearerAuthFields" class="d-none">
            <div class="mb-3">
                <label class="form-label">Token Reference</label>
                <input type="text" class="form-control" id="tokenRef" 
                       placeholder="env:API_TOKEN or aws:prod/api-token">
            </div>
        </div>
        
        <!-- API Key Fields -->
        <div id="apiKeyAuthFields" class="d-none">
            <div class="mb-3">
                <label class="form-label">API Key Reference</label>
                <input type="text" class="form-control" id="apiKeyRef" 
                       placeholder="env:API_KEY">
            </div>
            <div class="mb-3">
                <label class="form-label">Header Name</label>
                <input type="text" class="form-control" id="headerName" 
                       placeholder="X-API-Key" value="X-API-Key">
            </div>
        </div>
        
        <!-- OAuth2 Fields -->
        <div id="oauth2AuthFields" class="d-none">
            <div class="mb-3">
                <label class="form-label">Client ID Reference</label>
                <input type="text" class="form-control" id="clientIdRef" 
                       placeholder="env:OAUTH_CLIENT_ID">
            </div>
            <div class="mb-3">
                <label class="form-label">Client Secret Reference</label>
                <input type="text" class="form-control" id="clientSecretRef" 
                       placeholder="env:OAUTH_CLIENT_SECRET">
            </div>
            <div class="mb-3">
                <label class="form-label">Token URL</label>
                <input type="text" class="form-control" id="tokenUrl" 
                       placeholder="https://auth.example.com/oauth/token">
            </div>
            <div class="mb-3">
                <label class="form-label">Scope (Optional)</label>
                <input type="text" class="form-control" id="scope" 
                       placeholder="read write">
            </div>
        </div>
        
        <div class="alert alert-info mb-0">
            <small>
                <strong>💡 Security Best Practice:</strong><br>
                VajraEdge never stores credentials. Use references to external sources:
                <ul class="mb-0 mt-1">
                    <li><code>env:VAR_NAME</code> - Environment variable</li>
                    <li><code>aws:secret/path</code> - AWS Secrets Manager</li>
                    <li><code>vault:secret/path</code> - HashiCorp Vault</li>
                </ul>
            </small>
        </div>
    </div>
</div>
```

### ⏱️ Effort Estimation

| Component | Hours | Priority |
|-----------|-------|----------|
| AuthProvider interface & base classes | 4h | P0 |
| HTTP auth providers (Basic, Bearer, API Key) | 6h | P0 |
| OAuth2 provider with token caching | 6h | P1 |
| Database auth provider | 4h | P1 |
| Credential resolvers (Env, AWS, Vault) | 8h | P1 |
| Integration with HttpTask | 3h | P0 |
| DTO/API changes | 3h | P0 |
| UI authentication panel | 6h | P1 |
| Testing (unit + integration) | 8h | P0 |
| Documentation | 4h | P1 |
| **Total** | **52 hours** | **~6.5 days** |

### 🎯 Security Principles

#### 1. **Zero Storage**
```java
// ❌ NEVER do this
class VajraEdge {
    private Map<String, String> storedCredentials; // FORBIDDEN!
}

// ✅ ALWAYS do this
public void startTest(TestConfig config, AuthContext authContext) {
    // Use authContext
    Task task = createAuthenticatedTask(authContext);
    
    // authContext will be garbage collected after this method
    // No persistence!
}
```

#### 2. **No Logging of Credentials**
```java
// ❌ NEVER do this
logger.info("Using credentials: {}", authContext); // Dangerous!

// ✅ ALWAYS do this
logger.info("Using auth type: {}", authContext.getAuthType()); // Safe
authContext.toString(); // Returns <REDACTED>
```

#### 3. **Short-Lived In-Memory Only**
```java
// AuthContext lifecycle:
// 1. Created during API request
// 2. Passed to Task execution
// 3. Used during test initialization
// 4. Garbage collected after test starts
// 5. NEVER written to disk/database
```

#### 4. **Credential References, Not Values**
```json
{
  "authType": "BASIC",
  "credentialReferences": {
    "username": "env:API_USER",
    "password": "aws:prod/api/password"
  }
}
```

### ✅ Success Metrics

- ✅ Support 5+ authentication types (Basic, Bearer, API Key, OAuth2, mTLS)
- ✅ Zero credential persistence (verified by code review)
- ✅ Support 3+ credential sources (Env, AWS, Vault)
- ✅ 100% test coverage on auth providers
- ✅ Security audit passes
- ✅ Clear documentation on secure credential management

### 🔗 Integration with Other Items

#### With Item 7 (Pre-Flight Validation)
```java
public class AuthValidationCheck implements ValidationCheck {
    
    @Override
    public CheckResult execute(ValidationContext context) {
        // Validate auth credentials are resolvable
        AuthContext authContext = context.getAuthContext();
        
        try {
            authProvider.validate(authContext);
            // Try a test request with auth
            makeTestRequest(authContext);
            return CheckResult.pass("Authentication validated");
        } catch (Exception e) {
            return CheckResult.fail("Authentication failed: " + e.getMessage());
        }
    }
}
```

#### With Item 8 (SDK/Plugin)
```java
@VajraTask(
    name = "Authenticated HTTP",
    description = "HTTP task with OAuth2 authentication"
)
public class MyAuthenticatedTask implements Task {
    
    private final AuthProvider authProvider;
    private final AuthContext authContext;
    
    @Override
    public TaskResult execute() {
        // Use injected auth
        HttpRequest request = buildRequest();
        request = authProvider.apply(authContext, request);
        // Execute...
    }
}
```

---

## Success Metrics

### Item 7: Validation
- ✅ 0% test failures due to unreachable services
- ✅ Clear error messages for all failure scenarios
- ✅ <5s validation time

### Item 8: SDK
- ✅ 3rd party can create plugin in <1 hour
- ✅ Zero code changes to add new task types
- ✅ 5+ example plugins

### Item 9: Distributed
- ✅ Linear scalability up to 10 workers
- ✅ <1% metric accuracy variance
- ✅ <30s worker failure recovery

### Item 10: Authentication (NEW)
- ✅ Support 5+ authentication types
- ✅ Zero credential storage (100% in-memory)
- ✅ Support 3+ credential sources
- ✅ Security audit passes
- ✅ Clear documentation

---

## Revised Timeline & Effort

| Item | Effort | Priority | Sequence |
|------|--------|----------|----------|
| **Item 7**: Pre-flight validation | 20h (~2.5 days) | **P0** | **1st** |
| **Item 10**: Authentication support | 52h (~6.5 days) | **P0** | **2nd** |
| **Item 8**: SDK/Plugin architecture | 34h (~4.2 days) | **P1** | **3rd** |
| **Item 9**: Distributed testing | 69h (~8.6 days) | **P2** | **4th** |
| **Total** | **175 hours** | **~22 days** | - |

### Recommended Implementation Order

**Phase 1: Foundation (9 days)**
- Item 7: Pre-flight validation (2.5 days)
- Item 10: Authentication support (6.5 days)

**Phase 2: Extensibility (4 days)**
- Item 8: SDK/Plugin architecture (4 days)

**Phase 3: Scale (9 days)**
- Item 9: Distributed testing (9 days)

---

## Next Steps

1. **Review authentication design** with security team
2. **Validate credential resolver integrations** (AWS/Vault access)
3. **Start with Item 7** (quickest win)
4. **Implement Item 10** (critical for real-world use)
5. **Parallel track Item 8** (enables ecosystem)
6. **Plan Item 9** for future release (complex)

---

**Questions for Discussion**:
1. Should all 4 items be in same release?
2. What's priority order? (Recommended: 7 → 10 → 8 → 9)
3. Are we OK with 22 days timeline?
4. Which credential sources to support first? (Env + AWS recommended)
5. Any concerns with zero-storage authentication design?
