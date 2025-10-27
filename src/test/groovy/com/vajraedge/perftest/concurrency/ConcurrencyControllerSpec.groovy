package com.vajraedge.perftest.concurrency

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Tests for ConcurrencyController - manages virtual user concurrency and optional throttling
 */
class ConcurrencyControllerSpec extends Specification {

    def "should create controller with concurrency-based mode"() {
        given:
        def strategy = new LinearRampStrategy(10, 100, 60L)

        when:
        def controller = new ConcurrencyController(strategy, LoadTestMode.CONCURRENCY_BASED, null)

        then:
        controller.getMode() == LoadTestMode.CONCURRENCY_BASED
        controller.getRampStrategy() == strategy
        controller.getMaxTpsLimit() == Optional.empty()
        controller.getMaxConcurrency() == 100
    }

    def "should create controller with rate-limited mode and TPS limit"() {
        given:
        def strategy = new StepRampStrategy(10, 10, 30L, 100)

        when:
        def controller = new ConcurrencyController(strategy, LoadTestMode.RATE_LIMITED, 500)

        then:
        controller.getMode() == LoadTestMode.RATE_LIMITED
        controller.getMaxTpsLimit() == Optional.of(500)
        controller.getMaxConcurrency() == 100
    }

    def "should delegate target concurrency to ramp strategy"() {
        given:
        def strategy = new LinearRampStrategy(10, 100, 60L)
        def controller = new ConcurrencyController(strategy, LoadTestMode.CONCURRENCY_BASED, null)

        when:
        def concurrency0 = controller.getTargetConcurrency(0L)
        def concurrency30 = controller.getTargetConcurrency(30L)
        def concurrency60 = controller.getTargetConcurrency(60L)

        then:
        concurrency0 == 10
        concurrency30 == 55
        concurrency60 == 100
    }

    def "should not throttle in concurrency-based mode regardless of TPS"() {
        given:
        def strategy = new LinearRampStrategy(10, 100, 60L)
        def controller = new ConcurrencyController(strategy, LoadTestMode.CONCURRENCY_BASED, null)

        expect:
        !controller.shouldThrottle(0.0)
        !controller.shouldThrottle(100.0)
        !controller.shouldThrottle(1000.0)
        !controller.shouldThrottle(10000.0)
    }

    def "should throttle in rate-limited mode when TPS exceeds limit"() {
        given:
        def strategy = new LinearRampStrategy(10, 100, 60L)
        def controller = new ConcurrencyController(strategy, LoadTestMode.RATE_LIMITED, 500)

        expect:
        !controller.shouldThrottle(0.0)    // Below limit
        !controller.shouldThrottle(250.0)  // Below limit
        !controller.shouldThrottle(499.9)  // Just below limit
        controller.shouldThrottle(500.0)   // At limit (throttle at >=)
        controller.shouldThrottle(501.0)   // Exceeds limit
        controller.shouldThrottle(1000.0)  // Well above limit
    }

    def "should not throttle in rate-limited mode when no limit specified"() {
        given:
        def strategy = new LinearRampStrategy(10, 100, 60L)
        def controller = new ConcurrencyController(strategy, LoadTestMode.RATE_LIMITED, null)

        expect:
        !controller.shouldThrottle(0.0)
        !controller.shouldThrottle(1000.0)
        !controller.shouldThrottle(10000.0)
    }

    @Unroll
    def "should handle edge case: currentTps=#currentTps, limit=#limit in rate-limited mode"() {
        given:
        def strategy = new LinearRampStrategy(10, 100, 60L)
        def controller = new ConcurrencyController(strategy, LoadTestMode.RATE_LIMITED, limit)

        expect:
        controller.shouldThrottle(currentTps) == expectedThrottle

        where:
        currentTps | limit | expectedThrottle
        0.0        | 100   | false
        99.9       | 100   | false
        100.0      | 100   | true   // At limit triggers throttle (>=)
        100.1      | 100   | true
        200.0      | 100   | true
        50.0       | null  | false  // No limit
        1000.0     | null  | false  // No limit
    }

    def "should reject null ramp strategy"() {
        when:
        new ConcurrencyController(null, LoadTestMode.CONCURRENCY_BASED, null)

        then:
        thrown(IllegalArgumentException)
    }

    def "should reject null mode"() {
        when:
        def strategy = new LinearRampStrategy(10, 100, 60L)
        new ConcurrencyController(strategy, null, null)

        then:
        thrown(IllegalArgumentException)
    }

    def "should accept negative TPS for throttling check"() {
        given:
        def strategy = new LinearRampStrategy(10, 100, 60L)
        def controller = new ConcurrencyController(strategy, LoadTestMode.RATE_LIMITED, 100)

        expect:
        // Negative TPS doesn't make sense but shouldn't crash
        !controller.shouldThrottle(-10.0)
        !controller.shouldThrottle(-100.0)
    }

    def "should work with step ramp strategy"() {
        given:
        def strategy = new StepRampStrategy(10, 10, 30L, 100)
        def controller = new ConcurrencyController(strategy, LoadTestMode.CONCURRENCY_BASED, null)

        when:
        def concurrency0 = controller.getTargetConcurrency(0L)
        def concurrency30 = controller.getTargetConcurrency(30L)
        def concurrency60 = controller.getTargetConcurrency(60L)

        then:
        concurrency0 == 10
        concurrency30 == 20
        concurrency60 == 30
    }

    def "should expose max concurrency from strategy"() {
        given:
        def strategy = new LinearRampStrategy(5, 250, 120L)
        def controller = new ConcurrencyController(strategy, LoadTestMode.CONCURRENCY_BASED, null)

        expect:
        controller.getMaxConcurrency() == 250
    }

    def "should correctly identify mode"() {
        when:
        def strategy = new LinearRampStrategy(10, 100, 60L)
        def concurrencyController = new ConcurrencyController(strategy, LoadTestMode.CONCURRENCY_BASED, null)
        def rateLimitedController = new ConcurrencyController(strategy, LoadTestMode.RATE_LIMITED, 500)

        then:
        concurrencyController.getMode() == LoadTestMode.CONCURRENCY_BASED
        rateLimitedController.getMode() == LoadTestMode.RATE_LIMITED
    }

    def "should handle very high TPS values in throttling"() {
        given:
        def strategy = new LinearRampStrategy(10, 100, 60L)
        def controller = new ConcurrencyController(strategy, LoadTestMode.RATE_LIMITED, 1000)

        expect:
        controller.shouldThrottle(999.99) == false
        controller.shouldThrottle(1000.0) == true  // At limit triggers throttle
        controller.shouldThrottle(1000.01) == true
        controller.shouldThrottle(1000000.0) == true
        controller.shouldThrottle(Double.MAX_VALUE) == true
    }
}
