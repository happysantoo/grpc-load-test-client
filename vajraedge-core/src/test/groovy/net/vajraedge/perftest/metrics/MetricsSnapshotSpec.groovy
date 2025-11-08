package net.vajraedge.perftest.metrics

import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.time.Instant

/**
 * Comprehensive tests for MetricsSnapshot
 */
class MetricsSnapshotSpec extends Specification {

    def "should create snapshot with all metrics"() {
        given:
        Instant startTime = Instant.now()
        Duration elapsed = Duration.ofSeconds(10)
        double[] percentileKeys = [0.50, 0.95, 0.99] as double[]
        double[] percentileValues = [10.0, 50.0, 100.0] as double[]
        PercentileStats percentiles = new PercentileStats(percentileKeys, percentileValues)
        Map<String, Long> errorCounts = ["Error1": 5L, "Error2": 3L]

        when:
        MetricsSnapshot snapshot = new MetricsSnapshot(
            startTime, elapsed, 1000L, 950L, 50L, 100.0, 15.5, 95.0, percentiles, errorCounts
        )

        then:
        snapshot.getStartTime() == startTime
        snapshot.getElapsed() == elapsed
        snapshot.getTotalTasks() == 1000L
        snapshot.getSuccessfulTasks() == 950L
        snapshot.getFailedTasks() == 50L
        snapshot.getTps() == 100.0
        snapshot.getAvgLatencyMs() == 15.5
        snapshot.getSuccessRate() == 95.0
        snapshot.getPercentiles() == percentiles
        snapshot.getErrorCounts() == errorCounts
    }

    @Unroll
    def "should handle different metric scenarios: #scenario"() {
        given:
        double[] percentileKeys = [0.50, 0.95, 0.99] as double[]
        double[] percentileValues = [p50, p95, p99] as double[]
        PercentileStats percentiles = new PercentileStats(percentileKeys, percentileValues)

        when:
        MetricsSnapshot snapshot = new MetricsSnapshot(
            Instant.now(), Duration.ofSeconds(duration), total, successful, failed,
            tps, avgLatency, successRate, percentiles, [:]
        )

        then:
        snapshot.getTotalTasks() == total
        snapshot.getSuccessfulTasks() == successful
        snapshot.getFailedTasks() == failed
        snapshot.getTps() == tps
        snapshot.getAvgLatencyMs() == avgLatency
        snapshot.getSuccessRate() == successRate

        where:
        scenario        | duration | total | successful | failed | tps   | avgLatency | successRate | p50  | p95  | p99
        "no requests"   | 0        | 0     | 0          | 0      | 0.0   | 0.0        | 0.0         | 0.0  | 0.0  | 0.0
        "all success"   | 10       | 1000  | 1000       | 0      | 100.0 | 10.0       | 100.0       | 5.0  | 20.0 | 30.0
        "all failures"  | 5        | 500   | 0          | 500    | 100.0 | 15.0       | 0.0         | 10.0 | 50.0 | 100.0
        "mixed results" | 30       | 5000  | 4750       | 250    | 166.7 | 8.5        | 95.0        | 4.0  | 15.0 | 25.0
    }

    def "should store start time and elapsed duration"() {
        given:
        Instant startTime = Instant.now().minusSeconds(30)
        Duration elapsed = Duration.ofSeconds(30)
        double[] percentileKeys = [0.50, 0.95, 0.99] as double[]
        double[] percentileValues = [5.0, 20.0, 50.0] as double[]
        PercentileStats percentiles = new PercentileStats(percentileKeys, percentileValues)

        when:
        MetricsSnapshot snapshot = new MetricsSnapshot(
            startTime, elapsed, 100L, 100L, 0L, 10.0, 5.0, 100.0, percentiles, [:]
        )

        then:
        snapshot.getStartTime() == startTime
        snapshot.getElapsed().getSeconds() == 30
    }

    def "should store error counts"() {
        given:
        Map<String, Long> errorCounts = [
            "ConnectionTimeout": 10L,
            "ServerError": 5L,
            "ParseError": 3L
        ]
        double[] percentileKeys = [0.50, 0.95, 0.99] as double[]
        double[] percentileValues = [5.0, 20.0, 50.0] as double[]
        PercentileStats percentiles = new PercentileStats(percentileKeys, percentileValues)

        when:
        MetricsSnapshot snapshot = new MetricsSnapshot(
            Instant.now(), Duration.ofSeconds(10), 1000L, 982L, 18L,
            100.0, 10.0, 98.2, percentiles, errorCounts
        )

        then:
        snapshot.getErrorCounts() == errorCounts
        snapshot.getErrorCounts().size() == 3
    }

    def "should include percentile stats"() {
        given:
        double[] percentileKeys = [0.50, 0.75, 0.90, 0.95, 0.99, 0.999] as double[]
        double[] percentileValues = [5.0, 10.0, 20.0, 30.0, 50.0, 100.0] as double[]
        PercentileStats percentiles = new PercentileStats(percentileKeys, percentileValues)

        when:
        MetricsSnapshot snapshot = new MetricsSnapshot(
            Instant.now(), Duration.ofSeconds(30), 10000L, 9800L, 200L,
            333.3, 12.5, 98.0, percentiles, [:]
        )

        then:
        snapshot.getPercentiles() != null
        snapshot.getPercentiles().getPercentile(0.50) == 5.0
        snapshot.getPercentiles().getPercentile(0.95) == 30.0
        snapshot.getPercentiles().getPercentile(0.99) == 50.0
    }

