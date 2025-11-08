package net.vajraedge.perftest.concurrency

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Tests for LinearRampStrategy - linear concurrency ramp-up strategy
 */
class LinearRampStrategySpec extends Specification {

    def "should create strategy with valid parameters"() {
        when:
        def strategy = new LinearRampStrategy(10, 100, 60L)

        then:
        strategy.getStartingConcurrency() == 10
        strategy.getMaxConcurrency() == 100
        strategy.getDescription() contains "Linear ramp"
        strategy.getDescription() contains "10 to 100"
        strategy.getDescription() contains "60"
    }

    def "should reject invalid parameters"() {
        when:
        new LinearRampStrategy(startConcurrency, maxConcurrency, rampDuration)

        then:
        thrown(IllegalArgumentException)

        where:
        startConcurrency | maxConcurrency | rampDuration
        0                | 100            | 60L          // startConcurrency <= 0
        10               | 5              | 60L          // maxConcurrency < startConcurrency
        10               | 100            | 0L           // rampDuration <= 0
        10               | 100            | -10L         // rampDuration negative
        -5               | 100            | 60L          // negative startConcurrency
    }

    @Unroll
    def "should calculate correct concurrency at elapsed=#elapsed for 10->100 over 60s"() {
        given:
        def strategy = new LinearRampStrategy(10, 100, 60L)

        when:
        def concurrency = strategy.getTargetConcurrency(elapsed)

        then:
        concurrency == expected

        where:
        elapsed | expected
        0L      | 10       // At start
        30L     | 55       // Halfway
        60L     | 100      // At end
        90L     | 100      // After end (capped)
        120L    | 100      // Well past end
    }

    def "should calculate correct concurrency at various points for 5->50 over 100s"() {
        given:
        def strategy = new LinearRampStrategy(5, 50, 100L)

        expect:
        strategy.getTargetConcurrency(0L) == 5
        strategy.getTargetConcurrency(25L) == 16      // 5 + (45 * 0.25) = 16.25 -> 16
        strategy.getTargetConcurrency(50L) == 28      // 5 + (45 * 0.50) = 27.5 -> 28 (rounding)
        strategy.getTargetConcurrency(75L) == 39      // 5 + (45 * 0.75) = 38.75 -> 39 (rounding)
        strategy.getTargetConcurrency(100L) == 50
        strategy.getTargetConcurrency(150L) == 50     // Capped at max
    }

    def "should handle single-user ramp (no actual ramping)"() {
        given:
        def strategy = new LinearRampStrategy(1, 1, 60L)

        expect:
        strategy.getTargetConcurrency(0L) == 1
        strategy.getTargetConcurrency(30L) == 1
        strategy.getTargetConcurrency(60L) == 1
        strategy.getTargetConcurrency(120L) == 1
    }

    def "should handle instant ramp (duration=1)"() {
        given:
        def strategy = new LinearRampStrategy(10, 100, 1L)

        expect:
        strategy.getTargetConcurrency(0L) == 10
        strategy.getTargetConcurrency(1L) == 100
        strategy.getTargetConcurrency(2L) == 100
    }

    def "should never return concurrency below starting value"() {
        given:
        def strategy = new LinearRampStrategy(20, 200, 60L)

        when:
        def concurrency = strategy.getTargetConcurrency(elapsed)

        then:
        concurrency >= 20

        where:
        elapsed << [0L, 1L, 10L, 30L, 60L, 120L]
    }

    def "should never return concurrency above max value"() {
        given:
        def strategy = new LinearRampStrategy(10, 100, 60L)

        when:
        def concurrency = strategy.getTargetConcurrency(elapsed)

        then:
        concurrency <= 100

        where:
        elapsed << [0L, 30L, 60L, 90L, 120L, 1000L]
    }

    def "should handle very long ramp duration"() {
        given:
        def strategy = new LinearRampStrategy(1, 1000, 3600L) // 1 hour ramp

        expect:
        strategy.getTargetConcurrency(0L) == 1
        strategy.getTargetConcurrency(1800L) == 501  // Halfway: 1 + (999 * 0.5) = 500.5 -> 501
        strategy.getTargetConcurrency(3600L) == 1000
    }

    def "should be monotonically increasing"() {
        given:
        def strategy = new LinearRampStrategy(10, 100, 90L)

        when:
        def values = (0L..90L).collect { strategy.getTargetConcurrency(it) }

        then:
        // Check that each value is >= previous value
        for (int i = 1; i < values.size(); i++) {
            assert values[i] >= values[i - 1], 
                "Non-monotonic at index $i: ${values[i - 1]} -> ${values[i]}"
        }
    }
}
