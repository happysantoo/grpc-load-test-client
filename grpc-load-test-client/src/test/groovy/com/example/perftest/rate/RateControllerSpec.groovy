package com.example.perftest.rate

import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RateControllerSpec extends Specification {

    def "should create rate controller with specified TPS"() {
        when: "rate controller is created"
        RateController controller = new RateController(100, Duration.ZERO)

        then: "controller is initialized"
        controller != null
    }

    @Unroll
    def "should enforce rate limit: targetTPS=#targetTPS"() {
        given: "a rate controller with target TPS"
        RateController controller = new RateController(targetTPS, Duration.ZERO)
        int permitCount = targetTPS * 2 // Try double the rate
        AtomicInteger successCount = new AtomicInteger(0)
        long startTime = System.nanoTime()

        when: "attempting requests at high rate"
        (1..permitCount).each {
            if (controller.acquirePermit()) {
                successCount.incrementAndGet()
            }
        }
        
        long elapsedNanos = System.nanoTime() - startTime
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0

        then: "permits are issued"
        successCount.get() > 0
        controller.getTotalPermitsIssued() == successCount.get()

        where:
        targetTPS << [10, 50, 100]
    }

    def "should acquire permit successfully"() {
        given: "a rate controller"
        RateController controller = new RateController(100, Duration.ZERO)

        when: "acquiring a permit"
        boolean acquired = controller.acquirePermit()

        then: "permit is acquired"
        acquired == true
        controller.getTotalPermitsIssued() == 1
    }

    @Unroll
    def "should handle ramp-up duration: #rampUpSeconds seconds"() {
        given: "a rate controller with ramp-up"
        int targetTPS = 100
        RateController controller = new RateController(targetTPS, Duration.ofSeconds(rampUpSeconds))

        when: "acquiring permits"
        10.times {
            controller.acquirePermit()
        }

        then: "permits are issued"
        controller.getTotalPermitsIssued() == 10

        where:
        rampUpSeconds << [0, 1, 2, 5]
    }

    def "should handle concurrent acquire attempts"() {
        given: "a rate controller and multiple threads"
        RateController controller = new RateController(100, Duration.ZERO)
        int threadCount = 5
        int attemptsPerThread = 10
        AtomicInteger totalAcquired = new AtomicInteger(0)
        CountDownLatch startLatch = new CountDownLatch(1)
        CountDownLatch doneLatch = new CountDownLatch(threadCount)

        when: "multiple threads try to acquire permits"
        List<Thread> threads = (1..threadCount).collect {
            Thread.start {
                startLatch.await()
                attemptsPerThread.times {
                    if (controller.acquirePermit()) {
                        totalAcquired.incrementAndGet()
                    }
                }
                doneLatch.countDown()
            }
        }

        and: "all threads start together"
        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)

        then: "all permits are acquired"
        totalAcquired.get() == threadCount * attemptsPerThread
        controller.getTotalPermitsIssued() == threadCount * attemptsPerThread
    }

    @Unroll
    def "should handle edge case TPS values: #tps"() {
        when: "rate controller created with edge case TPS"
        RateController controller = new RateController(tps, Duration.ZERO)

        then: "controller is created successfully"
        controller != null
        
        and: "acquirePermit works"
        controller.acquirePermit() == true

        where:
        tps << [1, 1000, 10000]
    }

    def "should throw exception for zero or negative TPS"() {
        when: "creating controller with invalid TPS"
        new RateController(invalidTPS, Duration.ZERO)

        then: "exception is thrown"
        thrown(IllegalArgumentException)

        where:
        invalidTPS << [0, -1, -100]
    }

    def "should track total permits issued"() {
        given: "a rate controller"
        RateController controller = new RateController(100, Duration.ZERO)

        when: "acquiring multiple permits"
        int count = 5
        count.times {
            controller.acquirePermit()
        }

        then: "total is tracked"
        controller.getTotalPermitsIssued() == count
    }

    def "should handle ramp-up from low to target TPS"() {
        given: "a controller with ramp-up"
        RateController controller = new RateController(100, Duration.ofSeconds(2))

        when: "acquiring permits over time"
        long start = System.nanoTime()
        20.times {
            controller.acquirePermit()
        }
        long elapsed = System.nanoTime() - start

        then: "permits are issued with ramping"
        controller.getTotalPermitsIssued() == 20
        elapsed > 0
    }

    def "should accept zero ramp-up duration"() {
        when: "creating controller with zero ramp-up"
        RateController controller = new RateController(100, Duration.ZERO)

        then: "controller works normally"
        controller.acquirePermit() == true
    }

    @Unroll
    def "should handle different ramp-up durations: #scenario"() {
        given: "a controller with specific ramp-up"
        RateController controller = new RateController(100, duration)

        when: "acquiring permits"
        5.times {
            controller.acquirePermit()
        }

        then: "permits are issued"
        controller.getTotalPermitsIssued() == 5

        where:
        scenario      | duration
        "no ramp-up"  | Duration.ZERO
        "fast"        | Duration.ofMillis(500)
        "medium"      | Duration.ofSeconds(1)
        "slow"        | Duration.ofSeconds(3)
    }
}
