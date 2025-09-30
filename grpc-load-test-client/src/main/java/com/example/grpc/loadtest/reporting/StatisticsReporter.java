package com.example.grpc.loadtest.reporting;

import com.example.grpc.loadtest.config.LoadTestConfig;
import com.example.grpc.loadtest.controller.ThroughputController;
import com.example.grpc.loadtest.executor.VirtualThreadExecutor;
import com.example.grpc.loadtest.metrics.MetricsCollector;
import com.example.grpc.loadtest.metrics.MetricsSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Real-time and final statistics reporter for load test results
 */
public class StatisticsReporter implements AutoCloseable {
    
    private static final Logger logger = LoggerFactory.getLogger(StatisticsReporter.class);
    
    private final LoadTestConfig config;
    private final MetricsCollector metricsCollector;
    private final ThroughputController throughputController;
    private final VirtualThreadExecutor executor;
    
    private final ScheduledExecutorService reportingExecutor;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final ObjectMapper jsonMapper;
    private final PrintWriter csvWriter;
    
    public StatisticsReporter(LoadTestConfig config, MetricsCollector metricsCollector,
                             ThroughputController throughputController, VirtualThreadExecutor executor) {
        this.config = config;
        this.metricsCollector = metricsCollector;
        this.throughputController = throughputController;
        this.executor = executor;
        
        this.reportingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "statistics-reporter");
            t.setDaemon(true);
            return t;
        });
        
        this.jsonMapper = new ObjectMapper();
        this.jsonMapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        this.csvWriter = initializeCsvWriter();
        
        logger.info("Created StatisticsReporter with interval={}s, format={}", 
                   config.getReporting().getReportingIntervalSeconds(),
                   config.getReporting().getOutputFormat());
    }
    
    private PrintWriter initializeCsvWriter() {
        if (!"csv".equals(config.getReporting().getOutputFormat()) || 
            config.getReporting().getOutputFile() == null) {
            return null;
        }
        
        try {
            PrintWriter writer = new PrintWriter(new FileWriter(config.getReporting().getOutputFile()));
            // Write CSV header
            writer.println("timestamp,elapsed_seconds,total_requests,successful_requests,failed_requests," +
                          "tps,success_rate,avg_latency_ms,p10_ms,p25_ms,p50_ms,p75_ms,p90_ms,p95_ms,p99_ms," +
                          "active_threads,thread_utilization,response_codes,errors");
            writer.flush();
            return writer;
        } catch (IOException e) {
            logger.error("Failed to create CSV writer", e);
            return null;
        }
    }
    
    /**
     * Start real-time reporting
     */
    public void startRealTimeReporting() {
        if (isRunning.compareAndSet(false, true)) {
            int intervalSeconds = config.getReporting().getReportingIntervalSeconds();
            
            if (config.getReporting().isEnableRealTimeStats() && intervalSeconds > 0) {
                reportingExecutor.scheduleAtFixedRate(
                    this::generateRealTimeReport,
                    intervalSeconds,
                    intervalSeconds,
                    TimeUnit.SECONDS
                );
                logger.info("Started real-time reporting with {}s interval", intervalSeconds);
            }
        }
    }
    
    /**
     * Stop real-time reporting
     */
    public void stopRealTimeReporting() {
        if (isRunning.compareAndSet(true, false)) {
            reportingExecutor.shutdown();
            logger.info("Stopped real-time reporting");
        }
    }
    
    /**
     * Generate real-time report
     */
    private void generateRealTimeReport() {
        try {
            MetricsSnapshot snapshot = metricsCollector.getSnapshot();
            ThroughputController.ThroughputStats throughputStats = throughputController.getStats();
            VirtualThreadExecutor.ExecutorStats executorStats = executor.getStats();
            
            switch (config.getReporting().getOutputFormat().toLowerCase()) {
                case "json":
                    generateJsonReport(snapshot, throughputStats, executorStats, false);
                    break;
                case "csv":
                    generateCsvReport(snapshot, throughputStats, executorStats);
                    break;
                default:
                    generateConsoleReport(snapshot, throughputStats, executorStats, false);
            }
        } catch (Exception e) {
            logger.error("Error generating real-time report", e);
        }
    }
    
    /**
     * Generate final summary report
     */
    public void generateFinalReport() {
        logger.info("Generating final load test report...");
        
        try {
            MetricsSnapshot snapshot = metricsCollector.getSnapshot();
            ThroughputController.ThroughputStats throughputStats = throughputController.getStats();
            VirtualThreadExecutor.ExecutorStats executorStats = executor.getStats();
            
            switch (config.getReporting().getOutputFormat().toLowerCase()) {
                case "json":
                    generateJsonReport(snapshot, throughputStats, executorStats, true);
                    break;
                case "csv":
                    generateCsvReport(snapshot, throughputStats, executorStats);
                    break;
                default:
                    generateConsoleReport(snapshot, throughputStats, executorStats, true);
            }
            
            // Always generate a console summary regardless of output format
            if (!"console".equals(config.getReporting().getOutputFormat())) {
                generateConsoleSummary(snapshot);
            }
            
        } catch (Exception e) {
            logger.error("Error generating final report", e);
        }
    }
    
    private void generateConsoleReport(MetricsSnapshot snapshot, 
                                     ThroughputController.ThroughputStats throughputStats,
                                     VirtualThreadExecutor.ExecutorStats executorStats,
                                     boolean isFinal) {
        String reportType = isFinal ? "FINAL REPORT" : "REAL-TIME STATS";
        String timestamp = DateTimeFormatter.ISO_LOCAL_TIME.format(Instant.now().atZone(java.time.ZoneId.systemDefault()));
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println(String.format(" %s - %s", reportType, timestamp));
        System.out.println("=".repeat(80));
        
        // Basic statistics
        System.out.printf("Duration: %s | Total Requests: %,d | TPS: %.1f%n",
                formatDuration(snapshot.getElapsed()), snapshot.getTotalRequests(), snapshot.getTps());
        
        System.out.printf("Success Rate: %.2f%% (%,d/%,d) | Avg Response Size: %,d bytes%n",
                snapshot.getSuccessRate(), snapshot.getSuccessfulRequests(), 
                snapshot.getTotalRequests(), snapshot.getAvgResponseSize());
        
        // Latency statistics
        System.out.println("\nLatency Statistics:");
        System.out.printf("  Average: %.2f ms%n", snapshot.getAvgLatencyMs());
        
        MetricsCollector.PercentileStats percentiles = snapshot.getPercentiles();
        System.out.printf("  P10: %.2f ms | P25: %.2f ms | P50: %.2f ms | P75: %.2f ms%n",
                percentiles.getPercentile(0.1), percentiles.getPercentile(0.25),
                percentiles.getPercentile(0.5), percentiles.getPercentile(0.75));
        
        System.out.printf("  P90: %.2f ms | P95: %.2f ms | P99: %.2f ms%n",
                percentiles.getPercentile(0.9), percentiles.getPercentile(0.95),
                percentiles.getPercentile(0.99));
        
        // Response codes
        if (!snapshot.getResponseCodeCounts().isEmpty()) {
            System.out.println("\nResponse Codes:");
            snapshot.getResponseCodeCounts().entrySet().stream()
                    .sorted(Map.Entry.<Integer, Long>comparingByKey())
                    .forEach(entry -> {
                        String codeName = entry.getKey() == 0 ? "SUCCESS" : "ERROR_" + entry.getKey();
                        double percentage = (entry.getValue() * 100.0) / snapshot.getTotalRequests();
                        System.out.printf("  %s: %,d (%.1f%%)%n", codeName, entry.getValue(), percentage);
                    });
        }
        
        // Throughput controller stats
        if (throughputStats.isInRampUp()) {
            System.out.printf("\nThroughput: Target %d TPS | Current %d TPS | Ramp-up: %.1f%%\n",
                    throughputStats.getTargetTps(), throughputStats.getCurrentTps(),
                    throughputStats.getRampUpProgress());
        } else {
            System.out.printf("\nThroughput: Target %d TPS | Actual %.1f TPS\n",
                    throughputStats.getTargetTps(), throughputStats.getActualTps());
        }
        
        // Executor stats
        System.out.printf("Virtual Threads: Active %d/%d (%.1f%% utilization) | Submitted: %,d | Completed: %,d%n",
                executorStats.getActiveRequests(), executorStats.getMaxConcurrentRequests(),
                executorStats.getUtilizationPercent(), executorStats.getSubmittedTasks(),
                executorStats.getCompletedTasks());
        
        // Error summary (top 5)
        if (!snapshot.getErrorCounts().isEmpty()) {
            System.out.println("\nTop Errors:");
            snapshot.getErrorCounts().entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(5)
                    .forEach(entry -> System.out.printf("  %s: %,d%n", entry.getKey(), entry.getValue()));
        }
        
        System.out.println("=".repeat(80));
    }
    
    private void generateConsoleSummary(MetricsSnapshot snapshot) {
        System.out.println("\n" + "=".repeat(50));
        System.out.println(" LOAD TEST SUMMARY");
        System.out.println("=".repeat(50));
        System.out.printf("Total Requests: %,d | Success Rate: %.2f%% | Avg TPS: %.1f%n",
                snapshot.getTotalRequests(), snapshot.getSuccessRate(), snapshot.getTps());
        System.out.printf("Avg Latency: %.2f ms | P95: %.2f ms | P99: %.2f ms%n",
                snapshot.getAvgLatencyMs(), 
                snapshot.getPercentiles().getPercentile(0.95),
                snapshot.getPercentiles().getPercentile(0.99));
        System.out.println("=".repeat(50));
    }
    
    private void generateJsonReport(MetricsSnapshot snapshot,
                                  ThroughputController.ThroughputStats throughputStats,
                                  VirtualThreadExecutor.ExecutorStats executorStats,
                                  boolean isFinal) {
        try {
            ReportData report = new ReportData(snapshot, throughputStats, executorStats, isFinal);
            String json = jsonMapper.writeValueAsString(report);
            
            if (config.getReporting().getOutputFile() != null) {
                try (FileWriter writer = new FileWriter(config.getReporting().getOutputFile(), true)) {
                    writer.write(json + "\n");
                }
            } else {
                System.out.println(json);
            }
        } catch (IOException e) {
            logger.error("Error generating JSON report", e);
        }
    }
    
    private void generateCsvReport(MetricsSnapshot snapshot,
                                 ThroughputController.ThroughputStats throughputStats,
                                 VirtualThreadExecutor.ExecutorStats executorStats) {
        if (csvWriter == null) return;
        
        try {
            csvWriter.printf("%s,%d,%d,%d,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%d,%.1f,\"%s\",\"%s\"%n",
                    DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                    snapshot.getElapsed().getSeconds(),
                    snapshot.getTotalRequests(),
                    snapshot.getSuccessfulRequests(),
                    snapshot.getFailedRequests(),
                    snapshot.getTps(),
                    snapshot.getSuccessRate(),
                    snapshot.getAvgLatencyMs(),
                    snapshot.getPercentiles().getPercentile(0.1),
                    snapshot.getPercentiles().getPercentile(0.25),
                    snapshot.getPercentiles().getPercentile(0.5),
                    snapshot.getPercentiles().getPercentile(0.75),
                    snapshot.getPercentiles().getPercentile(0.9),
                    snapshot.getPercentiles().getPercentile(0.95),
                    snapshot.getPercentiles().getPercentile(0.99),
                    executorStats.getActiveRequests(),
                    executorStats.getUtilizationPercent(),
                    formatResponseCodes(snapshot.getResponseCodeCounts()),
                    formatErrors(snapshot.getErrorCounts())
            );
            csvWriter.flush();
        } catch (Exception e) {
            logger.error("Error writing CSV report", e);
        }
    }
    
    private String formatDuration(java.time.Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
    
    private String formatResponseCodes(Map<Integer, Long> codes) {
        StringBuilder sb = new StringBuilder();
        codes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    if (sb.length() > 0) sb.append(";");
                    sb.append(entry.getKey()).append(":").append(entry.getValue());
                });
        return sb.toString();
    }
    
    private String formatErrors(Map<String, Long> errors) {
        StringBuilder sb = new StringBuilder();
        errors.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .forEach(entry -> {
                    if (sb.length() > 0) sb.append(";");
                    sb.append(entry.getKey()).append(":").append(entry.getValue());
                });
        return sb.toString();
    }
    
    @Override
    public void close() {
        stopRealTimeReporting();
        
        if (csvWriter != null) {
            csvWriter.close();
        }
        
        logger.info("StatisticsReporter closed");
    }
    
    /**
     * Report data structure for JSON serialization
     */
    public static class ReportData {
        public final String timestamp;
        public final boolean isFinalReport;
        public final MetricsSnapshot metrics;
        public final ThroughputController.ThroughputStats throughput;
        public final VirtualThreadExecutor.ExecutorStats executor;
        
        public ReportData(MetricsSnapshot metrics,
                         ThroughputController.ThroughputStats throughput,
                         VirtualThreadExecutor.ExecutorStats executor,
                         boolean isFinalReport) {
            this.timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            this.isFinalReport = isFinalReport;
            this.metrics = metrics;
            this.throughput = throughput;
            this.executor = executor;
        }
    }
}