# VajraEdge SDK Separation Plan

**Date**: November 6, 2025  
**Status**: Planning Phase  
**Goal**: Extract SDK into separate JAR for distributed worker architecture

---

## Executive Summary

Currently, the SDK components (interfaces, annotations, metadata) are embedded within the main VajraEdge application. For Item 9 (Distributed Testing), we need to separate the SDK into its own JAR module so that:

1. **Workers can be built independently** - Users build their own worker applications
2. **SDK is a lightweight dependency** - Only core interfaces, no framework dependencies
3. **Plugin architecture is clean** - Plugins depend on SDK, not the entire framework
4. **Version management is independent** - SDK can have its own release cycle

---

## Current Architecture Issues

### Problem 1: SDK Embedded in Framework
```
vajraedge/
  src/main/java/com/vajraedge/perftest/
    sdk/                         ❌ Mixed with framework code
    plugins/                      ❌ Depends on framework
    core/                         ❌ SDK depends on core
    service/                      ✅ Framework services
    controller/                   ✅ Framework controllers
```

### Problem 2: Circular Dependencies
- SDK interfaces extend core interfaces (Task, TaskResult)
- Plugins depend on both SDK and core
- Worker would need entire framework as dependency

### Problem 3: No Clear Distribution Boundary
- Users can't build standalone workers
- Can't distribute SDK separately via Maven Central
- Plugin developers need to understand entire codebase

---

## Target Architecture

### Multi-Module Gradle Project

```
vajraedge/
├── settings.gradle                    # Multi-module configuration
├── build.gradle                       # Parent build config
│
├── vajraedge-sdk/                     # ✅ NEW MODULE
│   ├── build.gradle                   # Minimal dependencies
│   └── src/main/java/
│       └── com/vajraedge/sdk/
│           ├── Task.java              # Moved from core
│           ├── TaskResult.java        # Moved from core
│           ├── TaskPlugin.java        # Moved from sdk
│           ├── TaskMetadata.java      # Moved from sdk
│           ├── SimpleTaskResult.java  # Moved from core
│           └── annotations/
│               └── VajraTask.java     # Moved from sdk
│
├── vajraedge-core/                    # ✅ RENAMED (was vajraedge)
│   ├── build.gradle                   # Depends on vajraedge-sdk
│   └── src/main/java/
│       └── com/vajraedge/perftest/
│           ├── executor/              # Framework executors
│           ├── metrics/               # Metrics collection
│           ├── controller/            # REST API
│           ├── service/               # Business logic
│           └── plugins/               # Built-in plugins
│
├── vajraedge-worker/                  # ✅ NEW MODULE (Template)
│   ├── build.gradle                   # Depends on vajraedge-sdk only
│   └── src/main/java/
│       └── com/vajraedge/worker/
│           ├── Worker.java            # Worker bootstrap
│           ├── TaskExecutor.java      # Lightweight executor
│           └── GrpcClient.java        # Controller communication
│
└── vajraedge-plugins/                 # ✅ NEW MODULE (Examples)
    ├── build.gradle                   # Depends on vajraedge-sdk only
    └── src/main/java/
        └── com/vajraedge/plugins/
            ├── http/
            │   ├── HttpGetTask.java   # Moved from core
            │   └── HttpPostTask.java
            ├── grpc/
            │   └── GrpcTask.java
            └── database/
                ├── PostgresTask.java
                └── MongoTask.java
```

---

## Module Definitions

### 1. vajraedge-sdk (NEW)

**Purpose**: Lightweight SDK for plugin and worker development

**Dependencies**: NONE (zero external dependencies)

**Package**: `net.vajraedge.sdk`

**Contents**:
```
net.vajraedge.sdk/
├── Task.java                    # Core task interface
├── TaskResult.java              # Result interface
├── TaskPlugin.java              # Plugin marker interface
├── TaskMetadata.java            # Plugin metadata
├── SimpleTaskResult.java        # Default implementation
└── annotations/
    └── VajraTask.java           # Plugin annotation
```

**Build Output**: `vajraedge-sdk-1.0.0.jar` (~10KB)

