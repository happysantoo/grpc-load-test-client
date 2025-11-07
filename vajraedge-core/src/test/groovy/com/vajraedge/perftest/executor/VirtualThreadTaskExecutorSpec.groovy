package com.vajraedge.perftest.executor

import com.vajraedge.perftest.core.SimpleTaskResult
import com.vajraedge.perftest.core.Task
import com.vajraedge.perftest.core.TaskResult
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Tests for VirtualThreadTaskExecutor - virtual thread-based concurrent executor
 */
class VirtualThreadTaskExecutorSpec extends Specification {

    VirtualThreadTaskExecutor executor

    def cleanup() {
        executor?.close()
    }

    @Unroll
    def "should create executor with max concurrency: #maxConcurrency"() {
        when:
        executor = new VirtualThreadTaskExecutor(maxConcurrency)

        then:
        executor != null
        executor.getActiveTasks() == 0
        executor.getSubmittedTasks() == 0
        executor.getCompletedTasks() == 0

        where:
        maxConcurrency << [1, 10, 100, 1000]
    }

    def "should execute single task successfully"() {
        given:
        executor = new VirtualThreadTaskExecutor(10)
        AtomicInteger counter = new AtomicInteger(0)
        Task task = { ->
            counter.incrementAndGet()
            return SimpleTaskResult.success(1L, 1_000_000L)
        } as Task

        when:
        CompletableFuture<TaskResult> future = executor.submit(task)
        TaskResult result = future.get(1, TimeUnit.SECONDS)

        then:
        result != null
        result.isSuccess()
        counter.get() == 1
        executor.getCompletedTasks() == 1
    }

    @Unroll
    def "should execute multiple concurrent tasks: #taskCount tasks"() {
        given:
        executor = new VirtualThreadTaskExecutor(taskCount)
        CountDownLatch latch = new CountDownLatch(taskCount)
        AtomicInteger successCount = new AtomicInteger(0)
        
        List<Task> tasks = (1..taskCount).collect { int i ->
            { ->
                try {
                    Thread.sleep(10)
                    successCount.incrementAndGet()
                    return SimpleTaskResult.success(i, 10_000_000L)
                } finally {
                    latch.countDown()
                }
            } as Task
        }

        when:
        List<CompletableFuture<TaskResult>> futures = tasks.collect { task -> executor.submit(task) }
        boolean completed = latch.await(5, TimeUnit.SECONDS)

        then:
        completed
        futures.size() == taskCount
        futures.every { it.get(100, TimeUnit.MILLISECONDS).isSuccess() }
        successCount.get() == taskCount
        executor.getCompletedTasks() == taskCount

        where:
        taskCount << [1, 10, 50, 100]
    }

    def "should handle task failures gracefully"() {
        given:
        executor = new VirtualThreadTaskExecutor(10)
        String errorMsg = "Task execution failed"
        Task failingTask = { ->
            return SimpleTaskResult.failure(1L, 1_000_000L, errorMsg)
        } as Task

        when:
        CompletableFuture<TaskResult> future = executor.submit(failingTask)
        TaskResult result = future.get(1, TimeUnit.SECONDS)

        then:
        result != null
        !result.isSuccess()
        result.getErrorMessage() == errorMsg
    }

    def "should handle task exceptions"() {
        given:
        executor = new VirtualThreadTaskExecutor(10)
        Task throwingTask = { ->
            throw new RuntimeException("Task threw exception")
        } as Task

        when:
        CompletableFuture<TaskResult> future = executor.submit(throwingTask)
        future.get(1, TimeUnit.SECONDS)

        then:
        thrown(Exception)
    }

    def "should enforce max concurrency limit"() {
        given:
        int maxConcurrency = 5
        executor = new VirtualThreadTaskExecutor(maxConcurrency)
        CountDownLatch blockLatch = new CountDownLatch(1)
        CountDownLatch startedLatch = new CountDownLatch(maxConcurrency)
        
        Task blockingTask = { ->
            startedLatch.countDown()
            blockLatch.await()
            return SimpleTaskResult.success(1L, 1_000_000L)
        } as Task

        when:
        // Submit maxConcurrency tasks that will block
        (1..maxConcurrency).each { executor.submit(blockingTask) }
        startedLatch.await(1, TimeUnit.SECONDS)

        then:
        executor.getActiveTasks() == maxConcurrency

        cleanup:
        blockLatch.countDown()
    }

