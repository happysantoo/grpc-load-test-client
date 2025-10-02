package com.example.grpc.loadtest.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.HashMap;

/**
 * Configuration class for the gRPC load test client
 */
public class LoadTestConfig {
    
    @JsonProperty("target")
    private TargetConfig target = new TargetConfig();
    
    @JsonProperty("load")
    private LoadConfig load = new LoadConfig();
    
    @JsonProperty("client")
    private ClientConfig client = new ClientConfig();
    
    @JsonProperty("reporting")
    private ReportingConfig reporting = new ReportingConfig();
    
    @JsonProperty("payload")
    private PayloadConfig payload = new PayloadConfig();
    
    @JsonProperty("randomization")
    private RandomizationConfig randomization = new RandomizationConfig();
    
    // Getters and setters
    public TargetConfig getTarget() { return target; }
    public void setTarget(TargetConfig target) { this.target = target; }
    
    public LoadConfig getLoad() { return load; }
    public void setLoad(LoadConfig load) { this.load = load; }
    
    public ClientConfig getClient() { return client; }
    public void setClient(ClientConfig client) { this.client = client; }
    
    public ReportingConfig getReporting() { return reporting; }
    public void setReporting(ReportingConfig reporting) { this.reporting = reporting; }
    
    public PayloadConfig getPayload() { return payload; }
    public void setPayload(PayloadConfig payload) { this.payload = payload; }
    
    public RandomizationConfig getRandomization() { return randomization; }
    public void setRandomization(RandomizationConfig randomization) { this.randomization = randomization; }
    
    /**
     * Load configuration from YAML file
     */
    public static LoadTestConfig fromYaml(String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        return mapper.readValue(new File(filePath), LoadTestConfig.class);
    }
    
    /**
     * Create default configuration
     */
    public static LoadTestConfig createDefault() {
        LoadTestConfig config = new LoadTestConfig();
        config.target.host = "localhost";
        config.target.port = 8080;
        config.target.method = "Echo";
        config.target.useTls = false;
        
        config.load.tps = 100;
        config.load.duration = Duration.ofMinutes(1);
        config.load.warmupDuration = Duration.ofSeconds(10);
        
        config.client.maxConcurrentRequests = 1000;
        config.client.requestTimeoutMs = 5000;
        config.client.keepAliveTimeMs = 30000;
        config.client.keepAliveTimeoutMs = 5000;
        
        config.reporting.reportingIntervalSeconds = 10;
        config.reporting.enableRealTimeStats = true;
        
        return config;
    }
    
    /**
     * Target service configuration
     */
    public static class TargetConfig {
        @JsonProperty("host")
        private String host = "localhost";
        
        @JsonProperty("port")
        private int port = 8080;
        
        @JsonProperty("method")
        private String method = "Echo";
        
        @JsonProperty("use_tls")
        private boolean useTls = false;
        
        @JsonProperty("tls_cert_path")
        private String tlsCertPath;
        
        @JsonProperty("request_template")
        private Map<String, Object> requestTemplate = new HashMap<>();
        
        // Getters and setters
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        
        public boolean isUseTls() { return useTls; }
        public void setUseTls(boolean useTls) { this.useTls = useTls; }
        
        public String getTlsCertPath() { return tlsCertPath; }
        public void setTlsCertPath(String tlsCertPath) { this.tlsCertPath = tlsCertPath; }
        
        public Map<String, Object> getRequestTemplate() { return requestTemplate; }
        public void setRequestTemplate(Map<String, Object> requestTemplate) { this.requestTemplate = requestTemplate; }
        
        public String getAddress() {
            return host + ":" + port;
        }
    }
    
    /**
     * Load pattern configuration
     */
    public static class LoadConfig {
        @JsonProperty("tps")
        private int tps = 100;
        
        @JsonProperty("duration")
        private Duration duration = Duration.ofMinutes(1);
        
        @JsonProperty("warmup_duration")
        private Duration warmupDuration = Duration.ofSeconds(10);
        
        @JsonProperty("ramp_up_duration")
        private Duration rampUpDuration = Duration.ZERO;
        
        @JsonProperty("max_concurrent_requests")
        private int maxConcurrentRequests = 1000;
        
        // Getters and setters
        public int getTps() { return tps; }
        public void setTps(int tps) { this.tps = tps; }
        
        public Duration getDuration() { return duration; }
        public void setDuration(Duration duration) { this.duration = duration; }
        
        public Duration getWarmupDuration() { return warmupDuration; }
        public void setWarmupDuration(Duration warmupDuration) { this.warmupDuration = warmupDuration; }
        
