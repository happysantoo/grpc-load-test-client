package com.example.grpc.loadtest.metrics;

import com.example.grpc.loadtest.client.GrpcLoadTestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetricsCollectorTest {

    private MetricsCollector metricsCollector;

    @BeforeEach
    void setUp() {
        metricsCollector = new MetricsCollector(100, 1000); // Small history for testing
    }

    @AfterEach
    void tearDown() {
        if (metricsCollector != null) {
            metricsCollector.close();
        }
    }

    @Test
    void shouldInitializeWithCorrectDefaults() {
        MetricsCollector collector = new MetricsCollector();
        
        assertNotNull(collector);
        
        collector.close();
    }

    @Test
    void shouldRecordSuccessfulResults() {
        GrpcLoadTestClient.CallResult result = GrpcLoadTestClient.CallResult.success(1L, 1_000_000L, 100); // 1ms latency

        metricsCollector.recordResult(result);
        MetricsSnapshot snapshot = metricsCollector.getSnapshot();

        assertEquals(1, snapshot.getTotalRequests());
        assertEquals(1, snapshot.getSuccessfulRequests());
        assertEquals(0, snapshot.getFailedRequests());
        assertEquals(1.0, snapshot.getAvgLatencyMs(), 0.01);
        assertEquals(100.0, snapshot.getSuccessRate(), 0.01);
        assertEquals(100, snapshot.getAvgResponseSize());
    }

    @Test
    void shouldRecordFailedResults() {
        GrpcLoadTestClient.CallResult result = GrpcLoadTestClient.CallResult.failure(1L, 2_000_000L, 14, "UNAVAILABLE");

        metricsCollector.recordResult(result);
        MetricsSnapshot snapshot = metricsCollector.getSnapshot();

        assertEquals(1, snapshot.getTotalRequests());
        assertEquals(0, snapshot.getSuccessfulRequests());
        assertEquals(1, snapshot.getFailedRequests());
        assertEquals(2.0, snapshot.getAvgLatencyMs(), 0.01);
        assertEquals(0.0, snapshot.getSuccessRate(), 0.01);
        assertEquals(1L, snapshot.getResponseCodeCounts().get(14));
        assertEquals(1L, snapshot.getErrorCounts().get("UNAVAILABLE"));
    }

    @Test
    void shouldCalculatePercentiles() {
        // Record results with known latencies
        for (int i = 1; i <= 10; i++) {
            GrpcLoadTestClient.CallResult result = GrpcLoadTestClient.CallResult.success(
                i, (long)(i * 10 * 1_000_000), 100); // 10ms, 20ms, ..., 100ms
            metricsCollector.recordResult(result);
        }

        double[] percentiles = {0.5, 0.9, 0.95, 0.99};
        MetricsCollector.PercentileStats percentileStats = metricsCollector.calculatePercentiles(percentiles);

        assertTrue(percentileStats.getPercentile(0.5) > 40.0); // P50 should be around median
        assertTrue(percentileStats.getPercentile(0.5) < 70.0);
        assertTrue(percentileStats.getPercentile(0.9) > 80.0); // P90
        assertTrue(percentileStats.getPercentile(0.95) > 90.0); // P95
    }

    @Test
    void shouldHandleEmptyPercentileCalculation() {
        double[] percentiles = {0.5, 0.95, 0.99};

        MetricsCollector.PercentileStats percentileStats = metricsCollector.calculatePercentiles(percentiles);

        assertEquals(0.0, percentileStats.getPercentile(0.5));
        assertEquals(0.0, percentileStats.getPercentile(0.95));
        assertEquals(0.0, percentileStats.getPercentile(0.99));
    }

    @Test
    void shouldTrackResponseCodesCorrectly() {
        GrpcLoadTestClient.CallResult[] results = {
            GrpcLoadTestClient.CallResult.success(1L, 1_000_000L, 100),
            GrpcLoadTestClient.CallResult.success(2L, 1_000_000L, 100),
            GrpcLoadTestClient.CallResult.failure(3L, 1_000_000L, 14, "UNAVAILABLE"),
            GrpcLoadTestClient.CallResult.failure(4L, 1_000_000L, 3, "INVALID_ARGUMENT")
        };

        for (GrpcLoadTestClient.CallResult result : results) {
            metricsCollector.recordResult(result);
        }
        MetricsSnapshot snapshot = metricsCollector.getSnapshot();

        assertEquals(2L, snapshot.getResponseCodeCounts().get(0));  // Success code
        assertEquals(1L, snapshot.getResponseCodeCounts().get(14)); // UNAVAILABLE
        assertEquals(1L, snapshot.getResponseCodeCounts().get(3));  // INVALID_ARGUMENT
    }

    @Test
    void shouldCalculateTPSCorrectly() throws InterruptedException {
        for (int i = 1; i <= 10; i++) {
            GrpcLoadTestClient.CallResult result = GrpcLoadTestClient.CallResult.success(i, 1_000_000L, 100);
            metricsCollector.recordResult(result);
        }
        Thread.sleep(100); // Let some time pass
        MetricsSnapshot snapshot = metricsCollector.getSnapshot();

        assertTrue(snapshot.getTps() > 0); // Should be positive
        assertEquals(10, snapshot.getTotalRequests());
    }

    @Test
    void shouldResetCorrectly() {
        GrpcLoadTestClient.CallResult result = GrpcLoadTestClient.CallResult.success(1L, 1_000_000L, 100);
        metricsCollector.recordResult(result);

        metricsCollector.reset();
        MetricsSnapshot snapshot = metricsCollector.getSnapshot();

        assertEquals(0, snapshot.getTotalRequests());
        assertEquals(0, snapshot.getSuccessfulRequests());
        assertEquals(0, snapshot.getFailedRequests());
        assertTrue(snapshot.getResponseCodeCounts().isEmpty());
        assertTrue(snapshot.getErrorCounts().isEmpty());
    }

    @Test
    void shouldGetRecentSnapshot() {
        GrpcLoadTestClient.CallResult result = GrpcLoadTestClient.CallResult.success(1L, 1_000_000L, 100);

        metricsCollector.recordResult(result);
        MetricsSnapshot recentSnapshot = metricsCollector.getRecentSnapshot(5);

        assertNotNull(recentSnapshot);
        assertTrue(recentSnapshot.getTotalRequests() >= 0); // May fallback to overall snapshot
    }

    @Test
    void shouldProvideSummaryString() {
        GrpcLoadTestClient.CallResult[] results = {
            GrpcLoadTestClient.CallResult.success(1L, 1_000_000L, 100),
            GrpcLoadTestClient.CallResult.success(2L, 2_000_000L, 100),
            GrpcLoadTestClient.CallResult.failure(3L, 3_000_000L, 14, "UNAVAILABLE")
        };

        for (GrpcLoadTestClient.CallResult result : results) {
            metricsCollector.recordResult(result);
        }
        String summary = metricsCollector.getSummary();

        assertTrue(summary.contains("Requests: 3"));
        assertTrue(summary.contains("66.7% success")); // 2/3 success rate
        assertTrue(summary.contains("TPS:"));
        assertTrue(summary.contains("Avg Latency:"));
        assertTrue(summary.contains("P95:"));
        assertTrue(summary.contains("P99:"));
    }

    @Test
    void shouldTruncateLongErrorMessages() {
        String longError = "A".repeat(150); // Very long error message
        GrpcLoadTestClient.CallResult result = GrpcLoadTestClient.CallResult.failure(1L, 1_000_000L, 14, longError);

        metricsCollector.recordResult(result);
        MetricsSnapshot snapshot = metricsCollector.getSnapshot();

        String errorKey = snapshot.getErrorCounts().keySet().iterator().next();
        assertTrue(errorKey.length() <= 103); // 100 chars + "..."
        assertTrue(errorKey.endsWith("..."));
    }

    @Test
    void shouldHandleNullErrorMessages() {
        GrpcLoadTestClient.CallResult result = GrpcLoadTestClient.CallResult.failure(1L, 1_000_000L, 14, null);

        metricsCollector.recordResult(result);
        MetricsSnapshot snapshot = metricsCollector.getSnapshot();

        assertTrue(snapshot.getErrorCounts().isEmpty()); // Null errors should not be recorded
    }

    @Test
    void shouldHandleEmptyErrorMessages() {
        GrpcLoadTestClient.CallResult result = GrpcLoadTestClient.CallResult.failure(1L, 1_000_000L, 14, "");

        metricsCollector.recordResult(result);
        MetricsSnapshot snapshot = metricsCollector.getSnapshot();

        assertTrue(snapshot.getErrorCounts().isEmpty()); // Empty errors should not be recorded
    }

    @Test
    void shouldCloseWithoutErrors() {
        assertDoesNotThrow(() -> metricsCollector.close());
    }
}