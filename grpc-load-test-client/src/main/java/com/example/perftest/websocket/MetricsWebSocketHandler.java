package com.example.perftest.websocket;

import com.example.perftest.dto.MetricsResponse;
import com.example.perftest.service.MetricsService;
import com.example.perftest.service.TestExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * WebSocket handler for broadcasting real-time metrics updates.
 */
@Component
public class MetricsWebSocketHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsWebSocketHandler.class);
    
    private final SimpMessagingTemplate messagingTemplate;
    private final TestExecutionService testExecutionService;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;
    
    public MetricsWebSocketHandler(SimpMessagingTemplate messagingTemplate,
                                   TestExecutionService testExecutionService,
                                   MetricsService metricsService,
                                   ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.testExecutionService = testExecutionService;
        this.metricsService = metricsService;
        this.objectMapper = objectMapper;
        logger.info("MetricsWebSocketHandler initialized");
    }
    
    /**
     * Broadcast metrics for all active tests every 500ms.
     */
    @Scheduled(fixedRate = 500)
    public void broadcastMetrics() {
        testExecutionService.getActiveTests().forEach((testId, execution) -> {
            try {
                MetricsResponse metrics = metricsService.convertToResponse(
                    testId,
                    execution.getRunner().getMetricsCollector().getSnapshot()
                );
                
                // Also include execution status
                metrics.setActiveTasks(execution.getRunner().getExecutor().getActiveTasks());
                
                // Broadcast to topic for this specific test
                messagingTemplate.convertAndSend("/topic/metrics/" + testId, metrics);
                
                logger.debug("Broadcasted metrics for test {}: TPS={}, Active={}",
                    testId, metrics.getCurrentTps(), metrics.getActiveTasks());
                
            } catch (Exception e) {
                logger.error("Error broadcasting metrics for test {}", testId, e);
            }
        });
    }
    
    /**
     * Send a test status update (start, stop, complete).
     */
    public void sendTestStatusUpdate(String testId, String status, Object data) {
        try {
            var message = new TestStatusUpdate(testId, status, System.currentTimeMillis(), data);
            messagingTemplate.convertAndSend("/topic/status/" + testId, message);
            logger.info("Sent status update for test {}: {}", testId, status);
        } catch (Exception e) {
            logger.error("Error sending status update for test {}", testId, e);
        }
    }
    
    /**
     * Message class for test status updates.
     */
    public static class TestStatusUpdate {
        private String testId;
        private String status;
        private long timestamp;
        private Object data;
        
        public TestStatusUpdate(String testId, String status, long timestamp, Object data) {
            this.testId = testId;
            this.status = status;
            this.timestamp = timestamp;
            this.data = data;
        }
        
        // Getters and setters
        public String getTestId() { return testId; }
        public void setTestId(String testId) { this.testId = testId; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        
        public Object getData() { return data; }
        public void setData(Object data) { this.data = data; }
    }
}
