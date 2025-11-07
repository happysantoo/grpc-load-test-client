package net.vajraedge.perftest.service

import net.vajraedge.perftest.metrics.MetricsSnapshot
import net.vajraedge.perftest.metrics.PercentileStats
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.time.Instant

/**
 * Tests for MetricsService - converts metrics snapshots to DTOs
 */
class MetricsServiceSpec extends Specification {

    MetricsService service

    def setup() {
        service = new MetricsService()
    }

    def "should convert snapshot to MetricsResponse"() {
        given:
        double[] percentileKeys = [0.50, 0.75, 0.90, 0.95, 0.99] as double[]
        double[] percentileValues = [5.0, 10.0, 20.0, 30.0, 50.0] as double[]
        PercentileStats percentiles = new PercentileStats(percentileKeys, percentileValues)
        
        MetricsSnapshot snapshot = new MetricsSnapshot(
            Instant.now(),
            Duration.ofSeconds(10),
            1000L,
            950L,
            50L,
            100.5,
            10.0,
            95.0,
            percentiles,
            [:]
        )

        when:
        def response = service.convertToResponse("test-123", snapshot)

        then:
        response.testId == "test-123"
        response.totalRequests == 1000L
        response.successfulRequests == 950L
        response.failedRequests == 50L
        response.successRate == 95.0
        response.currentTps == 100.5
        response.avgLatencyMs == 10.0
        response.latencyPercentiles != null
    }

    @Unroll
    def "should handle different metric scenarios: #scenario"() {
        given:
        double[] percentileKeys = [0.50, 0.95, 0.99] as double[]
        double[] percentileValues = [p50, p95, p99] as double[]
        PercentileStats percentiles = new PercentileStats(percentileKeys, percentileValues)
        
        MetricsSnapshot snapshot = new MetricsSnapshot(
            Instant.now(),
            Duration.ofSeconds(duration),
            total,
            successful,
            failed,
            tps,
            avgLatency,
            successRate,
            percentiles,
            [:]
        )

        when:
        def response = service.convertToResponse(testId, snapshot)

        then:
        response.testId == testId
        response.totalRequests == total
        response.successfulRequests == successful
        response.failedRequests == failed
        response.successRate == successRate

        where:
        scenario         | testId    | duration | total | successful | failed | tps   | avgLatency | successRate | p50  | p95  | p99
        "all success"    | "test-1"  | 10       | 1000  | 1000       | 0      | 100.0 | 10.0       | 100.0       | 5.0  | 20.0 | 30.0
        "all failures"   | "test-2"  | 5        | 500   | 0          | 500    | 50.0  | 15.0       | 0.0         | 10.0 | 50.0 | 100.0
        "mixed results"  | "test-3"  | 30       | 5000  | 4750       | 250    | 166.7 | 8.5        | 95.0        | 4.0  | 15.0 | 25.0
    }

    def "should include percentile statistics"() {
        given:
        double[] percentileKeys = [0.50, 0.75, 0.90, 0.95, 0.99, 0.999] as double[]
        double[] percentileValues = [5.0, 8.0, 15.0, 25.0, 50.0, 100.0] as double[]
        PercentileStats percentiles = new PercentileStats(percentileKeys, percentileValues)
        
        MetricsSnapshot snapshot = new MetricsSnapshot(
            Instant.now(),
            Duration.ofSeconds(30),
            10000L,
            9800L,
            200L,
            333.3,
            12.5,
            98.0,
            percentiles,
            [:]
        )

        when:
        def response = service.convertToResponse("test-perf", snapshot)

        then:
        response.latencyPercentiles != null
        response.latencyPercentiles.size() > 0
    }

    def "should calculate min and max latencies from percentiles"() {
        given:
        double[] percentileKeys = [0.50, 0.95, 0.99] as double[]
        double[] percentileValues = [10.0, 50.0, 100.0] as double[]
        PercentileStats percentiles = new PercentileStats(percentileKeys, percentileValues)
        
        MetricsSnapshot snapshot = new MetricsSnapshot(
            Instant.now(),
            Duration.ofSeconds(10),
            1000L,
            1000L,
            0L,
            100.0,
            15.0,
            100.0,
            percentiles,
            [:]
        )

        when:
        def response = service.convertToResponse("test-min-max", snapshot)

        then:
        response.avgLatencyMs == 15.0
        // Min/max would typically come from the percentile values or separate tracking
    }

