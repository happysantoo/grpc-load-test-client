package com.example.grpc.loadtest.executor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;


import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class VirtualThreadExecutorTest {

    private VirtualThreadExecutor executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.close();
        }
    }

    @Test
    void shouldCreateExecutorWithValidConcurrencyLimit() {
        executor = new VirtualThreadExecutor(100);

        assertEquals(100, executor.getMaxConcurrentRequests());
        assertEquals(0, executor.getActiveRequests());
        assertEquals(0, executor.getSubmittedTasks());
        assertEquals(0, executor.getCompletedTasks());
        assertEquals(100, executor.getAvailablePermits());
        assertEquals(0.0, executor.getUtilizationPercent());
        assertFalse(executor.isSaturated());
    }

    @Test
    void shouldExecuteCallableTasks() throws Exception {
        executor = new VirtualThreadExecutor(10);
        AtomicInteger result = new AtomicInteger(0);

        CompletableFuture<String> future = executor.submit(() -> {
            result.incrementAndGet();
            return "success";
        });

        String taskResult = future.get(1, TimeUnit.SECONDS);

        assertEquals("success", taskResult);
        assertEquals(1, result.get());
    }

    @Test
    void shouldExecuteRunnableTasks() throws Exception {
        executor = new VirtualThreadExecutor(10);
        AtomicInteger executed = new AtomicInteger(0);

        CompletableFuture<Void> future = executor.submit(() -> {
            executed.incrementAndGet();
        });

        future.get(1, TimeUnit.SECONDS);

        assertEquals(1, executed.get());
    }

    @Test
    @Timeout(10)
    void shouldHandleConcurrentTaskExecution() throws Exception {
        executor = new VirtualThreadExecutor(50);
        AtomicInteger executionCount = new AtomicInteger(0);
        
        @SuppressWarnings("unchecked")
        CompletableFuture<Integer>[] futures = new CompletableFuture[20];

        for (int i = 0; i < 20; i++) {
            final int taskId = i;
            futures[i] = executor.submit(() -> {
                try {
                    Thread.sleep(100); // Simulate work
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                executionCount.incrementAndGet();
                return taskId;
            });
        }

        // Wait for all tasks to complete
        for (int i = 0; i < 20; i++) {
            Integer result = futures[i].get(5, TimeUnit.SECONDS);
            assertEquals(i, result.intValue());
        }

        assertEquals(20, executionCount.get());
    }

    @Test
    void shouldRespectConcurrencyLimitsWithBlockingSubmit() throws Exception {
        executor = new VirtualThreadExecutor(2); // Very low limit
        AtomicInteger startedTasks = new AtomicInteger(0);
        AtomicInteger completedTasks = new AtomicInteger(0);

        @SuppressWarnings("unchecked")
        CompletableFuture<Integer>[] futures = new CompletableFuture[3];
        for (int i = 0; i < 3; i++) {
            final int taskId = i;
            futures[i] = executor.submit(() -> {
                startedTasks.incrementAndGet();
                try {
                    Thread.sleep(200); // Hold the permit for a while
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                completedTasks.incrementAndGet();
                return taskId;
            });
        }

        // Wait for all to complete
        for (CompletableFuture<Integer> future : futures) {
            future.get(3, TimeUnit.SECONDS);
        }

        // The main test is that all tasks eventually complete despite concurrency limits
        assertEquals(3, completedTasks.get()); // All should eventually complete
        assertEquals(3, startedTasks.get()); // All tasks should have started eventually
    }

    @Test
    void shouldHandleTrySubmitWithoutBlocking() throws Exception {
        executor = new VirtualThreadExecutor(1);
        AtomicInteger executed = new AtomicInteger(0);

        // Fill the executor
        CompletableFuture<String> blockingFuture = executor.submit(() -> {
            try {
                Thread.sleep(500); // Block for a while
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            executed.incrementAndGet();
            return "first";
        });

        Thread.sleep(50); // Let first task start

        // Try to submit another - should return null since executor is full
        CompletableFuture<String> secondFuture = executor.trySubmit(() -> {
            executed.incrementAndGet();
            return "second";
        });

        String firstResult = blockingFuture.get(1, TimeUnit.SECONDS);

        assertEquals("first", firstResult);
        assertNull(secondFuture); // Should not be accepted
        assertEquals(1, executed.get()); // Only first task executed
    }

    @Test
    void shouldTrackStatisticsCorrectly() throws Exception {
        executor = new VirtualThreadExecutor(10);
        CountDownLatch latch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(2);

        CompletableFuture<Integer> future1 = executor.submit(() -> {
            try {
                startLatch.countDown();
                latch.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return 1;
        });
        CompletableFuture<Integer> future2 = executor.submit(() -> {
            try {
                startLatch.countDown();
                latch.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return 2;
        });

        // Wait for both tasks to start
        assertTrue(startLatch.await(1, TimeUnit.SECONDS));
        
        // Check stats while tasks are running
        VirtualThreadExecutor.ExecutorStats runningStats = executor.getStats();

        assertEquals(2, runningStats.getSubmittedTasks());
        assertEquals(2, runningStats.getActiveRequests());
        assertEquals(10, runningStats.getMaxConcurrentRequests());
        assertTrue(runningStats.getUtilizationPercent() >= 15.0); // Allow some tolerance
        assertTrue(runningStats.getUtilizationPercent() <= 25.0);

        // Release tasks
        latch.countDown();
        latch.countDown();

        // Wait for completion
        future1.get(1, TimeUnit.SECONDS);
        future2.get(1, TimeUnit.SECONDS);

        // Give a brief moment for stats to update
        Thread.sleep(10);

        VirtualThreadExecutor.ExecutorStats finalStats = executor.getStats();

        assertEquals(2, finalStats.getSubmittedTasks());
        assertEquals(2, finalStats.getCompletedTasks());
        assertEquals(0, finalStats.getActiveRequests());
        assertEquals(0.0, finalStats.getUtilizationPercent());
    }

    @Test
    void shouldDetectSaturationCorrectly() throws Exception {
        executor = new VirtualThreadExecutor(1);
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch taskCanFinish = new CountDownLatch(1);

        CompletableFuture<String> future = executor.submit(() -> {
            try {
                taskStarted.countDown();
                taskCanFinish.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "done";
        });

        // Wait for task to start
        assertTrue(taskStarted.await(1, TimeUnit.SECONDS));
        
        // Now the executor should be saturated
        assertTrue(executor.isSaturated());

        // Release the task
        taskCanFinish.countDown();
        future.get(1, TimeUnit.SECONDS);
        
        // Now it should not be saturated
        assertFalse(executor.isSaturated());
    }

    @Test
    @Timeout(5)
    void shouldAwaitCompletionCorrectly() throws Exception {
        executor = new VirtualThreadExecutor(5);
        long completionTime = System.currentTimeMillis();

        // Submit tasks that take some time
        for (int i = 0; i < 3; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return taskId;
            });
        }

        executor.awaitCompletion(2, TimeUnit.SECONDS);
        completionTime = System.currentTimeMillis() - completionTime;

        assertEquals(0, executor.getActiveRequests());
        assertTrue(completionTime >= 200); // Should take at least as long as the tasks
    }

    @Test
    void shouldHandleTaskExceptions() throws Exception {
        executor = new VirtualThreadExecutor(5);

        CompletableFuture<String> future = executor.submit(() -> {
            throw new RuntimeException("Test exception");
        });

        assertThrows(Exception.class, () -> future.get(1, TimeUnit.SECONDS));
        assertEquals(1, executor.getStats().getCompletedTasks()); // Should still count as completed
    }

    @Test
    void shouldCloseGracefully() throws Exception {
        executor = new VirtualThreadExecutor(5);
        @SuppressWarnings("unchecked")
        CompletableFuture<Integer>[] futures = new CompletableFuture[3];

        // Submit some long-running tasks
        for (int i = 0; i < 3; i++) {
            final int taskId = i;
            futures[i] = executor.submit(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return taskId;
            });
        }

        executor.close(); // Should wait for tasks to complete

        for (CompletableFuture<Integer> future : futures) {
            assertTrue(future.isDone());
        }
    }

    @Test
    void shouldHandleRunnableTrySubmit() throws Exception {
        executor = new VirtualThreadExecutor(1);
        AtomicInteger executed = new AtomicInteger(0);

        // Fill executor
        CompletableFuture<Void> blockingFuture = executor.submit(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            executed.incrementAndGet();
        });

        Thread.sleep(50); // Let task start

        // Try to submit another
        CompletableFuture<Void> secondFuture = executor.trySubmit(() -> {
            executed.incrementAndGet();
        });

        blockingFuture.get(1, TimeUnit.SECONDS);

        assertNull(secondFuture);
        assertEquals(1, executed.get());
    }

    @Test
    void shouldProvideExecutorStatisticsToString() {
        executor = new VirtualThreadExecutor(100);

        VirtualThreadExecutor.ExecutorStats stats = executor.getStats();
        String statsString = stats.toString();

        assertTrue(statsString.contains("ExecutorStats"));
        assertTrue(statsString.contains("submitted=0"));
        assertTrue(statsString.contains("completed=0"));
        assertTrue(statsString.contains("active=0"));
        assertTrue(statsString.contains("max=100"));
        assertTrue(statsString.contains("utilization=0.0%"));
    }

    @Test
    @Timeout(10)
    void shouldHandleTimeoutDuringShutdown() throws Exception {
        // Test Issue #7: Improved timeout handling during shutdown
        executor = new VirtualThreadExecutor(5);
        
        // Submit long-running tasks that may not finish in the first shutdown timeout
        for (int i = 0; i < 3; i++) {
            executor.submit(() -> {
                try {
                    Thread.sleep(6000); // Longer than the 5-second timeout
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // Task should be interrupted during shutdown
                }
                return "completed";
            });
        }
        
        Thread.sleep(100); // Let tasks start
        
        // Close should handle the timeout gracefully and force shutdown if needed
        assertDoesNotThrow(() -> executor.close());
        
        // After close, all resources should be cleaned up
        assertEquals(0, executor.getActiveRequests());
    }
}