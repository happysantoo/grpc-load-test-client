package net.vajraedge.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

/**
 * Worker node that executes tasks from the controller.
 * 
 * <p>Workers are lightweight, stateless executors that:
 * <ul>
 *   <li>Connect to the controller via gRPC</li>
 *   <li>Register their capabilities (plugins available)</li>
 *   <li>Receive task assignments from controller</li>
 *   <li>Execute tasks using virtual threads</li>
 *   <li>Report metrics back to controller</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>{@code
 * WorkerConfig config = WorkerConfig.builder()
 *     .workerId("worker-1")
 *     .controllerAddress("controller.example.com:8080")
 *     .maxConcurrency(10_000)
 *     .build();
 * 
 * Worker worker = new Worker(config);
 * worker.start();
 * }</pre>
 *
 * @since 2.0.0
 */
public class Worker {
    
    private static final Logger log = LoggerFactory.getLogger(Worker.class);
    
    private final WorkerConfig config;
    private final GrpcClient grpcClient;
    private final TaskExecutorService taskExecutor;
    private final MetricsReporter metricsReporter;
    private final CountDownLatch shutdownLatch;
    
    private volatile boolean running;
    
    /**
     * Create a new worker with the given configuration.
     *
     * @param config Worker configuration
     */
    public Worker(WorkerConfig config) {
        this.config = config;
        this.grpcClient = new GrpcClient(config.getControllerAddress());
        this.taskExecutor = new TaskExecutorService(config.getMaxConcurrency());
        this.metricsReporter = new MetricsReporter(config.getWorkerId(), grpcClient, taskExecutor);
        this.shutdownLatch = new CountDownLatch(1);
        this.running = false;
    }
    
    /**
     * Start the worker and connect to controller.
     * This method blocks until the worker is shut down.
     *
     * @throws Exception if worker fails to start
     */
    public void start() throws Exception {
        log.info("Starting worker: id={}, controller={}, maxConcurrency={}", 
            config.getWorkerId(), 
            config.getControllerAddress(),
            config.getMaxConcurrency());
        
        try {
            // Connect to controller
            grpcClient.connect();
            log.info("Connected to controller: {}", config.getControllerAddress());
            
            // Register worker with controller
            grpcClient.registerWorker(
                config.getWorkerId(), 
                config.getCapabilities(),
                config.getMaxConcurrency()
            );
            log.info("Worker registered successfully");
            
            // Start task executor
            taskExecutor.start();
            log.info("Task executor started");
            
            // Start metrics reporting
            metricsReporter.start();
            log.info("Metrics reporter started");
            
            running = true;
            
            // Keep worker alive
            shutdownLatch.await();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Worker interrupted", e);
        } catch (Exception e) {
            log.error("Worker failed to start", e);
            throw e;
        } finally {
            shutdown();
        }
    }
    
    /**
     * Gracefully shutdown the worker.
     */
    public void shutdown() {
        if (!running) {
            return;
        }
        
        log.info("Shutting down worker: {}", config.getWorkerId());
        running = false;
        
        try {
            // Stop accepting new tasks
            taskExecutor.stopAcceptingTasks();
            
            // Stop metrics reporting
            metricsReporter.stop();
            
            // Wait for in-flight tasks to complete (with timeout)
            taskExecutor.awaitTermination(30);
            
            // Unregister from controller
            grpcClient.unregisterWorker(config.getWorkerId());
            
            // Disconnect from controller
            grpcClient.disconnect();
            
            log.info("Worker shut down successfully");
        } catch (Exception e) {
            log.error("Error during worker shutdown", e);
        } finally {
            shutdownLatch.countDown();
        }
    }
    
    /**
     * Check if worker is running.
     *
     * @return true if worker is running
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * Main entry point for worker application.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        try {
            // Parse configuration from command line
            WorkerConfig config = WorkerConfig.fromArgs(args);
            
            // Create and start worker
            Worker worker = new Worker(config);
            
            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutdown signal received");
                worker.shutdown();
            }, "shutdown-hook"));
            
            // Start worker (blocks until shutdown)
            worker.start();
            
        } catch (Exception e) {
            log.error("Worker failed", e);
            System.exit(1);
        }
    }
}