        public Duration getRampUpDuration() { return rampUpDuration; }
        public void setRampUpDuration(Duration rampUpDuration) { this.rampUpDuration = rampUpDuration; }
        
        public int getMaxConcurrentRequests() { return maxConcurrentRequests; }
        public void setMaxConcurrentRequests(int maxConcurrentRequests) { this.maxConcurrentRequests = maxConcurrentRequests; }
    }
    
    /**
     * gRPC client configuration
     */
    public static class ClientConfig {
        @JsonProperty("max_concurrent_requests")
        private int maxConcurrentRequests = 1000;
        
        @JsonProperty("request_timeout_ms")
        private long requestTimeoutMs = 5000;
        
        @JsonProperty("keep_alive_time_ms")
        private long keepAliveTimeMs = 30000;
        
        @JsonProperty("keep_alive_timeout_ms")
        private long keepAliveTimeoutMs = 5000;
        
        @JsonProperty("keep_alive_without_calls")
        private boolean keepAliveWithoutCalls = true;
        
        @JsonProperty("max_inbound_message_size")
        private int maxInboundMessageSize = 4 * 1024 * 1024; // 4MB
        
        @JsonProperty("user_agent")
        private String userAgent = "grpc-load-test-client/1.0";
        
        // Getters and setters
        public int getMaxConcurrentRequests() { return maxConcurrentRequests; }
        public void setMaxConcurrentRequests(int maxConcurrentRequests) { this.maxConcurrentRequests = maxConcurrentRequests; }
        
        public long getRequestTimeoutMs() { return requestTimeoutMs; }
        public void setRequestTimeoutMs(long requestTimeoutMs) { this.requestTimeoutMs = requestTimeoutMs; }
        
        public long getKeepAliveTimeMs() { return keepAliveTimeMs; }
        public void setKeepAliveTimeMs(long keepAliveTimeMs) { this.keepAliveTimeMs = keepAliveTimeMs; }
        
        public long getKeepAliveTimeoutMs() { return keepAliveTimeoutMs; }
        public void setKeepAliveTimeoutMs(long keepAliveTimeoutMs) { this.keepAliveTimeoutMs = keepAliveTimeoutMs; }
        
        public boolean isKeepAliveWithoutCalls() { return keepAliveWithoutCalls; }
        public void setKeepAliveWithoutCalls(boolean keepAliveWithoutCalls) { this.keepAliveWithoutCalls = keepAliveWithoutCalls; }
        
        public int getMaxInboundMessageSize() { return maxInboundMessageSize; }
        public void setMaxInboundMessageSize(int maxInboundMessageSize) { this.maxInboundMessageSize = maxInboundMessageSize; }
        
        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    }
    
    /**
     * Reporting and statistics configuration
     */
    public static class ReportingConfig {
        @JsonProperty("reporting_interval_seconds")
        private int reportingIntervalSeconds = 10;
        
        @JsonProperty("enable_real_time_stats")
        private boolean enableRealTimeStats = true;
        
        @JsonProperty("percentiles")
        private double[] percentiles = {0.1, 0.25, 0.5, 0.75, 0.9, 0.95, 0.99};
        
        @JsonProperty("output_format")
        private String outputFormat = "console"; // console, json, csv
        
        @JsonProperty("output_file")
        private String outputFile;
        
        // Getters and setters
        public int getReportingIntervalSeconds() { return reportingIntervalSeconds; }
        public void setReportingIntervalSeconds(int reportingIntervalSeconds) { this.reportingIntervalSeconds = reportingIntervalSeconds; }
        
        public boolean isEnableRealTimeStats() { return enableRealTimeStats; }
        public void setEnableRealTimeStats(boolean enableRealTimeStats) { this.enableRealTimeStats = enableRealTimeStats; }
        
        public double[] getPercentiles() { return percentiles; }
        public void setPercentiles(double[] percentiles) { this.percentiles = percentiles; }
        
        public String getOutputFormat() { return outputFormat; }
        public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }
        
