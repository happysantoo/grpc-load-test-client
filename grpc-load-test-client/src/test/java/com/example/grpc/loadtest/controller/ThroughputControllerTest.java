package com.example.grpc.loadtest.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ThroughputControllerTest {

    @Test
    void shouldCreateControllerWithValidTPS() {
        ThroughputController controller = new ThroughputController(100);

        assertEquals(100, controller.getTargetTps());
        assertEquals(100, controller.getCurrentTps());
        assertFalse(controller.isInRampUp());
        assertEquals(100.0, controller.getRampUpProgress(), 0.1);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100})
    void shouldRejectInvalidTPS(int invalidTps) {
        assertThrows(IllegalArgumentException.class, () -> new ThroughputController(invalidTps));
    }

    @Test
    void shouldCreateControllerWithRampUp() {
        ThroughputController controller = new ThroughputController(100, Duration.ofSeconds(10));

        assertEquals(100, controller.getTargetTps());
        assertTrue(controller.isInRampUp());
        assertTrue(controller.getRampUpProgress() >= 0.0);
        assertTrue(controller.getRampUpProgress() <= 100.0);
        assertTrue(controller.getCurrentTps() >= 1);
        assertTrue(controller.getCurrentTps() <= 100);
    }

    @Test
    void shouldTryAcquirePermitWithoutBlocking() {
        ThroughputController controller = new ThroughputController(1); // 1 TPS = 1 second interval

        boolean firstPermit = controller.tryAcquirePermit();
        boolean secondPermit = controller.tryAcquirePermit();

        assertTrue(firstPermit);
        assertFalse(secondPermit); // Should not wait for next permit
    }

    @Test
    void shouldTrackTotalPermitsIssued() {
        ThroughputController controller = new ThroughputController(10000); // Very high TPS to avoid any blocking

        int permitsGranted = 0;
        for (int i = 0; i < 5; i++) {
            if (controller.tryAcquirePermit()) {
                permitsGranted++;
            }
        }

        assertEquals(permitsGranted, controller.getTotalPermitsIssued());
        assertTrue(permitsGranted > 0); // At least some permits should be granted
    }

    @Test
    void shouldResetCorrectly() {
        ThroughputController controller = new ThroughputController(100);
        controller.tryAcquirePermit(); // Issue at least one permit

        controller.reset();

        assertEquals(0, controller.getTotalPermitsIssued());
    }

    @Test
    void shouldHandleZeroRampUpDuration() {
        ThroughputController controller = new ThroughputController(100, Duration.ZERO);

        assertFalse(controller.isInRampUp());
        assertEquals(100.0, controller.getRampUpProgress(), 0.1);
        assertEquals(100, controller.getCurrentTps());
    }

    @Test
    void shouldProvideTimeUntilNextPermit() {
        ThroughputController controller = new ThroughputController(10); // 100ms intervals

        controller.tryAcquirePermit(); // First permit granted immediately
        Duration timeUntilNext = controller.getTimeUntilNextPermit();

        assertTrue(timeUntilNext.toMillis() >= 0);
        assertTrue(timeUntilNext.toMillis() <= 200); // Allow some tolerance
    }

    @Test
    void shouldProvideThroughputStatistics() {
        ThroughputController controller = new ThroughputController(50, Duration.ofSeconds(1));

        ThroughputController.ThroughputStats stats = controller.getStats();

        assertEquals(50, stats.getTargetTps());
        assertTrue(stats.getCurrentTps() >= 1);
        assertTrue(stats.getCurrentTps() <= 50);
        assertEquals(0, stats.getTotalPermits());
        assertTrue(stats.isInRampUp());
        assertTrue(stats.getRampUpProgress() >= 0.0);
        assertTrue(stats.getRampUpProgress() <= 100.0);
        assertNotNull(stats.getTimeUntilNextPermit());
    }

    @Test
    void shouldProvideStatisticsToString() {
        ThroughputController controller = new ThroughputController(50, Duration.ofSeconds(1));

        ThroughputController.ThroughputStats stats = controller.getStats();
        String statsString = stats.toString();

        assertTrue(statsString.contains("ThroughputStats"));
        assertTrue(statsString.contains("target=50"));
        assertTrue(statsString.contains("rampUp="));
    }

    @Test
    void shouldHandleStatisticsWithoutRampUp() {
        ThroughputController controller = new ThroughputController(100);

        ThroughputController.ThroughputStats stats = controller.getStats();
        String statsString = stats.toString();

        assertTrue(statsString.contains("ThroughputStats{target=100"));
        assertTrue(statsString.contains("actual="));
        assertTrue(statsString.contains("permits=0}"));
    }

    @Test
    void shouldCalculateActualTPSOverTime() throws InterruptedException {
        ThroughputController controller = new ThroughputController(1000);

        // Issue some permits
        for (int i = 0; i < 10; i++) {
            controller.tryAcquirePermit();
        }
        Thread.sleep(100); // Wait a bit for time to pass

        assertTrue(controller.getActualTps() >= 0);
    }

    @Test
    void shouldHandleZeroElapsedTime() {
        // Test Issue #3: Division by zero in getActualTps()
        ThroughputController controller = new ThroughputController(100);
        
        // Call getActualTps immediately after creation (elapsed < 1 second)
        double actualTps = controller.getActualTps();
        
        assertEquals(0.0, actualTps); // Should return 0.0 when elapsed time is too small
    }

    @Test
    void shouldHandleConcurrentTryAcquirePermit() throws InterruptedException {
        // Test Issue #11: Race condition in tryAcquirePermit()
        ThroughputController controller = new ThroughputController(10); // Low TPS to force rollbacks
        AtomicInteger successCount = new AtomicInteger(0);
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        // Create multiple threads trying to acquire permits simultaneously
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    if (controller.tryAcquirePermit()) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown(); // Start all threads simultaneously
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));

        // The exact number of successful permits is not deterministic due to timing,
        // but we should have at least 1 and the count should be consistent
        long totalPermits = controller.getTotalPermitsIssued();
        assertEquals(successCount.get(), totalPermits);
        assertTrue(totalPermits > 0);
    }
}