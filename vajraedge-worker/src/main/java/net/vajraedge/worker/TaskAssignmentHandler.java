package net.vajraedge.worker;

import net.vajraedge.perftest.proto.TaskAssignment;
import net.vajraedge.perftest.proto.TaskAssignmentResponse;
import net.vajraedge.sdk.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles task assignments from the controller.
 * Instantiates tasks, manages execution, and reports results.
 */
public class TaskAssignmentHandler {
    private static final Logger log = LoggerFactory.getLogger(TaskAssignmentHandler.class);
    
    private final TaskRegistry taskRegistry;
    private final TaskExecutorService executorService;
    private final GrpcClient grpcClient;
    
    // Track active test executions
    private final Map<String, TestExecution> activeTests = new ConcurrentHashMap<>();
    
    public TaskAssignmentHandler(TaskRegistry taskRegistry, 
                                  TaskExecutorService executorService,
                                  GrpcClient grpcClient) {
        this.taskRegistry = taskRegistry;
        this.executorService = executorService;
        this.grpcClient = grpcClient;
    }
    
    /**
     * Handle an incoming task assignment from the controller.
     *
     * @param assignment Task assignment details
     * @return Response indicating acceptance or rejection
     */
    public TaskAssignmentResponse handleAssignment(TaskAssignment assignment) {
        String testId = assignment.getTestId();
        String taskType = assignment.getTaskType();
        
        log.info("Received task assignment: testId={}, taskType={}, targetTps={}, duration={}s",
                testId, taskType, assignment.getTargetTps(), assignment.getDurationSeconds());
        
        // Check if task type is supported
        Class<? extends Task> taskClass = taskRegistry.getTaskClass(taskType);
        if (taskClass == null) {
            String errorMsg = "Task type not supported: " + taskType;
            log.error(errorMsg);
            return TaskAssignmentResponse.newBuilder()
                    .setAccepted(false)
                    .setMessage(errorMsg)
                    .setEstimatedTaskCount(0)
                    .build();
        }
        
        // Check if already running a test with this ID
        if (activeTests.containsKey(testId)) {
            String errorMsg = "Test already running with ID: " + testId;
            log.warn(errorMsg);
            return TaskAssignmentResponse.newBuilder()
                    .setAccepted(false)
                    .setMessage(errorMsg)
                    .setEstimatedTaskCount(0)
                    .build();
        }
        
        // Check capacity
        if (!executorService.canAcceptMore()) {
            String errorMsg = "Worker at capacity, cannot accept more tasks";
            log.warn(errorMsg);
            return TaskAssignmentResponse.newBuilder()
                    .setAccepted(false)
                    .setMessage(errorMsg)
                    .setEstimatedTaskCount(0)
                    .build();
        }
        
        try {
            // Create task instance
            Task task = createTaskInstance(taskClass, assignment.getParametersMap());
            
            // Calculate estimated task count
            long estimatedTasks = (long) assignment.getTargetTps() * assignment.getDurationSeconds();
            
            // Create test execution
            TestExecution execution = new TestExecution(
                    testId,
                    taskType,
                    task,
                    assignment.getTargetTps(),
                    assignment.getDurationSeconds(),
                    assignment.getRampUpSeconds(),
                    assignment.getMaxConcurrency()
            );
            
            activeTests.put(testId, execution);
            
            // Start execution in background
            execution.start();
            
            log.info("Accepted task assignment: testId={}, estimatedTasks={}", testId, estimatedTasks);
            
            return TaskAssignmentResponse.newBuilder()
                    .setAccepted(true)
                    .setMessage("Task accepted and started")
                    .setEstimatedTaskCount(estimatedTasks)
                    .build();
                    
        } catch (Exception e) {
            String errorMsg = "Error creating task instance: " + e.getMessage();
            log.error(errorMsg, e);
            return TaskAssignmentResponse.newBuilder()
                    .setAccepted(false)
                    .setMessage(errorMsg)
                    .setEstimatedTaskCount(0)
                    .build();
        }
    }
    
