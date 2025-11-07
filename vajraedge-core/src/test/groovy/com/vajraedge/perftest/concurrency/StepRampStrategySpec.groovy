package com.vajraedge.perftest.concurrency

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Tests for StepRampStrategy - step-based concurrency ramp-up strategy
 */
class StepRampStrategySpec extends Specification {

    def "should create strategy with valid parameters"() {
        when:
        def strategy = new StepRampStrategy(10, 10, 30L, 100)

        then:
        strategy.getStartingConcurrency() == 10
        strategy.getMaxConcurrency() == 100
        strategy.getDescription() contains "Step ramp"
        strategy.getDescription() contains "10"
        strategy.getDescription() contains "30"
    }

    def "should reject invalid parameters"() {
        when:
        new StepRampStrategy(startConcurrency, rampStep, rampInterval, maxConcurrency)

        then:
        thrown(IllegalArgumentException)

        where:
        startConcurrency | rampStep | rampInterval | maxConcurrency
        0                | 10       | 30L          | 100            // startConcurrency <= 0
        10               | 0        | 30L          | 100            // rampStep <= 0
        10               | 10       | 0L           | 100            // rampInterval <= 0
        10               | 10       | 30L          | 5              // maxConcurrency < startConcurrency
        -5               | 10       | 30L          | 100            // negative startConcurrency
        10               | -5       | 30L          | 100            // negative rampStep
    }

    @Unroll
    def "should calculate correct concurrency for start=10, step=10, interval=30s at elapsed=#elapsed"() {
        given:
        def strategy = new StepRampStrategy(10, 10, 30L, 100)

        when:
        def concurrency = strategy.getTargetConcurrency(elapsed)

        then:
        concurrency == expected

        where:
        elapsed | expected
        0L      | 10       // No intervals completed
        15L     | 10       // First interval not complete
        30L     | 20       // First interval complete: 10 + (1 * 10)
        45L     | 20       // Second interval not complete
        60L     | 30       // Second interval complete: 10 + (2 * 10)
        90L     | 40       // Third interval complete
        120L    | 50       // Fourth interval complete
        270L    | 100      // Ninth interval complete (capped at max)
        300L    | 100      // Beyond max (still capped)
    }

    def "should handle large ramp step"() {
        given:
        def strategy = new StepRampStrategy(10, 50, 30L, 200)

        expect:
        strategy.getTargetConcurrency(0L) == 10
        strategy.getTargetConcurrency(30L) == 60   // 10 + 50
        strategy.getTargetConcurrency(60L) == 110  // 10 + 100
        strategy.getTargetConcurrency(90L) == 160  // 10 + 150
        strategy.getTargetConcurrency(120L) == 200 // Capped at max
    }

    def "should handle small ramp step"() {
        given:
        def strategy = new StepRampStrategy(10, 1, 10L, 20)

        expect:
        strategy.getTargetConcurrency(0L) == 10
        strategy.getTargetConcurrency(10L) == 11
        strategy.getTargetConcurrency(20L) == 12
        strategy.getTargetConcurrency(50L) == 15
        strategy.getTargetConcurrency(100L) == 20  // Capped
    }

    def "should handle single interval (very long)"() {
        given:
        def strategy = new StepRampStrategy(10, 90, 3600L, 100)

        expect:
        strategy.getTargetConcurrency(0L) == 10
        strategy.getTargetConcurrency(1800L) == 10    // Not yet complete
        strategy.getTargetConcurrency(3600L) == 100   // First interval complete
        strategy.getTargetConcurrency(7200L) == 100   // Still at max
    }

    def "should calculate estimated ramp duration correctly"() {
        when:
        def strategy = new StepRampStrategy(10, 10, 30L, 100)

        then:
        // To go from 10 to 100 requires 9 steps of 10
        // 9 steps * 30 seconds = 270 seconds
        strategy.getEstimatedRampDuration() == 270L
    }

    def "should calculate ramp duration for exact steps"() {
        when:
        def strategy = new StepRampStrategy(20, 20, 10L, 100)

        then:
        // To go from 20 to 100 requires 4 steps of 20
        // 4 steps * 10 seconds = 40 seconds
        strategy.getEstimatedRampDuration() == 40L
    }

    def "should calculate ramp duration with partial final step"() {
        when:
        def strategy = new StepRampStrategy(10, 15, 20L, 100)

        then:
        // To go from 10 to 100 is 90 increase
        // 90 / 15 = 6 steps
        // 6 steps * 20 seconds = 120 seconds
        strategy.getEstimatedRampDuration() == 120L
    }

    def "should return zero ramp duration when already at max"() {
        when:
        def strategy = new StepRampStrategy(100, 10, 30L, 100)

        then:
        strategy.getEstimatedRampDuration() == 0L
    }

    def "should never return concurrency below starting value"() {
        given:
        def strategy = new StepRampStrategy(20, 10, 30L, 200)

        when:
        def concurrency = strategy.getTargetConcurrency(elapsed)

        then:
        concurrency >= 20

        where:
        elapsed << [0L, 15L, 30L, 60L, 120L, 300L]
    }

    def "should never return concurrency above max value"() {
        given:
        def strategy = new StepRampStrategy(10, 10, 30L, 100)

        when:
        def concurrency = strategy.getTargetConcurrency(elapsed)

        then:
        concurrency <= 100

        where:
        elapsed << [0L, 30L, 90L, 270L, 300L, 1000L]
    }

    def "should be monotonically non-decreasing"() {
        given:
        def strategy = new StepRampStrategy(10, 10, 30L, 100)

        when:
        def values = (0L..300L).collect { strategy.getTargetConcurrency(it) }

        then:
        // Check that each value is >= previous value
        for (int i = 1; i < values.size(); i++) {
            assert values[i] >= values[i - 1],
                "Non-monotonic at index $i: ${values[i - 1]} -> ${values[i]}"
        }
    }

    def "should remain constant between intervals"() {
        given:
        def strategy = new StepRampStrategy(10, 10, 30L, 100)

        expect:
        // Within first interval (0-30s), should remain at 10
        strategy.getTargetConcurrency(0L) == 10
        strategy.getTargetConcurrency(10L) == 10
        strategy.getTargetConcurrency(20L) == 10
        strategy.getTargetConcurrency(29L) == 10
        
        // After first interval, should be at 20
        strategy.getTargetConcurrency(30L) == 20
        strategy.getTargetConcurrency(40L) == 20
        strategy.getTargetConcurrency(50L) == 20
        strategy.getTargetConcurrency(59L) == 20
    }
}