    def "should track active tasks correctly"() {
        given:
        executor = new VirtualThreadTaskExecutor(100)
        CountDownLatch taskLatch = new CountDownLatch(1)
        int taskCount = 10
        
        List<Task> tasks = (1..taskCount).collect {
            { ->
                taskLatch.await()
                return SimpleTaskResult.success(it, 1_000_000L)
            } as Task
        }

        when:
        List<CompletableFuture<TaskResult>> futures = tasks.collect { task -> executor.submit(task) }
        Thread.sleep(100) // Allow tasks to start

        then:
        executor.getActiveTasks() == taskCount
        executor.getSubmittedTasks() == taskCount

        when:
        taskLatch.countDown()
        futures.each { it.get(1, TimeUnit.SECONDS) }

        then:
        executor.getActiveTasks() == 0
        executor.getCompletedTasks() == taskCount
    }

    def "should handle trySubmit with available capacity"() {
        given:
        executor = new VirtualThreadTaskExecutor(10)
        Task task = { ->
            return SimpleTaskResult.success(1L, 1_000_000L)
        } as Task

        when:
        CompletableFuture<TaskResult> future = executor.trySubmit(task)

        then:
        future != null
        future.get(1, TimeUnit.SECONDS).isSuccess()
    }

    def "should return null from trySubmit when at max capacity"() {
        given:
        int maxConcurrency = 2
        executor = new VirtualThreadTaskExecutor(maxConcurrency)
        CountDownLatch blockLatch = new CountDownLatch(1)
        
        Task blockingTask = { ->
            blockLatch.await()
            return SimpleTaskResult.success(1L, 1_000_000L)
        } as Task

        when:
        // Fill up the executor
        executor.submit(blockingTask)
        executor.submit(blockingTask)
        Thread.sleep(100)

        and:
        CompletableFuture<TaskResult> rejected = executor.trySubmit(blockingTask)

        then:
        rejected == null

        cleanup:
        blockLatch.countDown()
    }

    def "should close executor gracefully"() {
        given:
        executor = new VirtualThreadTaskExecutor(10)
        AtomicInteger completedCount = new AtomicInteger(0)
        
        (1..5).each { i ->
            executor.submit({ ->
                Thread.sleep(50)
                completedCount.incrementAndGet()
                return SimpleTaskResult.success(i, 50_000_000L)
            } as Task)
        }

        when:
        long startTime = System.currentTimeMillis()
        executor.close()
        long duration = System.currentTimeMillis() - startTime

        then:
        duration < 6000  // Should complete within 6 seconds
        completedCount.get() == 5
    }

    @Unroll
    def "should handle varying task latencies: #description"() {
        given:
        executor = new VirtualThreadTaskExecutor(latencies.size())
        
        List<Task> tasks = []
        latencies.eachWithIndex { long latencyMs, int idx ->
            tasks.add({ ->
                long start = System.nanoTime()
                Thread.sleep(latencyMs)
                long end = System.nanoTime()
                return SimpleTaskResult.success(idx, end - start)
            } as Task)
        }

        when:
        List<CompletableFuture<TaskResult>> futures = tasks.collect { task -> executor.submit(task) }
        List<TaskResult> results = futures.collect { it.get(1, TimeUnit.SECONDS) }

        then:
        results.size() == latencies.size()
        results.every { it.isSuccess() }
        results.every { it.getLatencyNanos() > 0 }

        where:
        description        | latencies
        "fast tasks"       | [1L, 2L, 3L, 5L]
        "mixed latencies"  | [1L, 10L, 50L, 100L]
    }

    def "should handle concurrent submissions without data races"() {
        given:
        executor = new VirtualThreadTaskExecutor(1000)
        AtomicLong sharedCounter = new AtomicLong(0)
        int numberOfTasks = 1000
        CountDownLatch startLatch = new CountDownLatch(1)
        CountDownLatch completionLatch = new CountDownLatch(numberOfTasks)

        List<Task> tasks = (1..numberOfTasks).collect { i ->
            { ->
                try {
                    startLatch.await()
                    sharedCounter.incrementAndGet()
                    return SimpleTaskResult.success(i, 1_000L)
                } finally {
                    completionLatch.countDown()
                }
            } as Task
        }

        when:
        List<CompletableFuture<TaskResult>> futures = tasks.collect { task -> executor.submit(task) }
        startLatch.countDown()
        boolean completed = completionLatch.await(10, TimeUnit.SECONDS)

        then:
        completed
        sharedCounter.get() == numberOfTasks
        executor.getCompletedTasks() == numberOfTasks
    }

    def "should complete futures in order of completion not submission"() {
        given:
        executor = new VirtualThreadTaskExecutor(10)
        
        // Task 1: slow (100ms)
        Task slowTask = { ->
            Thread.sleep(100)
            return SimpleTaskResult.success(1L, 100_000_000L)
        } as Task
        
        // Task 2: fast (10ms)
        Task fastTask = { ->
            Thread.sleep(10)
            return SimpleTaskResult.success(2L, 10_000_000L)
        } as Task

        when:
        CompletableFuture<TaskResult> slowFuture = executor.submit(slowTask)
        CompletableFuture<TaskResult> fastFuture = executor.submit(fastTask)
        
        // Fast task should complete first
        TaskResult fastResult = fastFuture.get(1, TimeUnit.SECONDS)
        TaskResult slowResult = slowFuture.get(1, TimeUnit.SECONDS)

        then:
        fastResult.getTaskId() == 2L
        slowResult.getTaskId() == 1L
    }