    /**
     * Create an instance of the task class with parameters.
     * Tries to use a constructor that accepts Map<String, String> for parameters.
     * Falls back to no-arg constructor if parameter constructor not available.
     */
    private Task createTaskInstance(Class<? extends Task> taskClass, Map<String, String> parameters) 
            throws Exception {
        // Try constructor with Map<String, String> parameter
        try {
            Constructor<? extends Task> constructor = taskClass.getDeclaredConstructor(Map.class);
            return constructor.newInstance(parameters);
        } catch (NoSuchMethodException e) {
            // Fall back to no-arg constructor
            log.debug("No Map constructor found for {}, using no-arg constructor", taskClass.getSimpleName());
        }
        
        // Try no-arg constructor
        try {
            Constructor<? extends Task> constructor = taskClass.getDeclaredConstructor();
            Task task = constructor.newInstance();
            
            if (parameters != null && !parameters.isEmpty()) {
                log.warn("Task {} does not support parameters but {} parameters were provided", 
                        taskClass.getSimpleName(), parameters.size());
            }
            
            return task;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Task class must have either a Map<String, String> constructor " +
                                          "or a no-arg constructor: " + taskClass.getName());
        }
    }
    
    /**
     * Stop a running test.
     *
     * @param testId Test identifier
     * @param graceful Whether to stop gracefully
     * @return true if test was stopped, false if not found
     */
    public boolean stopTest(String testId, boolean graceful) {
        TestExecution execution = activeTests.get(testId);
        
        if (execution == null) {
            log.warn("Cannot stop test {}: not found", testId);
            return false;
        }
        
        log.info("Stopping test {}: graceful={}", testId, graceful);
        execution.stop(graceful);
        activeTests.remove(testId);
        
        return true;
    }
    
    /**
     * Get active test count.
     */
    public int getActiveTestCount() {
        return activeTests.size();
    }
    
    /**
     * Represents an active test execution.
     */
    private class TestExecution {
        private final String testId;
        private final String taskType;
        private final Task task;
        private final int targetTps;
        private final long durationSeconds;
        private final int rampUpSeconds;
        private final int maxConcurrency;
        
        private final AtomicBoolean running = new AtomicBoolean(false);
        private Thread executionThread;
        
        public TestExecution(String testId, String taskType, Task task,
                           int targetTps, long durationSeconds,
                           int rampUpSeconds, int maxConcurrency) {
            this.testId = testId;
            this.taskType = taskType;
            this.task = task;
            this.targetTps = targetTps;
            this.durationSeconds = durationSeconds;
            this.rampUpSeconds = rampUpSeconds;
            this.maxConcurrency = maxConcurrency;
        }
        
        public void start() {
            if (running.compareAndSet(false, true)) {
                executionThread = Thread.ofVirtual().start(() -> executeTest());
                log.info("Started test execution: testId={}", testId);
            }
        }
        
        public void stop(boolean graceful) {
            if (running.compareAndSet(true, false)) {
                if (!graceful && executionThread != null) {
                    executionThread.interrupt();
                }
                log.info("Stopped test execution: testId={}, graceful={}", testId, graceful);
            }
        }
        
        private void executeTest() {
            long startTime = System.currentTimeMillis();
            long endTime = startTime + (durationSeconds * 1000);
            
            log.info("Executing test: testId={}, targetTps={}, duration={}s, rampUp={}s",
                    testId, targetTps, durationSeconds, rampUpSeconds);
            
            // Calculate ramp-up parameters
            long rampUpEndTime = startTime + (rampUpSeconds * 1000L);
            boolean hasRampUp = rampUpSeconds > 0;
            
            int executedTasks = 0;
            
            while (running.get() && System.currentTimeMillis() < endTime) {
                try {
                    // Calculate current TPS based on ramp-up
                    int currentTps = targetTps;
                    if (hasRampUp && System.currentTimeMillis() < rampUpEndTime) {
                        long elapsed = System.currentTimeMillis() - startTime;
                        double rampProgress = (double) elapsed / (rampUpSeconds * 1000.0);
                        currentTps = (int) (targetTps * rampProgress);
                        currentTps = Math.max(1, currentTps); // At least 1 TPS
                    }
                    
                    // Calculate delay between tasks
                    long delayMs = currentTps > 0 ? 1000 / currentTps : 1000;
                    
                    // Execute task
                    executorService.submit(task);
                    executedTasks++;
                    
                    // Sleep to maintain TPS
                    if (delayMs > 0) {
                        Thread.sleep(delayMs);
                    }
                    
                } catch (InterruptedException e) {
                    log.info("Test execution interrupted: testId={}", testId);
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error executing task for testId={}", testId, e);
                }
            }
            
            running.set(false);
            
            long actualDuration = (System.currentTimeMillis() - startTime) / 1000;
            double actualTps = executedTasks / (double) actualDuration;
            
            log.info("Test execution completed: testId={}, executedTasks={}, duration={}s, actualTps={}",
                    testId, executedTasks, actualDuration, String.format("%.2f", actualTps));
        }
    }
}
