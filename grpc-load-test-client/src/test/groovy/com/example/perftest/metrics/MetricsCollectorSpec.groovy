package com.example.perftest.metrics

import com.example.perftest.core.SimpleTaskResult
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Comprehensive tests for MetricsCollector
 */
class MetricsCollectorSpec extends Specification {

    MetricsCollector collector

    def setup() {
        collector = new MetricsCollector()
    }

    def cleanup() {
        collector?.close()
    }

    def "should initialize with zero metrics"() {
        when:
        MetricsSnapshot snapshot = collector.getSnapshot()

        then:
        snapshot.getTotalTasks() == 0
        snapshot.getSuccessfulTasks() == 0
        snapshot.getFailedTasks() == 0
        snapshot.getAvgLatencyMs() == 0.0
        snapshot.getSuccessRate() == 0.0
        snapshot.getTps() == 0.0
    }

    def "should record successful task result"() {
        given:
        SimpleTaskResult result = SimpleTaskResult.success(1L, 5_000_000L)

        when:
        collector.recordResult(result)
        MetricsSnapshot snapshot = collector.getSnapshot()

        then:
        snapshot.getTotalTasks() == 1
        snapshot.getSuccessfulTasks() == 1
        snapshot.getFailedTasks() == 0
        snapshot.getSuccessRate() == 100.0
    }

    def "should record failed task result"() {
        given:
        SimpleTaskResult result = SimpleTaskResult.failure(1L, 5_000_000L, "Connection timeout")

        when:
        collector.recordResult(result)
        MetricsSnapshot snapshot = collector.getSnapshot()

        then:
        snapshot.getTotalTasks() == 1
        snapshot.getSuccessfulTasks() == 0
        snapshot.getFailedTasks() == 1
        snapshot.getSuccessRate() == 0.0
        snapshot.getErrorCounts().size() > 0
    }

    def "should record multiple results"() {
        given:
        List<SimpleTaskResult> results = [
            SimpleTaskResult.success(1L, 1_000_000L),
            SimpleTaskResult.success(2L, 2_000_000L),
            SimpleTaskResult.failure(3L, 3_000_000L, "Error 1"),
            SimpleTaskResult.success(4L, 4_000_000L)
        ]

        when:
        results.each { collector.recordResult(it) }
        MetricsSnapshot snapshot = collector.getSnapshot()

        then:
        snapshot.getTotalTasks() == 4
        snapshot.getSuccessfulTasks() == 3
        snapshot.getFailedTasks() == 1
        snapshot.getSuccessRate() == 75.0
    }

    def "should calculate average latency correctly"() {
        given:
        collector.recordResult(SimpleTaskResult.success(1L, 10_000_000L))  // 10ms
        collector.recordResult(SimpleTaskResult.success(2L, 20_000_000L))  // 20ms
        collector.recordResult(SimpleTaskResult.success(3L, 30_000_000L))  // 30ms

        when:
        MetricsSnapshot snapshot = collector.getSnapshot()

        then:
        snapshot.getAvgLatencyMs() == 20.0
    }

    def "should calculate TPS based on elapsed time"() {
        given:
        (1..100).each { i ->
            collector.recordResult(SimpleTaskResult.success(i, 1_000_000L))
        }
        Thread.sleep(100)  // Wait a bit for elapsed time

        when:
        MetricsSnapshot snapshot = collector.getSnapshot()

        then:
        snapshot.getTps() > 0
        snapshot.getTotalTasks() == 100
    }

    def "should calculate percentiles from latency history"() {
        given:
        (1..100).each { i ->
            long latency = i * 1_000_000L  // 1ms to 100ms
            collector.recordResult(SimpleTaskResult.success(i, latency))
        }

        when:
        MetricsSnapshot snapshot = collector.getSnapshot()

        then:
        snapshot.getPercentiles() != null
        snapshot.getPercentiles().getPercentile(0.5) > 0
        snapshot.getPercentiles().getPercentile(0.95) > snapshot.getPercentiles().getPercentile(0.5)
        snapshot.getPercentiles().getPercentile(0.99) >= snapshot.getPercentiles().getPercentile(0.95)
    }

    def "should track error counts"() {
        given:
        collector.recordResult(SimpleTaskResult.failure(1L, 1_000_000L, "Error A"))
        collector.recordResult(SimpleTaskResult.failure(2L, 1_000_000L, "Error A"))
        collector.recordResult(SimpleTaskResult.failure(3L, 1_000_000L, "Error B"))

        when:
        MetricsSnapshot snapshot = collector.getSnapshot()

        then:
        snapshot.getErrorCounts().size() == 2
        snapshot.getErrorCounts().get("Error A") == 2L
        snapshot.getErrorCounts().get("Error B") == 1L
    }

    def "should truncate long error messages"() {
        given:
        String longError = "x" * 150
        collector.recordResult(SimpleTaskResult.failure(1L, 1_000_000L, longError))

        when:
        MetricsSnapshot snapshot = collector.getSnapshot()

        then:
        snapshot.getErrorCounts().size() == 1
        snapshot.getErrorCounts().keySet().first().length() <= 103  // 100 + "..."
    }

