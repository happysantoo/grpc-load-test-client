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
    
    // Getters and setters
    public TargetConfig getTarget() { return target; }
    public void setTarget(TargetConfig target) { this.target = target; }
    
    public LoadConfig getLoad() { return load; }
    public void setLoad(LoadConfig load) { this.load = load; }
    
    public ClientConfig getClient() { return client; }
    public void setClient(ClientConfig client) { this.client = client; }
    
    public ReportingConfig getReporting() { return reporting; }
    public void setReporting(ReportingConfig reporting) { this.reporting = reporting; }
    
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
}