        public String getOutputFile() { return outputFile; }
        public void setOutputFile(String outputFile) { this.outputFile = outputFile; }
    }
    
    /**
     * Payload manipulation and transformation configuration
     */
    public static class PayloadConfig {
        @JsonProperty("enable_transformation")
        private boolean enableTransformation = false;
        
        @JsonProperty("base_payload")
        private Map<String, Object> basePayload = new HashMap<>();
        
        @JsonProperty("transformation_rules")
        private Map<String, TransformationRuleConfig> transformationRules = new HashMap<>();
        
        @JsonProperty("default_values")
        private Map<String, Object> defaultValues = new HashMap<>();
        
        // Getters and setters
        public boolean isEnableTransformation() { return enableTransformation; }
        public void setEnableTransformation(boolean enableTransformation) { this.enableTransformation = enableTransformation; }
        
        public Map<String, Object> getBasePayload() { return basePayload; }
        public void setBasePayload(Map<String, Object> basePayload) { this.basePayload = basePayload; }
        
        public Map<String, TransformationRuleConfig> getTransformationRules() { return transformationRules; }
        public void setTransformationRules(Map<String, TransformationRuleConfig> transformationRules) { this.transformationRules = transformationRules; }
        
        public Map<String, Object> getDefaultValues() { return defaultValues; }
        public void setDefaultValues(Map<String, Object> defaultValues) { this.defaultValues = defaultValues; }
        
        public static class TransformationRuleConfig {
            @JsonProperty("type")
            private String type;
            
            @JsonProperty("parameters")
            private Map<String, Object> parameters = new HashMap<>();
            
            // Getters and setters
            public String getType() { return type; }
            public void setType(String type) { this.type = type; }
            
            public Map<String, Object> getParameters() { return parameters; }
            public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
        }
    }
    
    /**
     * Randomization configuration for varied load testing
     */
    public static class RandomizationConfig {
        @JsonProperty("enable_method_randomization")
        private boolean enableMethodRandomization = false;
        
        @JsonProperty("available_methods")
        private java.util.List<String> availableMethods = java.util.List.of("Echo");
        
        @JsonProperty("method_weights")
        private Map<String, Double> methodWeights = new HashMap<>();
        
        @JsonProperty("enable_payload_randomization")
        private boolean enablePayloadRandomization = false;
        
        @JsonProperty("random_fields")
        private Map<String, RandomFieldConfig> randomFields = new HashMap<>();
        
        @JsonProperty("enable_timing_randomization")
        private boolean enableTimingRandomization = false;
        
        @JsonProperty("min_delay_ms")
        private long minDelayMs = 0;
        
        @JsonProperty("max_delay_ms")
        private long maxDelayMs = 100;
        
        // Getters and setters
        public boolean isEnableMethodRandomization() { return enableMethodRandomization; }
        public void setEnableMethodRandomization(boolean enableMethodRandomization) { this.enableMethodRandomization = enableMethodRandomization; }
        
        public java.util.List<String> getAvailableMethods() { return availableMethods; }
        public void setAvailableMethods(java.util.List<String> availableMethods) { this.availableMethods = availableMethods; }
        
        public Map<String, Double> getMethodWeights() { return methodWeights; }
        public void setMethodWeights(Map<String, Double> methodWeights) { this.methodWeights = methodWeights; }
        
        public boolean isEnablePayloadRandomization() { return enablePayloadRandomization; }
        public void setEnablePayloadRandomization(boolean enablePayloadRandomization) { this.enablePayloadRandomization = enablePayloadRandomization; }
        
        public Map<String, RandomFieldConfig> getRandomFields() { return randomFields; }
        public void setRandomFields(Map<String, RandomFieldConfig> randomFields) { this.randomFields = randomFields; }
        
        public boolean isEnableTimingRandomization() { return enableTimingRandomization; }
        public void setEnableTimingRandomization(boolean enableTimingRandomization) { this.enableTimingRandomization = enableTimingRandomization; }
        
        public long getMinDelayMs() { return minDelayMs; }
        public void setMinDelayMs(long minDelayMs) { this.minDelayMs = minDelayMs; }
        
        public long getMaxDelayMs() { return maxDelayMs; }
        public void setMaxDelayMs(long maxDelayMs) { this.maxDelayMs = maxDelayMs; }
        
        public static class RandomFieldConfig {
            @JsonProperty("type")
            private String type;
            
            @JsonProperty("min_value")
            private Object minValue;
            
            @JsonProperty("max_value")
            private Object maxValue;
            
            @JsonProperty("possible_values")
            private java.util.List<Object> possibleValues;
            
            @JsonProperty("pattern")
            private String pattern;
            
            // Getters and setters
            public String getType() { return type; }
            public void setType(String type) { this.type = type; }
            
            public Object getMinValue() { return minValue; }
            public void setMinValue(Object minValue) { this.minValue = minValue; }
            
            public Object getMaxValue() { return maxValue; }
            public void setMaxValue(Object maxValue) { this.maxValue = maxValue; }
            
            public java.util.List<Object> getPossibleValues() { return possibleValues; }
            public void setPossibleValues(java.util.List<Object> possibleValues) { this.possibleValues = possibleValues; }
            
            public String getPattern() { return pattern; }
            public void setPattern(String pattern) { this.pattern = pattern; }
        }
    }
}