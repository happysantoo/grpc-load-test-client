# Simple API - VajraEdge Demo Application

A lightweight Spring Boot application designed to demonstrate VajraEdge load testing capabilities.

## Overview

This is a minimal REST API that returns product data with a simulated database query delay. Perfect for:
- Learning how to use VajraEdge
- Demonstrating load testing concepts
- Benchmarking performance characteristics
- Testing HTTP request tasks

## Endpoint

### GET /api/products

Returns a list of 10 products with detailed information.

**URL**: `http://localhost:8081/api/products`

**Response Format**: JSON array

**Response Size**: ~5KB

**Latency**: 10ms simulated delay (mimics database query)

**Example Response**:
```json
[
  {
    "id": 1,
    "name": "Premium Wireless Headphones",
    "description": "High-quality over-ear headphones with active noise cancellation, 30-hour battery life...",
    "price": 299.99,
    "category": "Electronics",
    "tags": ["audio", "wireless", "premium", "noise-cancelling"],
    "inStock": true,
    "quantity": 150,
    "rating": {
      "average": 4.7,
      "totalReviews": 1243
    },
    "createdAt": "2025-07-28T10:15:30",
    "updatedAt": "2025-10-21T14:22:10"
  },
  ...
]
```

## Quick Start

### Build

```bash
../../gradlew build
```

### Run

```bash
../../gradlew bootRun
```

The application starts on **port 8081**.

### Test

```bash
curl http://localhost:8081/api/products
```

Or open in browser: http://localhost:8081/api/products

## Configuration

Edit `src/main/resources/application.properties`:

```properties
server.port=8081                    # Change port if needed
spring.application.name=simple-api
```

## Technical Stack

- **Java**: 21
- **Spring Boot**: 3.5.7
- **Dependencies**: spring-boot-starter-web only
- **Build Tool**: Gradle

## Performance Characteristics

Under load testing:

- **Expected P50 Latency**: ~10-15ms
- **Expected P95 Latency**: ~15-25ms
- **Expected P99 Latency**: ~20-35ms
- **Max Sustainable TPS**: 1000+ (on modern hardware)
- **Success Rate**: 99.9%+

## Files

```
simple-api/
├── build.gradle                                    # Gradle build configuration
├── src/
│   ├── main/
│   │   ├── java/com/vajraedge/samples/simpleapi/
│   │   │   ├── SimpleApiApplication.java          # Main application class
│   │   │   ├── controller/
│   │   │   │   └── ProductController.java         # Products REST endpoint
│   │   │   └── model/
│   │   │       └── Product.java                   # Product record
│   │   └── resources/
│   │       └── application.properties             # Application configuration
```

## Customization Ideas

### Change Latency

Edit `ProductController.java`:
```java
Thread.sleep(10);  // Change to 50 for 50ms delay
```

### Add More Products

Edit `ProductController.java` and add more `Product` objects to the list.

### Add More Endpoints

Create new methods in `ProductController`:

```java
@GetMapping("/{id}")
public Product getProduct(@PathVariable Long id) {
    // Implementation
}

@PostMapping
public Product createProduct(@RequestBody Product product) {
    // Implementation
}
```

### Add Random Failures

Simulate errors for testing:

```java
@GetMapping
public List<Product> getProducts() throws Exception {
    if (Math.random() < 0.05) {  // 5% failure rate
        throw new RuntimeException("Random failure");
    }
    // ... rest of implementation
}
```

## Integration with VajraEdge

To load test this application:

1. **Start this application**: `../../gradlew bootRun`
2. **Start VajraEdge**: From project root, run `./gradlew bootRun`
3. **Open VajraEdge Dashboard**: http://localhost:8080
4. **Configure Test**:
   - Task Type: HTTP Request
   - Task Parameter: `http://localhost:8081/api/products`
   - Target TPS: 100
   - Max Concurrency: 50
5. **Start Test** and monitor metrics

See [DEMO_GUIDE.md](../DEMO_GUIDE.md) for detailed instructions.

## License

Part of the VajraEdge project - for demonstration purposes.
