package com.example.grpc.loadtest.randomization;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages randomization patterns for load testing scenarios.
 * Provides various strategies for generating randomized request patterns.
 */
public class RandomizationManager {
    
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    
    /**
     * Configuration for randomization behavior
     */
    public static class RandomizationConfig {
        private final boolean enableMethodRandomization;
        private final List<String> availableMethods;
        private final Map<String, Double> methodWeights;
        private final boolean enablePayloadRandomization;
        private final Map<String, RandomFieldConfig> fieldConfigs;
        private final boolean enableTimingRandomization;
        private final long minDelayMs;
        private final long maxDelayMs;
        
        private RandomizationConfig(Builder builder) {
            this.enableMethodRandomization = builder.enableMethodRandomization;
            this.availableMethods = builder.availableMethods;
            this.methodWeights = builder.methodWeights;
            this.enablePayloadRandomization = builder.enablePayloadRandomization;
            this.fieldConfigs = builder.fieldConfigs;
            this.enableTimingRandomization = builder.enableTimingRandomization;
            this.minDelayMs = builder.minDelayMs;
            this.maxDelayMs = builder.maxDelayMs;
        }
        
        // Getters
        public boolean isMethodRandomizationEnabled() { return enableMethodRandomization; }
        public List<String> getAvailableMethods() { return availableMethods; }
        public Map<String, Double> getMethodWeights() { return methodWeights; }
        public boolean isPayloadRandomizationEnabled() { return enablePayloadRandomization; }
        public Map<String, RandomFieldConfig> getFieldConfigs() { return fieldConfigs; }
        public boolean isTimingRandomizationEnabled() { return enableTimingRandomization; }
        public long getMinDelayMs() { return minDelayMs; }
        public long getMaxDelayMs() { return maxDelayMs; }
        
        public static class Builder {
            private boolean enableMethodRandomization = false;
            private List<String> availableMethods = List.of("Echo");
            private Map<String, Double> methodWeights = Map.of();
            private boolean enablePayloadRandomization = false;
            private Map<String, RandomFieldConfig> fieldConfigs = Map.of();
            private boolean enableTimingRandomization = false;
            private long minDelayMs = 0;
            private long maxDelayMs = 100;
            
            public Builder enableMethodRandomization(List<String> methods, Map<String, Double> weights) {
                this.enableMethodRandomization = true;
                this.availableMethods = methods;
                this.methodWeights = weights;
                return this;
            }
            
            public Builder enablePayloadRandomization(Map<String, RandomFieldConfig> configs) {
                this.enablePayloadRandomization = true;
                this.fieldConfigs = configs;
                return this;
            }
            
            public Builder enableTimingRandomization(long minDelayMs, long maxDelayMs) {
                this.enableTimingRandomization = true;
                this.minDelayMs = minDelayMs;
                this.maxDelayMs = maxDelayMs;
                return this;
            }
            
            public RandomizationConfig build() {
                return new RandomizationConfig(this);
            }
        }
    }
    
    /**
     * Configuration for randomizing specific fields
     */
    public static class RandomFieldConfig {
        private final FieldType type;
        private final Object minValue;
        private final Object maxValue;
        private final List<Object> possibleValues;
        private final String pattern;
        
        public RandomFieldConfig(FieldType type, Object minValue, Object maxValue, 
                               List<Object> possibleValues, String pattern) {
            this.type = type;
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.possibleValues = possibleValues;
            this.pattern = pattern;
        }
        
        // Getters
        public FieldType getType() { return type; }
        public Object getMinValue() { return minValue; }
        public Object getMaxValue() { return maxValue; }
        public List<Object> getPossibleValues() { return possibleValues; }
        public String getPattern() { return pattern; }
        
        // Static factory methods
        public static RandomFieldConfig randomString(int minLength, int maxLength) {
            return new RandomFieldConfig(FieldType.STRING, minLength, maxLength, null, null);
        }
        
        public static RandomFieldConfig randomNumber(Number min, Number max) {
            return new RandomFieldConfig(FieldType.NUMBER, min, max, null, null);
        }
        
        public static RandomFieldConfig fromList(List<Object> values) {
            return new RandomFieldConfig(FieldType.LIST, null, null, values, null);
        }
        
