# GitHub Copilot Instructions for VajraEdge

## Project Overview
VajraEdge is a modern, high-performance load testing framework built with Java 21, Spring Boot 3.5.7, and virtual threads. This document provides guidance for GitHub Copilot when assisting with code generation, refactoring, and documentation.

## Core Design Principles

### 1. Simplicity First
- Favor simple, readable code over clever abstractions
- One interface, one purpose - avoid multi-responsibility classes
- Minimize dependencies and coupling between components
- Prefer composition over inheritance

### 2. Developer Experience
- APIs should be intuitive and self-documenting
- Provide sensible defaults, make configuration optional
- Error messages should be helpful and actionable
- Documentation should be inline and up-to-date

### 3. Performance Without Compromise
- Leverage Java 21 features (virtual threads, pattern matching, records)
- Avoid premature optimization, but design for scalability
- Use virtual threads for I/O-bound operations
- Measure and monitor performance metrics

### 4. Production-Ready Code
- Comprehensive error handling with specific exceptions
- Proper logging at appropriate levels (DEBUG, INFO, WARN, ERROR)
- Graceful degradation and circuit breaker patterns where appropriate
- Security best practices (input validation, sanitization)

## Code Conventions

### Java Code Style

#### Naming Conventions
- **Classes**: PascalCase, noun-based (e.g., `TaskExecutor`, `MetricsCollector`)
- **Interfaces**: PascalCase, capability-based (e.g., `Task`, `TaskFactory`)
- **Methods**: camelCase, verb-based (e.g., `executeTask()`, `collectMetrics()`)
- **Variables**: camelCase, descriptive (e.g., `targetTps`, `rampUpDuration`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `MAX_CONCURRENCY`, `DEFAULT_TIMEOUT`)
- **Packages**: lowercase, domain-based (e.g., `com.vajraedge.perftest.executor`)

#### Class Structure
```java
// 1. Package declaration
package com.vajraedge.perftest.service;

// 2. Imports (grouped: java, javax, spring, third-party, internal)
import java.util.concurrent.*;
import org.springframework.stereotype.Service;
import com.vajraedge.perftest.core.Task;

// 3. Class documentation
/**
 * Service responsible for executing performance tests.
 * Uses virtual threads for efficient concurrent task execution.
 */
// 4. Annotations
@Service
// 5. Class declaration
public class TestExecutionService {
    
    // 6. Static constants
    private static final int DEFAULT_TIMEOUT = 30;
    
    // 7. Instance fields
    private final TaskExecutor executor;
    private final MetricsCollector metricsCollector;
    
    // 8. Constructor(s)
    public TestExecutionService(TaskExecutor executor, MetricsCollector metricsCollector) {
        this.executor = executor;
        this.metricsCollector = metricsCollector;
    }
    
    // 9. Public methods
    public void startTest(TestConfig config) {
        // Implementation
    }
    
    // 10. Private methods
    private void validateConfig(TestConfig config) {
        // Implementation
    }
}
```

#### Method Design
- Keep methods focused and small (prefer < 30 lines)
- Use early returns to reduce nesting
- Validate inputs at method entry
- Document public methods with JavaDoc
- Use descriptive parameter names

#### Exception Handling
```java
// Good - Specific exceptions with context
public TaskResult execute() throws TaskExecutionException {
    try {
        return performTask();
    } catch (IOException e) {
        throw new TaskExecutionException("Failed to execute task: " + taskId, e);
    }
}

// Bad - Generic exceptions, swallowing errors
public TaskResult execute() throws Exception {
    try {
        return performTask();
    } catch (Exception e) {
        return null; // Don't do this!
    }
}
```

### Spring Boot Conventions

#### Dependency Injection
- Use constructor injection (preferred over field injection)
- Mark services with `@Service`, controllers with `@RestController`
- Use `@Autowired` only when constructor injection is not feasible

#### REST API Design
- Use proper HTTP methods (GET, POST, PUT, DELETE)
- Return appropriate status codes (200, 201, 204, 400, 404, 500)
- Use DTOs for request/response objects
- Version APIs when making breaking changes

```java
@RestController
@RequestMapping("/api/tests")
public class TestController {
    
    @PostMapping
    public ResponseEntity<TestStatusResponse> startTest(@RequestBody @Valid TestConfigRequest request) {
        // Implementation
    }
    
    @GetMapping("/{testId}")
    public ResponseEntity<TestStatusResponse> getTestStatus(@PathVariable String testId) {
        // Implementation
    }
}
```

### Testing Conventions

#### Spock Tests
- Use Spock framework for all tests (Groovy-based)
- Follow Given-When-Then structure
- Use descriptive test method names
- Test edge cases and error conditions

```groovy
class MetricsCollectorSpec extends Specification {
    
    def "should calculate correct percentiles for collected latencies"() {
        given: "a metrics collector with sample data"
        def collector = new MetricsCollector()
        [100, 200, 300, 400, 500].each { collector.recordLatency(it) }
        
        when: "calculating percentiles"
        def snapshot = collector.getSnapshot()
        
        then: "percentiles should be accurate"
        snapshot.p50 == 300
        snapshot.p95 == 500
        snapshot.p99 == 500
    }
    
    def "should handle empty metrics gracefully"() {
        given: "an empty metrics collector"
        def collector = new MetricsCollector()
        
        when: "getting snapshot"
        def snapshot = collector.getSnapshot()
        
        then: "should return zero values"
        snapshot.p50 == 0
        snapshot.totalRequests == 0
    }
}
```