**Maven Coordinates**:
```xml
<dependency>
    <groupId>net.vajraedge</groupId>
    <artifactId>vajraedge-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

### 2. vajraedge-core (RENAMED)

**Purpose**: Main framework application (controller/master)

**Dependencies**: 
- vajraedge-sdk (compile)
- Spring Boot
- gRPC server
- WebSocket
- Metrics

**Package**: `net.vajraedge.perftest`

**Contents**:
```
net.vajraedge.perftest/
├── controller/          # REST API endpoints
├── service/             # Business logic
├── executor/            # Local task execution
├── metrics/             # Metrics aggregation
├── websocket/           # Real-time updates
├── validation/          # Configuration validation
└── grpc/                # gRPC controller service (NEW)
```

**Build Output**: `vajraedge-core-1.0.0.jar` + dependencies

**Deployment**: Controller/Master node

---

### 3. vajraedge-worker (NEW - TEMPLATE)

**Purpose**: Template for building custom workers

**Dependencies**: 
- vajraedge-sdk (compile)
- gRPC client
- Minimal logging

**Package**: `net.vajraedge.worker`

**Contents**:
```
net.vajraedge.worker/
├── Worker.java                  # Main worker bootstrap
├── TaskExecutor.java            # Virtual thread executor
├── GrpcClient.java              # Controller communication
├── MetricsReporter.java         # Send metrics to controller
└── config/
    └── WorkerConfig.java        # Worker configuration
```

**Build Output**: `vajraedge-worker-1.0.0.jar`

**Usage**: Users copy this module and add their plugins

---

### 4. vajraedge-plugins (NEW - EXAMPLES)

**Purpose**: Example plugins showing best practices

**Dependencies**: 
- vajraedge-sdk (compile)
- HTTP client (for HTTP plugins)
- gRPC libraries (for gRPC plugins)
- Database drivers (for DB plugins)

**Package**: `net.vajraedge.plugins`

**Contents**:
```
net.vajraedge.plugins/
├── http/
│   ├── HttpGetTask.java         # HTTP GET plugin
│   ├── HttpPostTask.java        # HTTP POST plugin
│   └── HttpPutTask.java         # HTTP PUT plugin
├── grpc/
│   └── GrpcUnaryTask.java       # gRPC unary call
├── database/
│   ├── PostgresQueryTask.java   # Postgres query
│   └── MongoQueryTask.java      # MongoDB query
└── messaging/
    ├── KafkaProducerTask.java   # Kafka producer
    └── RabbitMQTask.java        # RabbitMQ publish
```

**Build Output**: `vajraedge-plugins-1.0.0.jar`

**Usage**: Reference implementations for plugin authors

---

## Migration Steps

### Phase 1: Setup Multi-Module Structure (2 hours)

1. **Update settings.gradle**
```gradle
rootProject.name = 'vajraedge'

include 'vajraedge-sdk'
include 'vajraedge-core'
include 'vajraedge-worker'
include 'vajraedge-plugins'
```

2. **Create parent build.gradle**
```gradle
plugins {
    id 'java'
    id 'groovy'
}

subprojects {
    apply plugin: 'java'
    
    group = 'net.vajraedge'
    version = '1.0.0'
    
    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    
    repositories {
        mavenCentral()
    }
}
```

3. **Create module directories**
```bash
mkdir -p vajraedge-sdk/src/main/java/com/vajraedge/sdk
mkdir -p vajraedge-sdk/src/main/java/com/vajraedge/sdk/annotations
mkdir -p vajraedge-core/src/main/java
mkdir -p vajraedge-worker/src/main/java
mkdir -p vajraedge-plugins/src/main/java
```

---

### Phase 2: Extract SDK Module (4 hours)

#### Step 1: Create vajraedge-sdk/build.gradle
```gradle
plugins {
    id 'java-library'
    id 'maven-publish'
}

description = 'VajraEdge SDK - Lightweight task interface for load testing'

dependencies {
    // ZERO external dependencies - pure Java 21
}

jar {
    manifest {
        attributes(
            'Implementation-Title': 'VajraEdge SDK',
            'Implementation-Version': version,
            'Automatic-Module-Name': 'net.vajraedge.sdk'
        )
    }
}

publishing {
    publications {
        maven(MavenPublication) {
            groupId = 'net.vajraedge'
            artifactId = 'vajraedge-sdk'
            version = version
            from components.java
        }
    }
}
```

#### Step 2: Move Core Interfaces to SDK
```bash
# Move Task.java
git mv src/main/java/com/vajraedge/perftest/core/Task.java \
        vajraedge-sdk/src/main/java/com/vajraedge/sdk/Task.java