    def "should handle null error messages"() {
        given:
        collector.recordResult(SimpleTaskResult.failure(1L, 1_000_000L, null))

        when:
        MetricsSnapshot snapshot = collector.getSnapshot()

        then:
        snapshot.getFailedTasks() == 1
        snapshot.getErrorCounts().isEmpty()
    }

    def "should handle empty error messages"() {
        given:
        collector.recordResult(SimpleTaskResult.failure(1L, 1_000_000L, ""))

        when:
        MetricsSnapshot snapshot = collector.getSnapshot()

        then:
        snapshot.getFailedTasks() == 1
        snapshot.getErrorCounts().isEmpty()
    }

    def "should enforce max latency history size"() {
        given:
        int maxHistory = 100
        MetricsCollector limitedCollector = new MetricsCollector(maxHistory)
        
        when:
        (1..200).each { i ->
            limitedCollector.recordResult(SimpleTaskResult.success(i, i * 1_000_000L))
        }
        MetricsSnapshot snapshot = limitedCollector.getSnapshot()

        then:
        snapshot.getTotalTasks() == 200
        // Percentiles are calculated from limited history
        snapshot.getPercentiles() != null

        cleanup:
        limitedCollector.close()
    }

    def "should reset all metrics"() {
        given:
        (1..10).each { i ->
            collector.recordResult(SimpleTaskResult.success(i, i * 1_000_000L))
        }
        collector.recordResult(SimpleTaskResult.failure(11L, 1_000_000L, "Error"))

        when:
        collector.reset()
        MetricsSnapshot snapshot = collector.getSnapshot()

        then:
        snapshot.getTotalTasks() == 0
        snapshot.getSuccessfulTasks() == 0
        snapshot.getFailedTasks() == 0
        snapshot.getErrorCounts().isEmpty()
    }

    def "should handle close gracefully"() {
        given:
        (1..10).each { i ->
            collector.recordResult(SimpleTaskResult.success(i, i * 1_000_000L))
        }

        when:
        collector.close()

        then:
        notThrown(Exception)
    }

    @Unroll
    def "should handle edge case latencies: #latencyNanos"() {
        given:
        SimpleTaskResult result = SimpleTaskResult.success(1L, latencyNanos)

        when:
        collector.recordResult(result)
        MetricsSnapshot snapshot = collector.getSnapshot()

        then:
        snapshot.getTotalTasks() == 1
        snapshot.getAvgLatencyMs() >= 0

        where:
        latencyNanos << [0L, 1L, 1_000L, 1_000_000L, 1_000_000_000L]
    }

    def "should provide consistent snapshots"() {
        given:
        (1..50).each { i ->
            collector.recordResult(SimpleTaskResult.success(i, i * 1_000_000L))
        }

        when:
        MetricsSnapshot snapshot1 = collector.getSnapshot()
        MetricsSnapshot snapshot2 = collector.getSnapshot()

        then:
        snapshot1.getTotalTasks() == snapshot2.getTotalTasks()
        snapshot1.getSuccessfulTasks() == snapshot2.getSuccessfulTasks()
        snapshot1.getAvgLatencyMs() == snapshot2.getAvgLatencyMs()
    }

    def "should handle concurrent access"() {
        given:
        int threadCount = 10
        int resultsPerThread = 100
        List<Thread> threads = []

        when:
        threadCount.times { threadIdx ->
            Thread thread = new Thread({
                resultsPerThread.times { i ->
                    long taskId = threadIdx * resultsPerThread + i
                    collector.recordResult(SimpleTaskResult.success(taskId, 1_000_000L))
                }
            })
            threads.add(thread)
            thread.start()
        }
        threads.each { it.join() }
        MetricsSnapshot snapshot = collector.getSnapshot()

        then:
        snapshot.getTotalTasks() == threadCount * resultsPerThread
        snapshot.getSuccessfulTasks() == threadCount * resultsPerThread
    }

    def "should handle zero elapsed time correctly"() {
        given:
        collector.recordResult(SimpleTaskResult.success(1L, 1_000_000L))

        when:
        MetricsSnapshot snapshot = collector.getSnapshot()

        then:
        snapshot.getTps() >= 0  // Should not throw or return negative
    }

    def "should preserve start time"() {
        given:
        def startBefore = java.time.Instant.now()
        Thread.sleep(50)

        when:
        MetricsSnapshot snapshot = collector.getSnapshot()

        then:
        !snapshot.getStartTime().isBefore(startBefore.minusSeconds(1))
        !snapshot.getStartTime().isAfter(java.time.Instant.now())
    }

    def "should track elapsed duration"() {
        given:
        Thread.sleep(100)

        when:
        MetricsSnapshot snapshot = collector.getSnapshot()

        then:
        snapshot.getElapsed().toMillis() >= 100
    }
}