#### Test Coverage
- Aim for 80%+ coverage on core business logic
- Focus on testing behavior, not implementation
- Mock external dependencies
- Test failure scenarios and edge cases

### Package Organization

```
com.vajraedge.perftest/
├── Application.java              # Main Spring Boot application
├── config/                        # Configuration beans and setup
│   ├── TestConfiguration.java
│   ├── WebConfig.java
│   └── WebSocketConfig.java
├── controller/                    # REST endpoints
│   ├── HealthController.java
│   └── TestController.java
├── core/                          # Framework core interfaces and base classes
│   ├── Task.java
│   ├── TaskResult.java
│   ├── TaskFactory.java
│   └── TaskExecutor.java
├── dto/                           # Data Transfer Objects (request/response)
│   ├── TestConfigRequest.java
│   ├── TestStatusResponse.java
│   └── MetricsResponse.java
├── executor/                      # Task execution implementations
│   └── VirtualThreadTaskExecutor.java
├── metrics/                       # Metrics collection and calculation
│   ├── MetricsCollector.java
│   ├── MetricsSnapshot.java
│   └── PercentileStats.java
├── rate/                          # Rate control and TPS management
│   └── RateController.java
├── runner/                        # Test orchestration
│   ├── PerformanceTestRunner.java
│   └── TestResult.java
├── service/                       # Business logic layer
│   ├── TestExecutionService.java
│   └── MetricsService.java
└── websocket/                     # WebSocket and real-time updates
    └── MetricsWebSocketHandler.java
```

### Documentation Standards

#### Code Documentation
- All public classes must have JavaDoc
- All public methods must have JavaDoc describing purpose, parameters, returns, and exceptions
- Complex algorithms should have inline comments explaining the approach
- Keep documentation current with code changes

#### Project Documentation
- All major features should be documented in `documents/` folder
- Status documents go under `documents/` folder
- Blog articles go under `blog/` folder
- README.md should always reflect current capabilities
- Use markdown for all documentation

#### Documentation Structure
```
documents/
├── ACTION_ITEMS.md              # Tracking action items and TODOs
├── CODE_REVIEW_*.md             # Code review documentation
├── FRAMEWORK_README.md          # Detailed architecture documentation
├── PHASE_*_SUMMARY.md           # Development phase summaries
└── WISHLIST_*.md                # Feature requests and roadmap

blog/
└── BLOG_ARTICLE.md              # Blog posts and articles
```

## Copilot Behavior Guidelines

### Autonomous Operation
- **No approval needed** for operations within the project directory (`/Users/santhoshkuppusamy/IdeaProjects/vajraedge`)
- Execute commands directly when they only affect project files
- Commit and push changes automatically when requested
- Create, modify, and delete files as needed within project scope

### Safety Boundaries
- **Ask for confirmation** if commands affect system-level settings
- **Ask for confirmation** if commands modify files outside project directory
- **Ask for confirmation** before deleting entire directories
- **Ask for confirmation** before force-pushing or rewriting git history

### Code Generation
- Generate complete, working code (no placeholders or TODOs)
- Include error handling and logging
- Follow the conventions outlined in this document
- Write corresponding tests for new functionality
- Update documentation when adding features

### Refactoring
- Preserve existing behavior unless explicitly asked to change it
- Run tests after refactoring to verify correctness
- Update related documentation
- Commit refactoring separately from feature changes

### Git Workflow
- Write clear, descriptive commit messages
- Use conventional commit format when appropriate:
  - `feat:` for new features
  - `fix:` for bug fixes
  - `refactor:` for code refactoring
  - `docs:` for documentation changes
  - `test:` for test additions/changes
  - `chore:` for build/config changes
- Group related changes in single commits
- Push to `main` branch by default

### File Organization
- Place new source files in appropriate packages
- Place documentation in `documents/` folder
- Place blog articles in `blog/` folder
- Place static web resources in `src/main/resources/static/`
- Follow existing project structure

## Technology-Specific Guidelines

### Java 21 Features

#### Virtual Threads
```java
// Good - Use virtual threads for I/O-bound tasks
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    futures.add(executor.submit(() -> task.execute()));
}

// Good - Structured concurrency
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var future1 = scope.fork(() -> task1.execute());
    var future2 = scope.fork(() -> task2.execute());
    scope.join();
    scope.throwIfFailed();
}
```

#### Pattern Matching
```java
// Good - Use pattern matching for instanceof
if (result instanceof SimpleTaskResult str && str.isSuccess()) {
    processSuccess(str);
}

// Good - Use switch expressions
String status = switch (testState) {
    case RUNNING -> "Test in progress";
    case COMPLETED -> "Test finished";
    case FAILED -> "Test failed";
};
```