# Move TaskResult.java
git mv src/main/java/com/vajraedge/perftest/core/TaskResult.java \
        vajraedge-sdk/src/main/java/com/vajraedge/sdk/TaskResult.java

# Move SimpleTaskResult.java
git mv src/main/java/com/vajraedge/perftest/core/SimpleTaskResult.java \
        vajraedge-sdk/src/main/java/com/vajraedge/sdk/SimpleTaskResult.java

# Move SDK classes
git mv src/main/java/com/vajraedge/perftest/sdk/* \
        vajraedge-sdk/src/main/java/com/vajraedge/sdk/
```

#### Step 3: Update Package Declarations
```java
// Before (in core)
package net.vajraedge.perftest.core;

// After (in SDK)
package net.vajraedge.sdk;
```

#### Step 4: Update Imports Throughout Codebase
```java
// Before
import net.vajraedge.perftest.core.Task;
import net.vajraedge.perftest.core.TaskResult;
import net.vajraedge.perftest.sdk.TaskPlugin;

// After
import net.vajraedge.sdk.Task;
import net.vajraedge.sdk.TaskResult;
import net.vajraedge.sdk.TaskPlugin;
```

---

### Phase 3: Create Core Module (3 hours)

#### Step 1: Create vajraedge-core/build.gradle
```gradle
plugins {
    id 'java'
    id 'groovy'
    id 'application'
    id 'org.springframework.boot' version '3.5.7'
    id 'io.spring.dependency-management' version '1.1.7'
    id 'jacoco'
}

description = 'VajraEdge Core - Main controller/master application'

dependencies {
    // SDK dependency
    implementation project(':vajraedge-sdk')
    
    // Spring Boot starters
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-websocket'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    
    // gRPC server
    implementation "io.grpc:grpc-netty-shaded:${grpcVersion}"
    implementation "io.grpc:grpc-protobuf:${grpcVersion}"
    implementation "io.grpc:grpc-stub:${grpcVersion}"
    
    // Everything else from current build.gradle
}

application {
    mainClass = 'net.vajraedge.perftest.Application'
}
```

#### Step 2: Move Existing Code
```bash
# Move entire src directory to vajraedge-core
mv src vajraedge-core/

# Move resources
mv build.gradle.backup vajraedge-core/build.gradle
```

---

### Phase 4: Create Worker Template (6 hours)

#### Step 1: Create vajraedge-worker/build.gradle
```gradle
plugins {
    id 'java'
    id 'application'
}

description = 'VajraEdge Worker - Template for building custom workers'

ext {
    grpcVersion = '1.70.0'
}

dependencies {
    // SDK dependency only
    implementation project(':vajraedge-sdk')
    
    // gRPC client
    implementation "io.grpc:grpc-netty-shaded:${grpcVersion}"
    implementation "io.grpc:grpc-protobuf:${grpcVersion}"
    implementation "io.grpc:grpc-stub:${grpcVersion}"
    
    // Minimal logging
    implementation 'org.slf4j:slf4j-api:2.0.9'
    implementation 'ch.qos.logback:logback-classic:1.4.11'
}

application {
    mainClass = 'net.vajraedge.worker.Worker'
}
```

#### Step 2: Implement Worker Bootstrap
```java
package net.vajraedge.worker;

/**
 * Worker node that executes tasks from controller.
 * 
 * <p>Workers connect to the controller via gRPC and receive
 * task assignments. They execute tasks using virtual threads
 * and report metrics back to the controller.</p>
 */
public class Worker {
    
    private final WorkerConfig config;
    private final GrpcClient grpcClient;
    private final TaskExecutor taskExecutor;
    private final MetricsReporter metricsReporter;
    
    public Worker(WorkerConfig config) {
        this.config = config;
        this.grpcClient = new GrpcClient(config.getControllerAddress());
        this.taskExecutor = new TaskExecutor(config.getMaxConcurrency());
        this.metricsReporter = new MetricsReporter(grpcClient);
    }
    
    public void start() throws Exception {
        // Connect to controller
        grpcClient.connect();
        
        // Register worker
        grpcClient.register(config.getWorkerId(), config.getCapabilities());
        
        // Start task execution loop
        taskExecutor.start();
        
        // Start metrics reporting
        metricsReporter.start();
        
        log.info("Worker started: id={}, controller={}", 
            config.getWorkerId(), config.getControllerAddress());
    }
    
