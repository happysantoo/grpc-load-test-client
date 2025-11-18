package net.vajraedge.worker;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * gRPC server for the worker.
 * Listens for task assignments and control commands from the controller.
 */
public class WorkerGrpcServer {
    private static final Logger log = LoggerFactory.getLogger(WorkerGrpcServer.class);
    
    private final Server server;
    private final int port;
    
    public WorkerGrpcServer(int port, TaskAssignmentHandler assignmentHandler) {
        this.port = port;
        this.server = ServerBuilder.forPort(port)
                .addService(new WorkerServiceImpl(assignmentHandler))
                .build();
    }
    
    /**
     * Start the gRPC server.
     */
    public void start() throws IOException {
        server.start();
        log.info("Worker gRPC server started on port {}", port);
        log.info("Ready to accept task assignments from controller");
    }
    
    /**
     * Stop the gRPC server.
     */
    public void stop() {
        if (server != null) {
            try {
                log.info("Shutting down worker gRPC server");
                server.shutdown();
                if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Server did not terminate gracefully, forcing shutdown");
                    server.shutdownNow();
                }
                log.info("Worker gRPC server stopped");
            } catch (InterruptedException e) {
                log.error("Interrupted while shutting down server", e);
                server.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Block until the server shuts down.
     */
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
    
    /**
     * Get the port the server is listening on.
     */
    public int getPort() {
        return port;
    }
}
