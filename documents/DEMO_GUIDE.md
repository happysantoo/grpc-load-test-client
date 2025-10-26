# VajraEdge Demo: Load Testing a Spring Boot Application

This demo shows how to use VajraEdge to performance test a real Spring Boot REST API.

## What's Included

- **Sample API**: A Spring Boot 3.5.7 application with a Products endpoint
- **HTTP Task**: Built-in HTTP load testing capability in VajraEdge
- **Complete Demo**: Step-by-step instructions to run and test

## Architecture

```
┌─────────────────────────────────────────┐
│  VajraEdge (Port 8080)                  │
│  - Dashboard UI                         │
│  - Performance Test Framework           │
│  - Real-time Metrics & Charts           │
└───────────┬─────────────────────────────┘
            │
            │ HTTP Requests
            │ (Load Testing)
            ▼
┌─────────────────────────────────────────┐
│  Sample API (Port 8081)                 │
│  - GET /api/products                    │
│  - Returns 10 products (JSON)           │
│  - 10ms simulated latency               │
└─────────────────────────────────────────┘
```

## Sample Application Details

### Endpoint: GET /api/products

**URL**: `http://localhost:8081/api/products`

**Response**: Returns an array of 10 product objects with the following structure:

```json
[
  {
    "id": 1,
    "name": "Premium Wireless Headphones",
    "description": "High-quality over-ear headphones with active noise cancellation...",
    "price": 299.99,
    "category": "Electronics",
    "tags": ["audio", "wireless", "premium", "noise-cancelling"],
    "inStock": true,
    "quantity": 150,
    "rating": {
      "average": 4.7,
      "totalReviews": 1243
    },
    "createdAt": "2025-07-28T...",
    "updatedAt": "2025-10-21T..."
  },
  ...
]
```

**Response Size**: ~5KB per request (substantial JSON payload)

**Performance Characteristics**:
- Simulated database query: 10ms delay
- Consistent response time
- Ideal for testing baseline performance

## Step-by-Step Demo Instructions

### Prerequisites

- Java 21 or higher
- Two terminal windows

### Step 1: Start the Sample Application

Open **Terminal 1** and run:

```bash
# Navigate to the sample API directory
cd samples/simple-api

# Build the application (first time only)
../../gradlew build

# Start the server (runs on port 8081)
../../gradlew bootRun
```

**Expected output**:
```
Started SimpleApiApplication in 2.3 seconds
```

**Verify it's working**:
```bash
curl http://localhost:8081/api/products
```

You should see a JSON array of 10 products.

### Step 2: Start VajraEdge

Open **Terminal 2** and run:

```bash
# Navigate to the project root
cd /Users/santhoshkuppusamy/IdeaProjects/vajraedge

# Start VajraEdge (runs on port 8080)
./gradlew bootRun
```

**Expected output**:
```
Started Application in 3.1 seconds
```

### Step 3: Open the Dashboard

Open your browser and navigate to:

```
http://localhost:8080
```

You should see the **VajraEdge Performance Testing Dashboard**.

### Step 4: Configure Your First Load Test

In the dashboard's **Test Configuration** panel:

1. **Target TPS**: `100` (100 requests per second)
2. **Max Concurrency**: `50` (50 parallel requests max)
3. **Test Duration**: `60` (run for 60 seconds)
4. **Ramp-Up Duration**: `10` (gradually increase load over 10 seconds)
5. **Task Type**: Select `HTTP Request`
6. **Task Parameter**: `http://localhost:8081/api/products`

### Step 5: Start the Test

Click the **"Start Test"** button.

**What happens**:
1. Test starts immediately
2. Connection status shows **"Connected"** (green badge)
3. Metrics panel appears showing real-time data
4. TPS gradually increases from 0 to 100 over 10 seconds
5. Charts update every 500ms

### Step 6: Monitor Real-Time Metrics

Watch the dashboard as it updates:

#### TPS Chart
- Shows actual transactions per second
- Should smoothly ramp from 0 to ~100 TPS
- Then sustain at ~100 TPS for remaining 50 seconds

#### Latency Percentiles Chart
- **P50 (Median)**: Should be ~10-15ms
- **P95**: Should be ~15-20ms
- **P99**: Should be ~20-30ms

These numbers include:
- 10ms simulated delay in the API
- Network overhead (negligible on localhost)
- JSON serialization/deserialization

#### Current Metrics Panel
- **Current TPS**: Real-time throughput
- **Active Tasks**: Currently executing requests
- **Total Requests**: Cumulative count
- **Success Rate**: Should be ~100%

#### Detailed Percentiles Table
- Shows P50, P75, P90, P95, P99, P99.9
- All values in milliseconds
- Updated every 500ms

### Step 7: Observe the Test Progress

During the 60-second test:

**First 10 seconds (Ramp-Up)**:
- TPS increases gradually: 10, 20, 30... up to 100
- Latency should remain stable around 10-15ms
- Active tasks increase proportionally

**Next 50 seconds (Sustained Load)**:
- TPS stays at ~100
- Latency remains consistent
- System demonstrates stable performance

**Terminal 1 (Sample API)**:
- You'll see Spring Boot access logs showing incoming requests
- All should return 200 status codes

### Step 8: Wait for Completion

After 60 seconds:
- Test automatically stops
- Final metrics are displayed
- You can review the percentile statistics

