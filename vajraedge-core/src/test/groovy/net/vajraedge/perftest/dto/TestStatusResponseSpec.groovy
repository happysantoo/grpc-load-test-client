package net.vajraedge.perftest.dto

import net.vajraedge.perftest.concurrency.LoadTestMode
import net.vajraedge.perftest.concurrency.RampStrategyType
import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDateTime

/**
 * Tests for TestStatusResponse DTO
 */
class TestStatusResponseSpec extends Specification {

    def "should create test status response with basic info"() {
        given:
        String testId = "test-123"
        String status = "RUNNING"

        when:
        TestStatusResponse response = new TestStatusResponse(testId, status)

        then:
        response.getTestId() == testId
        response.getStatus() == status
    }

    def "should create test status response with no-args constructor"() {
        when:
        TestStatusResponse response = new TestStatusResponse()

        then:
        response != null
        response.getTestId() == null
        response.getStatus() == null
    }

    @Unroll
    def "should handle different status values: #status"() {
        given:
        TestStatusResponse response = new TestStatusResponse()

        when:
        response.setStatus(status)

        then:
        response.getStatus() == status

        where:
        status << ["RUNNING", "COMPLETED", "FAILED", "STOPPED"]
    }

    def "should store start and end times"() {
        given:
        TestStatusResponse response = new TestStatusResponse()
        LocalDateTime startTime = LocalDateTime.now().minusMinutes(5)
        LocalDateTime endTime = LocalDateTime.now()

        when:
        response.setStartTime(startTime)
        response.setEndTime(endTime)

        then:
        response.getStartTime() == startTime
        response.getEndTime() == endTime
    }

    @Unroll
    def "should store elapsed seconds: #elapsedSeconds"() {
        given:
        TestStatusResponse response = new TestStatusResponse()

        when:
        response.setElapsedSeconds(elapsedSeconds)

        then:
        response.getElapsedSeconds() == elapsedSeconds

        where:
        elapsedSeconds << [0L, 30L, 60L, 300L, 3600L]
    }

    def "should store test configuration"() {
        given:
        TestStatusResponse response = new TestStatusResponse()
        TestConfigRequest config = new TestConfigRequest()
        config.setMode(LoadTestMode.CONCURRENCY_BASED)
        config.setStartingConcurrency(10)
        config.setMaxConcurrency(100)
        config.setRampStrategyType(RampStrategyType.STEP)
        config.setRampStep(10)
        config.setRampIntervalSeconds(30L)
        config.setTestDurationSeconds(60)

        when:
        response.setConfiguration(config)

        then:
        response.getConfiguration() == config
        response.getConfiguration().getMaxConcurrency() == 100
    }

    def "should create and store current metrics"() {
        given:
        TestStatusResponse response = new TestStatusResponse()
        TestStatusResponse.CurrentMetrics metrics = new TestStatusResponse.CurrentMetrics()
        metrics.setTotalRequests(1000L)
        metrics.setSuccessfulRequests(950L)
        metrics.setFailedRequests(50L)
        metrics.setActiveTasks(10)
        metrics.setCurrentTps(100.5)
        metrics.setAvgLatencyMs(25.3)

        when:
        response.setCurrentMetrics(metrics)

        then:
        response.getCurrentMetrics() == metrics
        response.getCurrentMetrics().getTotalRequests() == 1000L
        response.getCurrentMetrics().getSuccessfulRequests() == 950L
        response.getCurrentMetrics().getFailedRequests() == 50L
        response.getCurrentMetrics().getActiveTasks() == 10
        response.getCurrentMetrics().getCurrentTps() == 100.5
        response.getCurrentMetrics().getAvgLatencyMs() == 25.3
    }

    def "should create complete test status response"() {
        given:
        TestStatusResponse response = new TestStatusResponse("test-456", "COMPLETED")
        response.setStartTime(LocalDateTime.now().minusMinutes(5))
        response.setEndTime(LocalDateTime.now())
        response.setElapsedSeconds(300L)

        TestConfigRequest config = new TestConfigRequest()
        config.setMode(LoadTestMode.CONCURRENCY_BASED)
        config.setStartingConcurrency(10)
        config.setMaxConcurrency(1000)
        config.setRampStrategyType(RampStrategyType.LINEAR)
        config.setRampDurationSeconds(30)
        config.setTestDurationSeconds(300)
        response.setConfiguration(config)

        TestStatusResponse.CurrentMetrics metrics = new TestStatusResponse.CurrentMetrics()
        metrics.setTotalRequests(15000L)
        metrics.setSuccessfulRequests(14500L)
        metrics.setFailedRequests(500L)
        metrics.setActiveTasks(0)
        metrics.setCurrentTps(0.0)
        metrics.setAvgLatencyMs(15.7)
        response.setCurrentMetrics(metrics)

        expect:
        response.getTestId() == "test-456"
        response.getStatus() == "COMPLETED"
        response.getElapsedSeconds() == 300L
        response.getConfiguration().getMaxConcurrency() == 1000
        response.getCurrentMetrics().getTotalRequests() == 15000L
    }

    def "should handle null values in CurrentMetrics"() {
        when:
        TestStatusResponse.CurrentMetrics metrics = new TestStatusResponse.CurrentMetrics()

        then:
        metrics.getTotalRequests() == null
        metrics.getSuccessfulRequests() == null
        metrics.getFailedRequests() == null
        metrics.getActiveTasks() == null
        metrics.getCurrentTps() == null
        metrics.getAvgLatencyMs() == null
    }

    def "should allow null configuration and metrics"() {
        given:
        TestStatusResponse response = new TestStatusResponse()

        when:
        response.setConfiguration(null)
        response.setCurrentMetrics(null)

        then:
        response.getConfiguration() == null
        response.getCurrentMetrics() == null
    }

    @Unroll
    def "should handle edge case values in CurrentMetrics: total=#total, success=#success, failed=#failed"() {
        given:
        TestStatusResponse.CurrentMetrics metrics = new TestStatusResponse.CurrentMetrics()

        when:
        metrics.setTotalRequests(total)
        metrics.setSuccessfulRequests(success)
        metrics.setFailedRequests(failed)

        then:
        metrics.getTotalRequests() == total
        metrics.getSuccessfulRequests() == success
        metrics.getFailedRequests() == failed

        where:
        total | success | failed
        0L    | 0L      | 0L
        1L    | 1L      | 0L
        1000L | 500L    | 500L
        1000L | 1000L   | 0L
    }
}