        public static RandomFieldConfig pattern(String pattern) {
            return new RandomFieldConfig(FieldType.PATTERN, null, null, null, pattern);
        }
    }
    
    public enum FieldType {
        STRING, NUMBER, LIST, PATTERN
    }
    
    private final RandomizationConfig config;
    
    public RandomizationManager(RandomizationConfig config) {
        this.config = config;
    }
    
    /**
     * Get a random method name based on configuration weights
     */
    public String getRandomMethod() {
        if (!config.isMethodRandomizationEnabled() || config.getAvailableMethods().isEmpty()) {
            return "Echo";
        }
        
        if (config.getMethodWeights().isEmpty()) {
            // Equal probability for all methods
            List<String> methods = config.getAvailableMethods();
            return methods.get(random.nextInt(methods.size()));
        }
        
        // Weighted selection
        double totalWeight = config.getMethodWeights().values().stream().mapToDouble(Double::doubleValue).sum();
        double randomValue = random.nextDouble() * totalWeight;
        
        double cumulativeWeight = 0.0;
        for (Map.Entry<String, Double> entry : config.getMethodWeights().entrySet()) {
            cumulativeWeight += entry.getValue();
            if (randomValue <= cumulativeWeight) {
                return entry.getKey();
            }
        }
        
        // Fallback
        return config.getAvailableMethods().get(0);
    }
    
    /**
     * Generate random field values based on configuration
     */
    public Map<String, Object> generateRandomFields() {
        if (!config.isPayloadRandomizationEnabled()) {
            return Map.of();
        }
        
        Map<String, Object> randomFields = new java.util.HashMap<>();
        
        for (Map.Entry<String, RandomFieldConfig> entry : config.getFieldConfigs().entrySet()) {
            String fieldName = entry.getKey();
            RandomFieldConfig fieldConfig = entry.getValue();
            
            Object randomValue = generateRandomValue(fieldConfig);
            randomFields.put(fieldName, randomValue);
        }
        
        return randomFields;
    }
    
    /**
     * Get random delay between requests
     */
    public long getRandomDelay() {
        if (!config.isTimingRandomizationEnabled()) {
            return 0;
        }
        
        if (config.getMinDelayMs() == config.getMaxDelayMs()) {
            return config.getMinDelayMs();
        }
        
        return random.nextLong(config.getMinDelayMs(), config.getMaxDelayMs() + 1);
    }
    
    private Object generateRandomValue(RandomFieldConfig fieldConfig) {
        switch (fieldConfig.getType()) {
            case STRING:
                int minLen = (Integer) fieldConfig.getMinValue();
                int maxLen = (Integer) fieldConfig.getMaxValue();
                int length = random.nextInt(minLen, maxLen + 1);
                return generateRandomString(length);
                
            case NUMBER:
                Number min = (Number) fieldConfig.getMinValue();
                Number max = (Number) fieldConfig.getMaxValue();
                if (min instanceof Integer) {
                    return random.nextInt(min.intValue(), max.intValue() + 1);
                } else if (min instanceof Long) {
                    return random.nextLong(min.longValue(), max.longValue() + 1);
                } else {
                    return random.nextDouble(min.doubleValue(), max.doubleValue());
                }
                
            case LIST:
                List<Object> values = fieldConfig.getPossibleValues();
                return values.get(random.nextInt(values.size()));
                
            case PATTERN:
                return generateFromPattern(fieldConfig.getPattern());
                
            default:
                return null;
        }
    }
    
    private String generateRandomString(int length) {
        String charset = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(charset.charAt(random.nextInt(charset.length())));
        }
        return sb.toString();
    }
    
    private String generateFromPattern(String pattern) {
        // Simple pattern replacement: {d} for digit, {l} for letter, {c} for alphanumeric
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '{' && i + 2 < pattern.length() && pattern.charAt(i + 2) == '}') {
                char type = pattern.charAt(i + 1);
                switch (type) {
                    case 'd':
                        result.append(random.nextInt(10));
                        break;
                    case 'l':
                        result.append((char) ('a' + random.nextInt(26)));
                        break;
                    case 'L':
                        result.append((char) ('A' + random.nextInt(26)));
                        break;
                    case 'c':
                        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
                        result.append(chars.charAt(random.nextInt(chars.length())));
                        break;
                    default:
                        result.append(c);
                        continue;
                }
                i += 2; // Skip the pattern
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}