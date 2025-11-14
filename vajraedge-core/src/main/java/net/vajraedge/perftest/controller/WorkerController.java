package net.vajraedge.perftest.controller;

import net.vajraedge.perftest.distributed.WorkerInfo;
import net.vajraedge.perftest.distributed.WorkerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for worker management.
 */
@RestController
@RequestMapping("/api/workers")
public class WorkerController {
    
    private static final Logger logger = LoggerFactory.getLogger(WorkerController.class);
    
    private final WorkerManager workerManager;
    
    public WorkerController(WorkerManager workerManager) {
        this.workerManager = workerManager;
    }
    
    /**
     * Get all registered workers.
     * 
     * GET /api/workers
     */
    @GetMapping
    public ResponseEntity<?> getWorkers() {
        logger.info("Fetching all registered workers");
        
        List<WorkerInfo> workers = workerManager.getAllWorkers();
        
        Map<String, Object> response = new HashMap<>();
        response.put("workers", workers);
        response.put("totalCount", workers.size());
        response.put("healthyCount", workerManager.getHealthyWorkers().size());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get worker details by ID.
     * 
     * GET /api/workers/{workerId}
     */
    @GetMapping("/{workerId}")
    public ResponseEntity<?> getWorker(@PathVariable String workerId) {
        logger.info("Fetching worker details: {}", workerId);
        
        WorkerInfo worker = workerManager.getWorkerById(workerId);
        
        if (worker == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Worker not found: " + workerId));
        }
        
        return ResponseEntity.ok(worker);
    }
    
    /**
     * Unregister a worker.
     * 
     * DELETE /api/workers/{workerId}
     */
    @DeleteMapping("/{workerId}")
    public ResponseEntity<?> unregisterWorker(@PathVariable String workerId) {
        logger.info("Unregistering worker: {}", workerId);
        
        boolean removed = workerManager.unregisterWorker(workerId);
        
        if (removed) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Worker unregistered successfully"
            ));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "success", false,
                            "message", "Worker not found: " + workerId
                    ));
        }
    }
    
    /**
     * Get worker pool statistics.
     * 
     * GET /api/workers/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getWorkerStats() {
        logger.info("Fetching worker pool statistics");
        
        WorkerManager.WorkerPoolStats stats = workerManager.getPoolStats();
        
        return ResponseEntity.ok(stats);
    }
}