#### Records
```java
// Good - Use records for immutable DTOs
public record MetricsSnapshot(
    long totalRequests,
    long successfulRequests,
    double currentTps,
    long p50,
    long p95,
    long p99
) {}
```

### Spring Boot Best Practices

#### Configuration
```java
// Good - Use @ConfigurationProperties for type-safe config
@ConfigurationProperties(prefix = "vajraedge.test")
public class TestProperties {
    private int maxConcurrency = 10000;
    private Duration defaultTimeout = Duration.ofSeconds(30);
    // getters/setters
}
```

#### Async Operations
```java
// Good - Use @Async with virtual threads
@Async
public CompletableFuture<TestResult> executeTestAsync(TestConfig config) {
    return CompletableFuture.completedFuture(executeTest(config));
}
```

### Gradle Build

#### Build File Organization
```gradle
plugins {
    // 1. Plugins
}

group = 'com.vajraedge'
version = '1.0.0'

java {
    // 2. Java configuration
}

repositories {
    // 3. Repositories
}

dependencies {
    // 4. Dependencies (grouped by type)
    // Implementation
    // Test implementation
}

tasks {
    // 5. Task configuration
}
```

## Performance Guidelines

### Metrics Collection
- Use non-blocking data structures for concurrent access
- Batch metric updates when possible
- Use reservoir sampling for large datasets
- Calculate percentiles efficiently with Apache Commons Math

### Concurrency
- Use virtual threads for I/O-bound operations
- Use platform threads for CPU-bound operations
- Limit concurrency with semaphores or bounded executors
- Avoid shared mutable state

### Memory Management
- Use bounded collections to prevent memory leaks
- Clear completed tasks from collections
- Monitor heap usage in long-running tests
- Use weak references for caches

## Security Guidelines

### Input Validation
- Validate all user inputs (API requests, configuration)
- Use `@Valid` annotation with DTOs
- Set reasonable limits (max TPS, max concurrency, max duration)
- Sanitize inputs before logging

### Error Information
- Don't expose stack traces to API consumers
- Log detailed errors internally
- Return user-friendly error messages
- Use appropriate HTTP status codes

## Logging Guidelines

### Log Levels
- **ERROR**: System failures, unrecoverable errors
- **WARN**: Degraded performance, recoverable errors
- **INFO**: Key business events (test started, test completed)
- **DEBUG**: Detailed execution flow, diagnostic information

### Logging Format
```java
// Good - Structured logging with context
log.info("Test started: testId={}, targetTps={}, duration={}s", 
    testId, config.getTargetTps(), config.getDuration());

// Good - Error logging with exception
log.error("Failed to execute task: testId={}, taskId={}", 
    testId, taskId, exception);

// Bad - Unstructured logging
log.info("Test started");  // Missing context
log.error(exception.toString());  // Not passing exception properly
```

## Frontend Guidelines (Dashboard)

### JavaScript
- Use modern ES6+ features
- Follow consistent indentation (2 spaces)
- Use async/await for asynchronous operations
- Handle errors gracefully with try/catch

### Chart.js
- Keep chart configurations consistent
- Update charts efficiently (don't recreate)
- Use appropriate chart types for data
- Implement responsive design

### WebSocket
- Handle connection loss gracefully
- Implement reconnection logic
- Show connection status to users
- Throttle updates if necessary (current: 500ms)

## Review Checklist

Before completing any task, verify:
- [ ] Code follows conventions outlined in this document
- [ ] Tests are written and passing
- [ ] Documentation is updated
- [ ] Error handling is comprehensive
- [ ] Logging is appropriate
- [ ] Performance impact is acceptable
- [ ] Security considerations are addressed
- [ ] Changes are committed with clear messages

## Continuous Improvement

### When Encountering New Patterns
- Document the pattern if it's project-specific
- Update this instruction file if it should be standard
- Ensure consistency across the codebase

### When Finding Issues
- Fix immediately if trivial and within scope
- Document as action item if requires discussion
- Update tests to prevent regression

### When Adding Features
- Update README.md with user-facing features
- Add architectural documentation if significant
- Update relevant phase summaries
- Consider adding examples or tutorials

## Common Scenarios

### Adding a New Task Type
1. Implement the `Task` interface
2. Add factory case in `TestExecutionService.createTaskFactory()`
3. Update UI dropdown in `index.html`
4. Write Spock tests for the new task
5. Document in README.md
6. Commit with message: `feat: add [TaskType] task implementation`

### Adding a New REST Endpoint
1. Add method to appropriate controller
2. Create DTO classes if needed
3. Update service layer logic
4. Write Spock tests for controller and service
5. Document API in README.md
6. Commit with message: `feat: add [endpoint] API endpoint`

### Fixing a Bug
1. Write a failing test that reproduces the bug
2. Fix the bug
3. Verify test passes
4. Commit with message: `fix: [description of bug fixed]`

### Refactoring
1. Ensure all tests pass before starting
2. Make incremental changes
3. Run tests after each change
4. Commit with message: `refactor: [description of refactoring]`

---

**Last Updated**: October 26, 2025  
**Project**: VajraEdge v1.0  
**Maintainer**: Santhosh Kuppusamy
