package com.vajraedge.perftest.service;

import com.vajraedge.perftest.dto.MetricsResponse;
import com.vajraedge.perftest.metrics.MetricsSnapshot;
import com.vajraedge.perftest.metrics.PercentileStats;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for managing and aggregating metrics.
 */
@Service
public class MetricsService {
    
    /**
     * Convert MetricsSnapshot to MetricsResponse DTO.
     */
    public MetricsResponse convertToResponse(String testId, MetricsSnapshot snapshot) {
        MetricsResponse response = new MetricsResponse();
        response.setTestId(testId);
        response.setTimestamp(System.currentTimeMillis());
        response.setTotalRequests(snapshot.getTotalTasks());
        response.setSuccessfulRequests(snapshot.getSuccessfulTasks());
        response.setFailedRequests(snapshot.getFailedTasks());
        
        if (snapshot.getTotalTasks() > 0) {
            response.setSuccessRate(
                (double) snapshot.getSuccessfulTasks() / snapshot.getTotalTasks() * 100.0
            );
        } else {
            response.setSuccessRate(0.0);
        }
        
        response.setCurrentTps(snapshot.getTps());
        response.setAvgLatencyMs(snapshot.getAvgLatencyMs());
        
        PercentileStats percentiles = snapshot.getPercentiles();
        if (percentiles != null) {
            Map<String, Double> percentileMap = new HashMap<>();
            percentileMap.put("p50", percentiles.getPercentile(0.5));
            percentileMap.put("p75", percentiles.getPercentile(0.75));
            percentileMap.put("p90", percentiles.getPercentile(0.9));
            percentileMap.put("p95", percentiles.getPercentile(0.95));
            percentileMap.put("p99", percentiles.getPercentile(0.99));
            percentileMap.put("p99.9", percentiles.getPercentile(0.999));
            response.setLatencyPercentiles(percentileMap);
            
            // Get min/max from the values array
            double[] values = percentiles.getValues();
            if (values.length > 0) {
                double min = values[0];
                double max = values[0];
                for (double v : values) {
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                }
                response.setMinLatencyMs(min);
                response.setMaxLatencyMs(max);
            }
        }
        
        return response;
    }
}
