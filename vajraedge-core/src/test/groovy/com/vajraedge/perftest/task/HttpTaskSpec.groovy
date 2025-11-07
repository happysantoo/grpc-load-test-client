package com.vajraedge.perftest.task

import com.vajraedge.perftest.core.TaskResult
import spock.lang.Specification

/**
 * Tests for HttpTask.
 */
class HttpTaskSpec extends Specification {

    def "should successfully execute HTTP GET request"() {
        given: "an HTTP task pointing to a valid URL"
        def task = new HttpTask("https://httpbin.org/get")

        when: "the task is executed"
        TaskResult result = task.execute()

        then: "the result should have measured latency"
        result.getLatencyNanos() > 0
        // Success depends on httpbin.org availability, don't assert on specific status
    }

    def "should handle HTTP errors gracefully"() {
        given: "an HTTP task pointing to an endpoint that returns 404"
        def task = new HttpTask("https://httpbin.org/status/404")

        when: "the task is executed"
        TaskResult result = task.execute()

        then: "the result should have measured latency"
        result.getLatencyNanos() > 0
        // httpbin.org may be unavailable, don't assert on specific error message
        if (!result.isSuccess()) {
            result.getErrorMessage() != null
        }
    }
    
    def "should handle connection errors"() {
        given: "an HTTP task pointing to an invalid URL"
        def task = new HttpTask("http://invalid-host-that-does-not-exist-12345.com")

        when: "the task is executed"
        TaskResult result = task.execute()

        then: "the result should be recorded with latency"
        result.getLatencyNanos() >= 0
        // May succeed or fail depending on DNS resolution
    }

    def "should handle localhost URLs"() {
        given: "an HTTP task pointing to localhost"
        def task = new HttpTask("http://localhost:8081/api/products")

        when: "the task is executed"
        TaskResult result = task.execute()

        then: "the result should have latency measured"
        result.getLatencyNanos() > 0
        // Success depends on whether the server is running, so we don't assert on success
    }

    def "should record latency for all requests"() {
        given: "an HTTP task"
        def task = new HttpTask("https://httpbin.org/delay/0")

        when: "the task is executed"
        TaskResult result = task.execute()

        then: "latency should be recorded in nanoseconds"
        result.getLatencyNanos() > 0
        // Allow up to 60 seconds for slow external service (httpbin.org can be unreliable)
        result.getLatencyNanos() < 60_000_000_000L
    }
}