    def "should generate meaningful toString"() {
        given:
        double[] percentileKeys = [0.50, 0.95, 0.99] as double[]
        double[] percentileValues = [10.0, 50.0, 100.0] as double[]
        PercentileStats percentiles = new PercentileStats(percentileKeys, percentileValues)

        when:
        MetricsSnapshot snapshot = new MetricsSnapshot(
            Instant.now(), Duration.ofSeconds(10), 1000L, 950L, 50L,
            100.0, 15.5, 95.0, percentiles, [:]
        )
        String str = snapshot.toString()

        then:
        str.contains("1000")
        str.contains("95.0")
        str.contains("100.0")
        str.contains("15.5")
        str.contains("50.0")
        str.contains("100.0")
    }

    @Unroll
    def "should handle edge case values: #scenario"() {
        given:
        double[] percentileKeys = [0.50, 0.95, 0.99] as double[]
        double[] percentileValues = [p50, p95, p99] as double[]
        PercentileStats percentiles = new PercentileStats(percentileKeys, percentileValues)

        when:
        MetricsSnapshot snapshot = new MetricsSnapshot(
            Instant.now(), Duration.ofMillis(duration), total, successful, failed,
            tps, avgLatency, successRate, percentiles, [:]
        )

        then:
        snapshot.getTotalTasks() == total
        snapshot.getTps() == tps

        where:
        scenario          | duration | total      | successful | failed | tps       | avgLatency | successRate | p50   | p95    | p99
        "zero everything" | 0        | 0          | 0          | 0      | 0.0       | 0.0        | 0.0         | 0.0   | 0.0    | 0.0
        "high throughput" | 10000    | 1000000    | 1000000    | 0      | 100000.0  | 1.0        | 100.0       | 0.5   | 2.0    | 5.0
        "low latency"     | 5000     | 5000       | 5000       | 0      | 1000.0    | 0.123      | 100.0       | 0.1   | 0.3    | 0.5
    }

    def "should handle empty error counts map"() {
        given:
        double[] percentileKeys = [0.50, 0.95, 0.99] as double[]
        double[] percentileValues = [5.0, 20.0, 50.0] as double[]
        PercentileStats percentiles = new PercentileStats(percentileKeys, percentileValues)

        when:
        MetricsSnapshot snapshot = new MetricsSnapshot(
            Instant.now(), Duration.ofSeconds(10), 1000L, 1000L, 0L,
            100.0, 5.0, 100.0, percentiles, [:]
        )

        then:
        snapshot.getErrorCounts().isEmpty()
    }

    def "should handle null percentiles"() {
        when:
        MetricsSnapshot snapshot = new MetricsSnapshot(
            Instant.now(), Duration.ofSeconds(10), 100L, 100L, 0L,
            10.0, 5.0, 100.0, null, [:]
        )

        then:
        snapshot.getPercentiles() == null
    }

    @Unroll
    def "should store precise values: tps=#tps, avgLatency=#avgLatency, successRate=#successRate"() {
        given:
        double[] percentileKeys = [0.50, 0.95, 0.99] as double[]
        double[] percentileValues = [1.0, 2.0, 3.0] as double[]
        PercentileStats percentiles = new PercentileStats(percentileKeys, percentileValues)

        when:
        MetricsSnapshot snapshot = new MetricsSnapshot(
            Instant.now(), Duration.ofSeconds(10), 1000L, 950L, 50L,
            tps, avgLatency, successRate, percentiles, [:]
        )

        then:
        snapshot.getTps() == tps
        snapshot.getAvgLatencyMs() == avgLatency
        snapshot.getSuccessRate() == successRate

        where:
        tps      | avgLatency | successRate
        0.0      | 0.0        | 0.0
        1.5      | 0.123      | 99.99
        1000.0   | 100.456    | 100.0
        99999.99 | 999.999    | 50.5
    }

    def "should be immutable"() {
        given:
        Map<String, Long> errorCounts = ["Error": 1L]
        double[] percentileKeys = [0.50, 0.95, 0.99] as double[]
        double[] percentileValues = [5.0, 20.0, 50.0] as double[]
        PercentileStats percentiles = new PercentileStats(percentileKeys, percentileValues)
        
        MetricsSnapshot snapshot = new MetricsSnapshot(
            Instant.now(), Duration.ofSeconds(10), 100L, 100L, 0L,
            10.0, 5.0, 100.0, percentiles, errorCounts
        )

        when:
        errorCounts.put("NewError", 2L)

        then:
        // Original snapshot should not be affected
        snapshot.getErrorCounts().size() >= 1
    }

    def "should handle very long elapsed duration"() {
        given:
        Duration veryLongDuration = Duration.ofDays(365)
        double[] percentileKeys = [0.50, 0.95, 0.99] as double[]
        double[] percentileValues = [5.0, 20.0, 50.0] as double[]
        PercentileStats percentiles = new PercentileStats(percentileKeys, percentileValues)

        when:
        MetricsSnapshot snapshot = new MetricsSnapshot(
            Instant.now().minus(veryLongDuration), veryLongDuration,
            1000000L, 1000000L, 0L, 31.71, 5.0, 100.0, percentiles, [:]
        )

        then:
        snapshot.getElapsed().toDays() == 365
    }

    def "should handle very short elapsed duration"() {
        given:
        Duration shortDuration = Duration.ofNanos(1)
        double[] percentileKeys = [0.50, 0.95, 0.99] as double[]
        double[] percentileValues = [5.0, 20.0, 50.0] as double[]
        PercentileStats percentiles = new PercentileStats(percentileKeys, percentileValues)

        when:
        MetricsSnapshot snapshot = new MetricsSnapshot(
            Instant.now(), shortDuration, 1L, 1L, 0L, 1000000000.0, 0.001, 100.0, percentiles, [:]
        )

        then:
        snapshot.getElapsed().toNanos() == 1
    }
}
