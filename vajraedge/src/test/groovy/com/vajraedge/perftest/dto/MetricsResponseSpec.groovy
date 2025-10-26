package com.vajraedge.perftest.dto

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Tests for MetricsResponse DTO
 */
class MetricsResponseSpec extends Specification {

    def "should create metrics response with no-args constructor"() {
        when:
        MetricsResponse response = new MetricsResponse()

        then:
        response != null
        response.getTestId() == null
        response.getTimestamp() == null
    }

    def "should set and get all basic fields"() {
        given:
        MetricsResponse response = new MetricsResponse()

        when:
        response.setTestId("test-789")
        response.setTimestamp(System.currentTimeMillis())
        response.setTotalRequests(1000L)
        response.setSuccessfulRequests(950L)
        response.setFailedRequests(50L)
        response.setSuccessRate(95.0)
        response.setActiveTasks(10)
        response.setCurrentTps(100.5)

        then:
        response.getTestId() == "test-789"
        response.getTimestamp() != null
        response.getTotalRequests() == 1000L
        response.getSuccessfulRequests() == 950L
        response.getFailedRequests() == 50L
        response.getSuccessRate() == 95.0
        response.getActiveTasks() == 10
        response.getCurrentTps() == 100.5
    }

    @Unroll
    def "should store latency metrics: avg=#avg, min=#min, max=#max"() {
        given:
        MetricsResponse response = new MetricsResponse()

        when:
        response.setAvgLatencyMs(avg)
        response.setMinLatencyMs(min)
        response.setMaxLatencyMs(max)

        then:
        response.getAvgLatencyMs() == avg
        response.getMinLatencyMs() == min
        response.getMaxLatencyMs() == max

        where:
        avg   | min  | max
        10.0  | 1.0  | 50.0
        25.5  | 5.0  | 100.0
        100.0 | 10.0 | 500.0
    }

    def "should store latency percentiles map"() {
        given:
        MetricsResponse response = new MetricsResponse()
        Map<String, Double> percentiles = [
            "p50": 10.0,
            "p75": 15.0,
            "p90": 25.0,
            "p95": 35.0,
            "p99": 50.0,
            "p99.9": 100.0
        ]

        when:
        response.setLatencyPercentiles(percentiles)

        then:
        response.getLatencyPercentiles() == percentiles
        response.getLatencyPercentiles().get("p50") == 10.0
        response.getLatencyPercentiles().get("p95") == 35.0
        response.getLatencyPercentiles().get("p99.9") == 100.0
    }

    def "should handle empty latency percentiles map"() {
        given:
        MetricsResponse response = new MetricsResponse()
        Map<String, Double> emptyMap = [:]

        when:
        response.setLatencyPercentiles(emptyMap)

        then:
        response.getLatencyPercentiles().isEmpty()
    }

    def "should create complete metrics response"() {
        given:
        MetricsResponse response = new MetricsResponse()
        response.setTestId("test-complete")
        response.setTimestamp(System.currentTimeMillis())
        response.setTotalRequests(10000L)
        response.setSuccessfulRequests(9800L)
        response.setFailedRequests(200L)
        response.setSuccessRate(98.0)
        response.setActiveTasks(50)
        response.setCurrentTps(500.0)
        
        Map<String, Double> percentiles = [
            "p50": 12.5,
            "p75": 18.0,
            "p90": 30.0,
            "p95": 45.0,
            "p99": 75.0,
            "p99.9": 150.0
        ]
        response.setLatencyPercentiles(percentiles)
        
        response.setAvgLatencyMs(15.3)
        response.setMinLatencyMs(2.1)
        response.setMaxLatencyMs(200.5)

        expect:
        response.getTestId() == "test-complete"
        response.getTotalRequests() == 10000L
        response.getSuccessfulRequests() == 9800L
        response.getFailedRequests() == 200L
        response.getSuccessRate() == 98.0
        response.getActiveTasks() == 50
        response.getCurrentTps() == 500.0
        response.getLatencyPercentiles().size() == 6
        response.getAvgLatencyMs() == 15.3
        response.getMinLatencyMs() == 2.1
        response.getMaxLatencyMs() == 200.5
    }

    @Unroll
    def "should calculate success rate correctly: total=#total, success=#success, expected=#expectedRate"() {
        given:
        MetricsResponse response = new MetricsResponse()

        when:
        response.setTotalRequests(total)
        response.setSuccessfulRequests(success)
        response.setSuccessRate(expectedRate)

        then:
        response.getSuccessRate() == expectedRate

        where:
        total | success | expectedRate
        100   | 100     | 100.0
        100   | 95      | 95.0
        1000  | 950     | 95.0
        1000  | 0       | 0.0
    }

    @Unroll
    def "should handle zero and null values: total=#total, success=#success, failed=#failed"() {
        given:
        MetricsResponse response = new MetricsResponse()

        when:
        response.setTotalRequests(total)
        response.setSuccessfulRequests(success)
        response.setFailedRequests(failed)

        then:
        response.getTotalRequests() == total
        response.getSuccessfulRequests() == success
        response.getFailedRequests() == failed

        where:
        total | success | failed
        0L    | 0L      | 0L
        null  | null    | null
        100L  | null    | null
    }

    @Unroll
    def "should handle different TPS values: #tps"() {
        given:
        MetricsResponse response = new MetricsResponse()

        when:
        response.setCurrentTps(tps)

        then:
        response.getCurrentTps() == tps

        where:
        tps << [0.0, 1.0, 10.5, 100.0, 1000.0, 10000.0]
    }

    def "should handle null latency percentiles"() {
        given:
        MetricsResponse response = new MetricsResponse()

        when:
        response.setLatencyPercentiles(null)

        then:
        response.getLatencyPercentiles() == null
    }

    @Unroll
    def "should store different active task counts: #activeTasks"() {
        given:
        MetricsResponse response = new MetricsResponse()

        when:
        response.setActiveTasks(activeTasks)

        then:
        response.getActiveTasks() == activeTasks

        where:
        activeTasks << [0, 1, 10, 100, 1000, 10000]
    }

    def "should handle partial latency percentiles"() {
        given:
        MetricsResponse response = new MetricsResponse()
        Map<String, Double> partialPercentiles = [
            "p50": 10.0,
            "p95": 50.0
        ]

        when:
        response.setLatencyPercentiles(partialPercentiles)

        then:
        response.getLatencyPercentiles().size() == 2
        response.getLatencyPercentiles().get("p50") == 10.0
        response.getLatencyPercentiles().get("p95") == 50.0
        response.getLatencyPercentiles().get("p99") == null
    }
}
