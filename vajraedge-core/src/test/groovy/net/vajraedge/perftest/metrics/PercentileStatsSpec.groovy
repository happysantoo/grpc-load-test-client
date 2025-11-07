package net.vajraedge.perftest.metrics

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Comprehensive tests for PercentileStats
 */
class PercentileStatsSpec extends Specification {

    def "should create percentile stats with arrays"() {
        given:
        double[] percentiles = [0.50, 0.95, 0.99] as double[]
        double[] values = [10.0, 50.0, 100.0] as double[]

        when:
        PercentileStats stats = new PercentileStats(percentiles, values)

        then:
        stats.getPercentile(0.50) == 10.0
        stats.getPercentile(0.95) == 50.0
        stats.getPercentile(0.99) == 100.0
    }

    @Unroll
    def "should return correct percentile value: p#percentile = #expectedValue"() {
        given:
        double[] percentiles = [0.50, 0.75, 0.90, 0.95, 0.99] as double[]
        double[] values = [10.0, 20.0, 40.0, 60.0, 100.0] as double[]
        PercentileStats stats = new PercentileStats(percentiles, values)

        expect:
        stats.getPercentile(percentile) == expectedValue

        where:
        percentile | expectedValue
        0.50       | 10.0
        0.75       | 20.0
        0.90       | 40.0
        0.95       | 60.0
        0.99       | 100.0
    }

    def "should return 0.0 for non-existent percentile"() {
        given:
        double[] percentiles = [0.50, 0.95, 0.99] as double[]
        double[] values = [10.0, 50.0, 100.0] as double[]
        PercentileStats stats = new PercentileStats(percentiles, values)

        expect:
        stats.getPercentile(0.75) == 0.0
        stats.getPercentile(0.999) == 0.0
    }

    def "should return cloned arrays from getters"() {
        given:
        double[] percentiles = [0.50, 0.95, 0.99] as double[]
        double[] values = [10.0, 50.0, 100.0] as double[]
        PercentileStats stats = new PercentileStats(percentiles, values)

        when:
        double[] returnedPercentiles = stats.getPercentiles()
        double[] returnedValues = stats.getValues()
        returnedPercentiles[0] = 999.0
        returnedValues[0] = 999.0

        then:
        stats.getPercentile(0.50) == 10.0  // Original value unchanged
        stats.getPercentiles()[0] == 0.50  // Original percentile unchanged
    }

    def "should generate meaningful toString"() {
        given:
        double[] percentiles = [0.50, 0.95, 0.99] as double[]
        double[] values = [10.0, 50.0, 100.0] as double[]
        PercentileStats stats = new PercentileStats(percentiles, values)

        when:
        String str = stats.toString()

        then:
        str.contains("P50=10.00ms")
        str.contains("P95=50.00ms")
        str.contains("P99=100.00ms")
    }

    def "should handle empty arrays"() {
        given:
        double[] percentiles = [] as double[]
        double[] values = [] as double[]

        when:
        PercentileStats stats = new PercentileStats(percentiles, values)
        String str = stats.toString()

        then:
        stats.getPercentiles().length == 0
        stats.getValues().length == 0
        str.contains("Percentiles{")
    }

    def "should handle single percentile"() {
        given:
        double[] percentiles = [0.50] as double[]
        double[] values = [25.5] as double[]

        when:
        PercentileStats stats = new PercentileStats(percentiles, values)

        then:
        stats.getPercentile(0.50) == 25.5
        stats.getPercentiles().length == 1
        stats.getValues().length == 1
    }

    @Unroll
    def "should handle edge case values: #description"() {
        given:
        double[] percentiles = [0.50, 0.95, 0.99] as double[]
        double[] values = [p50, p95, p99] as double[]

        when:
        PercentileStats stats = new PercentileStats(percentiles, values)

        then:
        stats.getPercentile(0.50) == p50
        stats.getPercentile(0.95) == p95
        stats.getPercentile(0.99) == p99

        where:
        description       | p50     | p95      | p99
        "zero values"     | 0.0     | 0.0      | 0.0
        "very small"      | 0.001   | 0.002    | 0.003
        "very large"      | 1000.0  | 5000.0   | 10000.0
        "decimals"        | 12.345  | 67.890   | 123.456
        "negative"        | -1.0    | -0.5     | -0.1
    }

    def "should handle many percentiles"() {
        given:
        double[] percentiles = [0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 0.95, 0.99, 0.999] as double[]
        double[] values = [1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 20.0, 50.0] as double[]

        when:
        PercentileStats stats = new PercentileStats(percentiles, values)

        then:
        stats.getPercentiles().length == 12
        stats.getValues().length == 12
        stats.getPercentile(0.1) == 1.0
        stats.getPercentile(0.999) == 50.0
    }

    def "should maintain precision for fractional percentiles"() {
        given:
        double[] percentiles = [0.501, 0.951, 0.991] as double[]
        double[] values = [10.123, 50.456, 100.789] as double[]

        when:
        PercentileStats stats = new PercentileStats(percentiles, values)

        then:
        stats.getPercentile(0.501) == 10.123
        stats.getPercentile(0.951) == 50.456
        stats.getPercentile(0.991) == 100.789
    }

    def "should handle toString with single value"() {
        given:
        double[] percentiles = [0.99] as double[]
        double[] values = [123.45] as double[]
        PercentileStats stats = new PercentileStats(percentiles, values)

        when:
        String str = stats.toString()

        then:
        str.contains("P99=123.45ms")
        !str.contains(", ")  // No comma since only one value
    }

    def "should format toString with multiple values correctly"() {
        given:
        double[] percentiles = [0.50, 0.95] as double[]
        double[] values = [10.0, 50.0] as double[]
        PercentileStats stats = new PercentileStats(percentiles, values)

        when:
        String str = stats.toString()

        then:
        str.contains("P50=10.00ms, P95=50.00ms")
    }

    def "should be immutable after construction"() {
        given:
        double[] percentiles = [0.50, 0.95, 0.99] as double[]
        double[] values = [10.0, 50.0, 100.0] as double[]
        PercentileStats stats = new PercentileStats(percentiles, values)

        when:
        percentiles[0] = 999.0
        values[0] = 999.0

        then:
        stats.getPercentile(0.50) == 10.0  // Original value unchanged
        stats.getPercentiles()[0] == 0.50  // Original percentile unchanged
    }

    def "should handle decimal formatting in toString"() {
        given:
        double[] percentiles = [0.50, 0.95, 0.99] as double[]
        double[] values = [1.2345, 10.6789, 99.9999] as double[]
        PercentileStats stats = new PercentileStats(percentiles, values)

        when:
        String str = stats.toString()

        then:
        str.contains("P50=1.23ms")
        str.contains("P95=10.68ms")
        str.contains("P99=100.00ms")
    }
}
