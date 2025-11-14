package net.vajraedge.perftest.config;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import net.vajraedge.perftest.distributed.WorkerServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Configuration for gRPC server for distributed testing.
 */
@Configuration
public class GrpcServerConfig {
    private static final Logger log = LoggerFactory.getLogger(GrpcServerConfig.class);
    
    @Value("${vajraedge.grpc.port:9090}")
    private int grpcPort;
    
    @Value("${vajraedge.grpc.enabled:false}")
    private boolean grpcEnabled;
    
    @Bean
    public Server grpcServer(WorkerServiceImpl workerService) throws IOException {
        if (!grpcEnabled) {
            log.info("gRPC server disabled. Set vajraedge.grpc.enabled=true to enable distributed testing.");
            return null;
        }
        
        Server server = ServerBuilder.forPort(grpcPort)
                .addService(workerService)
                .build();
        
        server.start();
        
        log.info("gRPC server started on port {}", grpcPort);
        log.info("Workers can connect to: localhost:{}", grpcPort);
        
        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down gRPC server...");
            server.shutdown();
            try {
                if (!server.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    log.warn("gRPC server did not terminate gracefully");
                    server.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("Interrupted during gRPC server shutdown", e);
                server.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("gRPC server shut down");
        }));
        
        return server;
    }
}