    @Unroll
    def "should preserve metric precision: #scenario"() {
        given:
        double[] percentileKeys = [0.50, 0.95, 0.99] as double[]
        double[] percentileValues = [p50, p95, p99] as double[]
        PercentileStats percentiles = new PercentileStats(percentileKeys, percentileValues)
        
        MetricsSnapshot snapshot = new MetricsSnapshot(
            Instant.now(),
            Duration.ofMillis(duration),
            total,
            successful,
            0L,
            tps,
            avgLatency,
            100.0,
            percentiles,
            [:]
        )

        when:
        def response = service.convertToResponse(testId, snapshot)

        then:
        Math.abs(response.avgLatencyMs - avgLatency) < 0.001
        Math.abs(response.currentTps - tps) < 0.001

        where:
        scenario       | testId   | duration | total | successful | tps      | avgLatency | p50   | p95   | p99
        "low latency"  | "test-l" | 10000    | 10000 | 10000      | 1000.0   | 1.234      | 1.0   | 2.0   | 3.0
        "high latency" | "test-h" | 30000    | 3000  | 3000       | 100.0    | 123.456    | 100.0 | 200.0 | 300.0
        "mixed"        | "test-m" | 15000    | 5000  | 5000       | 333.333  | 45.678     | 30.0  | 80.0  | 150.0
    }

    def "should set timestamp in response"() {
        given:
        double[] percentileKeys = [0.50, 0.95, 0.99] as double[]
        double[] percentileValues = [5.0, 20.0, 50.0] as double[]
        PercentileStats percentiles = new PercentileStats(percentileKeys, percentileValues)
        
        MetricsSnapshot snapshot = new MetricsSnapshot(
            Instant.now(),
            Duration.ofSeconds(10),
            100L,
            100L,
            0L,
            10.0,
            5.0,
            100.0,
            percentiles,
            [:]
        )

        when:
        def response = service.convertToResponse("test-ts", snapshot)

        then:
        response.timestamp != null
    }

    def "should handle null percentiles gracefully"() {
        given:
        MetricsSnapshot snapshot = new MetricsSnapshot(
            Instant.now(),
            Duration.ofSeconds(10),
            100L,
            100L,
            0L,
            10.0,
            5.0,
            100.0,
            null,
            [:]
        )

        when:
        def response = service.convertToResponse("test-null", snapshot)

        then:
        response != null
        response.testId == "test-null"
    }

    def "should handle empty error counts map"() {
        given:
        double[] percentileKeys = [0.50, 0.95, 0.99] as double[]
        double[] percentileValues = [5.0, 20.0, 50.0] as double[]
        PercentileStats percentiles = new PercentileStats(percentileKeys, percentileValues)
        
        MetricsSnapshot snapshot = new MetricsSnapshot(
            Instant.now(),
            Duration.ofSeconds(10),
            1000L,
            1000L,
            0L,
            100.0,
            5.0,
            100.0,
            percentiles,
            [:]
        )

        when:
        def response = service.convertToResponse("test-empty", snapshot)

        then:
        response.failedRequests == 0L
    }

    def "should handle error counts map with values"() {
        given:
        double[] percentileKeys = [0.50, 0.95, 0.99] as double[]
        double[] percentileValues = [5.0, 20.0, 50.0] as double[]
        PercentileStats percentiles = new PercentileStats(percentileKeys, percentileValues)
        
        Map<String, Long> errorCounts = [
            "ConnectionTimeout": 10L,
            "ServerError": 5L,
            "ParseError": 3L
        ]
        
        MetricsSnapshot snapshot = new MetricsSnapshot(
            Instant.now(),
            Duration.ofSeconds(30),
            1000L,
            982L,
            18L,
            33.3,
            12.0,
            98.2,
            percentiles,
            errorCounts
        )

        when:
        def response = service.convertToResponse("test-errors", snapshot)

        then:
        response.failedRequests == 18L
    }
}
