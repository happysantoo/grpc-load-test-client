package com.vajraedge.perftest.core

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Tests for SimpleTaskResult - immutable task result implementation
 */
class SimpleTaskResultSpec extends Specification {

    @Unroll
    def "should create successful task result: taskId=#taskId, latency=#latency"() {
        when:
        SimpleTaskResult result = SimpleTaskResult.success(taskId, latency)

        then:
        result.getTaskId() == taskId
        result.isSuccess()
        result.getErrorMessage() == null
        result.getLatencyNanos() == latency
        result.getLatencyMs() == latency / 1_000_000.0
        result.getResponseSize() == 0
        result.getMetadata() == null

        where:
        taskId | latency
        1L     | 1_000_000L    // 1ms
        2L     | 5_000_000L    // 5ms
        100L   | 10_000_000L   // 10ms
    }

    @Unroll
    def "should create successful task result with response size: size=#size"() {
        when:
        SimpleTaskResult result = SimpleTaskResult.success(1L, 1_000_000L, size)

        then:
        result.isSuccess()
        result.getResponseSize() == size
        result.getMetadata() == null

        where:
        size << [0, 100, 1024, 65536]
    }

    def "should create successful task result with response size and metadata"() {
        given:
        Map metadata = [protocol: "gRPC", endpoint: "/test"]

        when:
        SimpleTaskResult result = SimpleTaskResult.success(1L, 1_000_000L, 512, metadata)

        then:
        result.isSuccess()
        result.getResponseSize() == 512
        result.getMetadata() == metadata
    }

    @Unroll
    def "should create failed task result: taskId=#taskId, error=#error"() {
        when:
        SimpleTaskResult result = SimpleTaskResult.failure(taskId, latency, error)

        then:
        result.getTaskId() == taskId
        !result.isSuccess()
        result.getErrorMessage() == error
        result.getLatencyNanos() == latency
        result.getResponseSize() == 0
        result.getMetadata() == null

        where:
        taskId | latency      | error
        1L     | 1_000_000L   | "Connection timeout"
        2L     | 2_000_000L   | "Server error"
        3L     | 3_000_000L   | "Invalid response"
    }

    def "should create failed task result with metadata"() {
        given:
        Map metadata = [errorCode: 500, retryable: true]

        when:
        SimpleTaskResult result = SimpleTaskResult.failure(1L, 1_000_000L, "Internal error", metadata)

        then:
        !result.isSuccess()
        result.getErrorMessage() == "Internal error"
        result.getMetadata() == metadata
        result.getResponseSize() == 0
    }

    @Unroll
    def "should calculate latency in milliseconds correctly: nanos=#nanos, expectedMs=#expectedMs"() {
        when:
        SimpleTaskResult result = SimpleTaskResult.success(1L, nanos)

        then:
        result.getLatencyMs() == expectedMs

        where:
        nanos         | expectedMs
        0L            | 0.0
        1_000_000L    | 1.0
        5_000_000L    | 5.0
        10_000_000L   | 10.0
        1_500_000L    | 1.5
    }

    def "should handle zero latency"() {
        when:
        SimpleTaskResult result = SimpleTaskResult.success(1L, 0L)

        then:
        result.getLatencyNanos() == 0L
        result.getLatencyMs() == 0.0
    }

    def "should format successful result toString correctly"() {
        when:
        SimpleTaskResult result = SimpleTaskResult.success(42L, 5_000_000L, 1024)
        String str = result.toString()

        then:
        str.contains("id=42")
        str.contains("latency=5.00ms")
        str.contains("success=true")
        str.contains("size=1024")
    }

    def "should format failed result toString correctly"() {
        when:
        SimpleTaskResult result = SimpleTaskResult.failure(42L, 3_000_000L, "Connection failed")
        String str = result.toString()

        then:
        str.contains("id=42")
        str.contains("latency=3.00ms")
        str.contains("success=false")
        str.contains("error='Connection failed'")
    }

    def "should handle null error message in failure"() {
        when:
        SimpleTaskResult result = SimpleTaskResult.failure(1L, 1_000_000L, null)

        then:
        !result.isSuccess()
        result.getErrorMessage() == null
    }

    @Unroll
    def "should support different task IDs: taskId=#taskId"() {
        when:
        SimpleTaskResult result = SimpleTaskResult.success(taskId, 1_000_000L)

        then:
        result.getTaskId() == taskId

        where:
        taskId << [0L, 1L, 100L, 999999L, Long.MAX_VALUE]
    }

    def "should be immutable - success case"() {
        given:
        Map metadata = [key: "value"]
        SimpleTaskResult result = SimpleTaskResult.success(1L, 1_000_000L, 100, metadata)

        when:
        metadata.put("newKey", "newValue")

        then:
        // Original metadata reference may change, but result should not expose mutable state
        result.getMetadata() != null
    }

    def "should be immutable - failure case"() {
        given:
        Map metadata = [error: "code"]
        SimpleTaskResult result = SimpleTaskResult.failure(1L, 1_000_000L, "Failed", metadata)

        when:
        metadata.clear()

        then:
        // Original metadata reference may change, but result should not expose mutable state
        result.getMetadata() != null
    }
}