    public static void main(String[] args) {
        WorkerConfig config = WorkerConfig.fromArgs(args);
        Worker worker = new Worker(config);
        
        try {
            worker.start();
            
            // Keep alive until shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(worker::shutdown));
            Thread.currentThread().join();
            
        } catch (Exception e) {
            log.error("Worker failed", e);
            System.exit(1);
        }
    }
}
```

---

### Phase 5: Create Plugin Examples (4 hours)

#### Step 1: Create vajraedge-plugins/build.gradle
```gradle
plugins {
    id 'java-library'
}

description = 'VajraEdge Plugins - Example plugin implementations'

dependencies {
    // SDK dependency only
    api project(':vajraedge-sdk')
    
    // HTTP client for HTTP plugins
    implementation 'org.apache.httpcomponents.client5:httpclient5:5.3'
    
    // gRPC for gRPC plugins
    implementation "io.grpc:grpc-netty-shaded:${grpcVersion}"
    implementation "io.grpc:grpc-stub:${grpcVersion}"
    
    // Database drivers (examples)
    compileOnly 'org.postgresql:postgresql:42.7.1'
    compileOnly 'org.mongodb:mongodb-driver-sync:4.11.1'
}
```

#### Step 2: Move Existing Plugins
```bash
# Move HttpGetTaskPlugin
git mv vajraedge-core/src/main/java/com/vajraedge/perftest/plugins/HttpGetTaskPlugin.java \
        vajraedge-plugins/src/main/java/com/vajraedge/plugins/http/HttpGetTask.java

# Update package
# From: package net.vajraedge.perftest.plugins;
# To:   package net.vajraedge.plugins.http;
```

---

## User Workflow: Building a Custom Worker

### Step 1: Clone Worker Template
```bash
git clone https://github.com/vajraedge/vajraedge-worker-template
cd vajraedge-worker-template
```

### Step 2: Add SDK Dependency
```gradle
// build.gradle
dependencies {
    implementation 'net.vajraedge:vajraedge-sdk:1.0.0'
    
    // Add your dependencies
    implementation 'your-library:1.0.0'
}
```

### Step 3: Implement Custom Task
```java
package com.mycompany.loadtest;

import net.vajraedge.sdk.*;
import net.vajraedge.sdk.annotations.VajraTask;

@VajraTask(
    name = "MY_API_CALL",
    displayName = "My API Call",
    description = "Calls my internal API"
)
public class MyApiTask implements TaskPlugin {
    
    private final String apiUrl;
    
    @Override
    public void initialize(Map<String, Object> parameters) {
        this.apiUrl = (String) parameters.get("url");
    }
    
    @Override
    public TaskResult execute() throws Exception {
        long start = System.nanoTime();
        try {
            // Your API call logic
            MyApiResponse response = myApiClient.call(apiUrl);
            long latency = System.nanoTime() - start;
            
            return SimpleTaskResult.success(latency);
        } catch (Exception e) {
            long latency = System.nanoTime() - start;
            return SimpleTaskResult.failure(latency, e.getMessage());
        }
    }
    
    @Override
    public TaskMetadata getMetadata() {
        return TaskMetadata.builder()
            .name("MY_API_CALL")
            .displayName("My API Call")
            .description("Calls my internal API endpoint")
            .build();
    }
}
```

### Step 4: Build Worker
```bash
./gradlew clean build
```

### Step 5: Deploy Worker
```bash
# Copy jar to worker nodes
scp build/libs/my-worker-1.0.0.jar worker1:/opt/vajraedge/

# Run worker
java -jar my-worker-1.0.0.jar \
  --worker-id=worker1 \
  --controller=controller.example.com:8080 \
  --max-concurrency=10000