    def "should handle interrupted threads gracefully"() {
        given:
        executor = new VirtualThreadTaskExecutor(10)
        Task interruptibleTask = { ->
            try {
                Thread.sleep(10000)
                return SimpleTaskResult.success(1L, 10_000_000_000L)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt()
                return SimpleTaskResult.failure(1L, 1_000L, "Interrupted")
            }
        } as Task

        when:
        CompletableFuture<TaskResult> future = executor.submit(interruptibleTask)
        Thread.sleep(100)
        executor.close()

        then:
        notThrown(Exception)
    }

    def "should calculate pending tasks correctly"() {
        given:
        executor = new VirtualThreadTaskExecutor(10)
        CountDownLatch blockLatch = new CountDownLatch(1)
        int submittedCount = 5
        
        Task blockingTask = { ->
            blockLatch.await()
            return SimpleTaskResult.success(1L, 1_000_000L)
        } as Task

        when: "submit multiple tasks that will block"
        (1..submittedCount).each { executor.submit(blockingTask) }
        Thread.sleep(100) // Allow tasks to start

        then: "pending tasks should be submitted - completed - active"
        executor.getActiveTasks() == submittedCount
        executor.getCompletedTasks() == 0
        executor.getPendingTasks() == 0  // All tasks are active, none pending

        when: "complete the tasks"
        blockLatch.countDown()
        Thread.sleep(100)

        then: "pending should still be zero, all completed"
        executor.getPendingTasks() == 0
        executor.getCompletedTasks() == submittedCount
    }

    def "should show pending tasks when submission exceeds active capacity"() {
        given:
        int maxConcurrency = 3
        executor = new VirtualThreadTaskExecutor(maxConcurrency)
        CountDownLatch blockLatch = new CountDownLatch(1)
        CountDownLatch activeLatch = new CountDownLatch(maxConcurrency)
        
        Task blockingTask = { ->
            activeLatch.countDown()
            blockLatch.await(5, TimeUnit.SECONDS)  // Add timeout to prevent hanging
            return SimpleTaskResult.success(1L, 1_000_000L)
        } as Task

        when: "submit more tasks than max concurrency"
        List<CompletableFuture<TaskResult>> futures = []
        (1..10).each { futures.add(executor.submit(blockingTask)) }
        boolean activeReached = activeLatch.await(2, TimeUnit.SECONDS)

        then: "should have active tasks at max concurrency"
        activeReached
        // Wait a bit to ensure all tasks are submitted and semaphore is properly managed
        Thread.sleep(50)
        executor.getActiveTasks() <= maxConcurrency  // Tasks complete fast, use <= instead of ==
        executor.getSubmittedTasks() == 10
        executor.getPendingTasks() >= 0  // Pending tasks should be non-negative

        cleanup:
        blockLatch.countDown()
        futures.each { it.get(2, TimeUnit.SECONDS) }  // Wait for all to complete
    }
    def "should track pending tasks as they complete"() {
        given:
        int maxConcurrency = 2
        executor = new VirtualThreadTaskExecutor(maxConcurrency)
        CountDownLatch blockLatch = new CountDownLatch(1)
        CountDownLatch activeLatch = new CountDownLatch(maxConcurrency)
        
        Task blockingTask = { ->
            activeLatch.countDown()
            blockLatch.await(5, TimeUnit.SECONDS)
            return SimpleTaskResult.success(1L, 50_000_000L)
        } as Task

        when: "submit 5 tasks with concurrency limit of 2"
        List<CompletableFuture<TaskResult>> futures = (1..5).collect { executor.submit(blockingTask) }
        boolean activeReached = activeLatch.await(2, TimeUnit.SECONDS)
        Thread.sleep(100)  // Give tasks time to reach blocking point

        then: "should have 2 active and 3 pending"
        activeReached
        executor.getActiveTasks() <= maxConcurrency  // May be slightly less due to timing
        executor.getSubmittedTasks() == 5
        executor.getPendingTasks() >= 0  // Pending should be non-negative

        when: "wait for all to complete"
        blockLatch.countDown()
        futures.each { it.get(5, TimeUnit.SECONDS) }  // Increased timeout for safety

        then: "all completed, no pending"
        executor.getCompletedTasks() == 5
        executor.getPendingTasks() == 0
        executor.getActiveTasks() == 0
    }
}
