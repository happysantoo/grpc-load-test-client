package com.vajraedge.perftest.metrics;

import java.util.HashMap;
import java.util.Map;

/**
 * Percentile statistics for latency measurements.
 */
public class PercentileStats {
    
    private final double[] percentiles;
    private final double[] values;
    private final Map<Double, Double> percentileMap;
    
    public PercentileStats(double[] percentiles, double[] values) {
        this.percentiles = percentiles.clone();
        this.values = values.clone();
        this.percentileMap = new HashMap<>();
        
        for (int i = 0; i < percentiles.length; i++) {
            percentileMap.put(percentiles[i], values[i]);
        }
    }
    
    public double getPercentile(double percentile) {
        return percentileMap.getOrDefault(percentile, 0.0);
    }
    
    public double[] getPercentiles() {
        return percentiles.clone();
    }
    
    public double[] getValues() {
        return values.clone();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Percentiles{");
        for (int i = 0; i < percentiles.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("P%.0f=%.2fms", percentiles[i] * 100, values[i]));
        }
        sb.append("}");
        return sb.toString();
    }
}