```

---

## Benefits of Separation

### For Plugin Developers
- ✅ **Minimal dependencies** - Only need vajraedge-sdk (~10KB)
- ✅ **No framework knowledge required** - Just implement Task interface
- ✅ **Fast compilation** - No need to build entire framework
- ✅ **Clear contracts** - SDK interface is stable and versioned

### For Worker Developers
- ✅ **Build custom workers** - Add only the plugins they need
- ✅ **Small deployment artifacts** - Not carrying entire framework
- ✅ **Independent versioning** - Workers can update independently
- ✅ **Language flexibility** - Could port worker to Go/Python/Rust

### For Framework Development
- ✅ **Clean separation** - Core framework vs SDK vs workers
- ✅ **Independent releases** - SDK, core, and plugins version separately
- ✅ **Better testing** - Test SDK without framework dependencies
- ✅ **Maven Central ready** - Can publish SDK separately

### For Distributed Architecture (Item 9)
- ✅ **Worker deployment** - Users build workers with their plugins
- ✅ **Controller isolation** - Controller doesn't need plugin code
- ✅ **Scalability** - Workers can be deployed independently
- ✅ **Security** - Workers only get SDK, not framework secrets

---

## Migration Checklist

### Setup Phase
- [ ] Create multi-module Gradle structure
- [ ] Update settings.gradle with modules
- [ ] Create parent build.gradle
- [ ] Create module directories

### SDK Module
- [ ] Create vajraedge-sdk/build.gradle
- [ ] Move Task.java to SDK
- [ ] Move TaskResult.java to SDK
- [ ] Move SimpleTaskResult.java to SDK
- [ ] Move TaskPlugin.java to SDK
- [ ] Move TaskMetadata.java to SDK
- [ ] Move VajraTask annotation to SDK
- [ ] Update package declarations
- [ ] Update imports throughout codebase
- [ ] Run tests - ensure all pass
- [ ] Build SDK jar

### Core Module
- [ ] Create vajraedge-core/build.gradle
- [ ] Add dependency on vajraedge-sdk
- [ ] Move existing src to vajraedge-core
- [ ] Update all imports to use SDK package
- [ ] Update Application.java if needed
- [ ] Run tests - ensure all pass
- [ ] Build core jar

### Worker Module
- [ ] Create vajraedge-worker/build.gradle
- [ ] Implement Worker.java
- [ ] Implement TaskExecutor.java
- [ ] Implement GrpcClient.java
- [ ] Implement MetricsReporter.java
- [ ] Implement WorkerConfig.java
- [ ] Add example usage in README
- [ ] Run tests
- [ ] Build worker jar

### Plugins Module
- [ ] Create vajraedge-plugins/build.gradle
- [ ] Move HttpGetTaskPlugin to plugins module
- [ ] Update package declarations
- [ ] Add README with examples
- [ ] Run tests
- [ ] Build plugins jar

### Documentation
- [ ] Update main README.md
- [ ] Create SDK documentation
- [ ] Create worker template guide
- [ ] Create plugin developer guide
- [ ] Update architecture diagrams
- [ ] Add migration guide for existing users

### Testing & Validation
- [ ] All modules build successfully
- [ ] All tests pass (467/467)
- [ ] SDK has no external dependencies
- [ ] Worker can build standalone
- [ ] Plugins work with SDK only
- [ ] Integration test: core + worker + plugin

---

## Timeline

| Phase | Tasks | Estimate |
|-------|-------|----------|
| Phase 1 | Multi-module setup | 2 hours |
| Phase 2 | Extract SDK | 4 hours |
| Phase 3 | Create core module | 3 hours |
| Phase 4 | Create worker template | 6 hours |
| Phase 5 | Create plugin examples | 4 hours |
| Testing | End-to-end validation | 3 hours |
| Documentation | Guides and examples | 4 hours |
| **TOTAL** | | **26 hours (~3.5 days)** |

---

## Next Steps

After SDK separation is complete, proceed with Item 9:

1. **Define gRPC Protocol** (2 days)
   - Controller ↔ Worker communication
   - Task assignment messages
   - Metrics reporting messages

2. **Implement Controller gRPC Service** (3 days)
   - Worker registration
   - Task distribution
   - Metrics aggregation

3. **Enhance Worker Template** (2 days)
   - Full gRPC integration
   - Health checks
   - Graceful shutdown

4. **Test Distributed Setup** (1 day)
   - Controller + 5 workers
   - Large-scale load test
   - Failover scenarios

---

## Conclusion

Separating the SDK into its own module is a critical prerequisite for Item 9 (Distributed Testing). This architectural change provides:

- **Clean boundaries** between SDK, core, and workers
- **Independent deployment** of workers with custom plugins
- **Minimal dependencies** for plugin developers
- **Foundation for distributed architecture**

This work should be completed before starting Item 9 implementation.

---

**Document Status**: Planning Complete  
**Next Action**: Begin Phase 1 (Multi-module setup)  
**Estimated Completion**: 3.5 days  
**Depends On**: None (can start immediately)  
**Blocks**: Item 9 (Distributed Testing Architecture)