Or click **"Stop Test"** anytime to end early.

## Understanding the Results

### What Good Performance Looks Like

For this simple API with 10ms sleep:

- **P50**: 10-15ms (median includes sleep time)
- **P95**: 15-25ms (95% of requests)
- **P99**: 20-35ms (99% of requests)
- **Success Rate**: 99.9%+ (all requests succeed)

### What the Metrics Tell You

1. **Consistent P50/P95/P99**: System handles load well
2. **P99 close to P95**: Few outliers, predictable performance
3. **Large P99 spikes**: Possible GC pauses or resource contention
4. **Dropping TPS**: System can't keep up with target rate

## Try Different Scenarios

### Scenario 1: Light Load (Baseline)
```
Target TPS: 50
Max Concurrency: 25
Duration: 30 seconds
Ramp-Up: 5 seconds
```
**Expected**: Very stable latency, low resource usage

### Scenario 2: Moderate Load
```
Target TPS: 200
Max Concurrency: 100
Duration: 60 seconds
Ramp-Up: 10 seconds
```
**Expected**: Slightly higher latency, still stable

### Scenario 3: High Load
```
Target TPS: 500
Max Concurrency: 200
Duration: 120 seconds
Ramp-Up: 20 seconds
```
**Expected**: Test the limits of the simple API

### Scenario 4: Stress Test
```
Target TPS: 1000
Max Concurrency: 500
Duration: 60 seconds
Ramp-Up: 10 seconds
```
**Expected**: See how the system behaves under extreme load

## Advanced Usage

### Testing Different Endpoints

You can test any HTTP endpoint by changing the **Task Parameter**:

```
http://localhost:8081/api/products        # Sample API
https://httpbin.org/get                   # Public test API
https://httpbin.org/delay/1               # Simulated 1s delay
https://api.github.com/users/octocat      # Real public API
```

### Multiple Concurrent Tests

VajraEdge supports running multiple tests simultaneously:

1. Start a test with the sample API
2. Configure another test with a different endpoint
3. Both run independently with separate metrics

### Using the REST API

You can also start tests programmatically:

```bash
curl -X POST http://localhost:8080/api/tests \
  -H "Content-Type: application/json" \
  -d '{
    "targetTps": 100,
    "maxConcurrency": 50,
    "testDurationSeconds": 60,
    "rampUpDurationSeconds": 10,
    "taskType": "HTTP",
    "taskParameter": "http://localhost:8081/api/products"
  }'
```

Response:
```json
{
  "testId": "abc123...",
  "status": "RUNNING"
}
```

## Troubleshooting

### Sample API Not Responding

**Problem**: Connection refused to `localhost:8081`

**Solution**:
```bash
# Check if the sample API is running
curl http://localhost:8081/api/products

# If not running, start it:
cd samples/simple-api
../../gradlew bootRun
```

### VajraEdge Dashboard Not Loading

**Problem**: Can't access `localhost:8080`

**Solution**:
```bash
# Check if VajraEdge is running
curl http://localhost:8080/actuator/health

# If not running, start it:
./gradlew bootRun
```

### Low TPS Not Reaching Target

**Problem**: Actual TPS is lower than target

**Possible causes**:
1. **API is too slow**: The 10ms delay + processing time limits max TPS
2. **Concurrency too low**: Increase max concurrency
3. **Network issues**: Check localhost connectivity

### WebSocket Not Connecting

**Problem**: Dashboard shows "Disconnected"

**Solution**:
1. Refresh the browser page
2. Check browser console for errors
3. Ensure VajraEdge is fully started

## What's Next?

After completing this demo, try:

1. **Modify the Sample API**: 
   - Change the sleep duration
   - Add more data to the response
   - Introduce random failures

2. **Create Your Own Tasks**:
   - Test database queries
   - Test message queue operations
   - Test custom business logic

3. **Integrate with CI/CD**:
   - Run tests in Jenkins/GitHub Actions
   - Set performance thresholds
   - Generate reports

## Demo Cleanup

When finished:

1. Stop VajraEdge: `Ctrl+C` in Terminal 2
2. Stop Sample API: `Ctrl+C` in Terminal 1

## Summary

You've successfully:
- ✅ Started a Spring Boot sample application
- ✅ Launched VajraEdge performance framework
- ✅ Configured and executed a load test
- ✅ Monitored real-time metrics and charts
- ✅ Analyzed latency percentiles
- ✅ Understood performance characteristics

**VajraEdge makes load testing simple, visual, and powerful!**

---

## Technical Details

### Sample Application
- **Framework**: Spring Boot 3.5.7
- **Java**: 21
- **Port**: 8081
- **Endpoint**: GET /api/products
- **Response**: 10 products, ~5KB JSON
- **Latency**: 10ms simulated delay

### VajraEdge
- **Framework**: Spring Boot 3.5.7
- **Java**: 21 (Virtual Threads)
- **Port**: 8080
- **Features**: Real-time WebSocket metrics, Chart.js visualization
- **Update Frequency**: 500ms

### Performance Characteristics
- **Max TPS** (sample API): ~1000+ on modern hardware
- **Latency**: P50 ~10ms, P95 ~20ms, P99 ~30ms
- **Concurrency**: Tested up to 500 concurrent requests
- **Stability**: 99.9%+ success rate under normal load
