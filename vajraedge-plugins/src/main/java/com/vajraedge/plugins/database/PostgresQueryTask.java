package com.vajraedge.plugins.database;

import com.vajraedge.sdk.SimpleTaskResult;
import com.vajraedge.sdk.TaskResult;
import com.vajraedge.sdk.TaskMetadata;
import com.vajraedge.sdk.TaskMetadata.ParameterDef;
import com.vajraedge.sdk.TaskPlugin;
import com.vajraedge.sdk.annotations.VajraTask;

import java.util.List;
import java.util.Map;

/**
 * Example PostgreSQL query task plugin for load testing database operations.
 * This is a reference implementation showing how to create a database plugin.
 * 
 * <p>To use this plugin, you would:
 * <ol>
 *   <li>Add JDBC driver dependency (e.g., org.postgresql:postgresql)</li>
 *   <li>Create connection pool in initialize() (e.g., HikariCP)</li>
 *   <li>Execute queries in execute() using pooled connections</li>
 *   <li>Handle prepared statements for parameterized queries</li>
 *   <li>Close connection pool in cleanup method</li>
 * </ol>
 * 
 * @since 1.1.0
 */
@VajraTask(
    name = "POSTGRES_QUERY",
    displayName = "PostgreSQL Query",
    description = "Executes SQL queries against PostgreSQL database (example implementation)",
    category = "DATABASE",
    version = "1.0.0",
    author = "VajraEdge"
)
public class PostgresQueryTask implements TaskPlugin {
    
    private String jdbcUrl;
    private String username;
    private String password;
    private String query;
    private int queryTimeoutSeconds;
    private int poolSize;
    
    // In real implementation:
    // private HikariDataSource dataSource;
    
    public PostgresQueryTask() {
        this.jdbcUrl = "jdbc:postgresql://localhost:5432/testdb";
        this.username = "postgres";
        this.password = "postgres";
        this.query = "SELECT 1";
        this.queryTimeoutSeconds = 5;
        this.poolSize = 10;
    }
    
    @Override
    public TaskMetadata getMetadata() {
        return TaskMetadata.builder()
            .name("POSTGRES_QUERY")
            .displayName("PostgreSQL Query")
            .description("Executes SQL queries against PostgreSQL database")
            .category("DATABASE")
            .parameters(List.of(
                ParameterDef.requiredString(
                    "jdbcUrl",
                    "JDBC connection URL (e.g., jdbc:postgresql://host:5432/dbname)"
                ),
                ParameterDef.requiredString(
                    "username",
                    "Database username"
                ),
                ParameterDef.requiredString(
                    "password",
                    "Database password"
                ),
                ParameterDef.requiredString(
                    "query",
                    "SQL query to execute"
                ),
                ParameterDef.optionalInteger(
                    "queryTimeout",
                    5,
                    1,
                    300,
                    "Query timeout in seconds (1-300)"
                ),
                ParameterDef.optionalInteger(
                    "poolSize",
                    10,
                    1,
                    100,
                    "Connection pool size (1-100)"
                )
            ))
            .metadata(Map.of(
                "database", "PostgreSQL",
                "blocking", "true",
                "example", "true",
                "security", "Credentials should be stored securely"
            ))
            .build();
    }
    
    @Override
    public void validateParameters(Map<String, Object> parameters) {
        if (!parameters.containsKey("jdbcUrl") || parameters.get("jdbcUrl").toString().isBlank()) {
            throw new IllegalArgumentException("jdbcUrl parameter is required");
        }
        
        if (!parameters.containsKey("username") || parameters.get("username").toString().isBlank()) {
            throw new IllegalArgumentException("username parameter is required");
        }
        
        if (!parameters.containsKey("password")) {
            throw new IllegalArgumentException("password parameter is required");
        }
        
        if (!parameters.containsKey("query") || parameters.get("query").toString().isBlank()) {
            throw new IllegalArgumentException("query parameter is required");
        }
        
        if (parameters.containsKey("queryTimeout")) {
            Object timeoutObj = parameters.get("queryTimeout");
            int timeout = timeoutObj instanceof Integer ? (Integer) timeoutObj : 
                         Integer.parseInt(timeoutObj.toString());
            
            if (timeout < 1 || timeout > 300) {
                throw new IllegalArgumentException("queryTimeout must be between 1 and 300 seconds");
            }
        }
        
        if (parameters.containsKey("poolSize")) {
            Object poolSizeObj = parameters.get("poolSize");
            int size = poolSizeObj instanceof Integer ? (Integer) poolSizeObj : 
                      Integer.parseInt(poolSizeObj.toString());
            
            if (size < 1 || size > 100) {
                throw new IllegalArgumentException("poolSize must be between 1 and 100");
            }
        }
    }
    
    @Override
    public void initialize(Map<String, Object> parameters) {
        this.jdbcUrl = parameters.get("jdbcUrl").toString();
        this.username = parameters.get("username").toString();
        this.password = parameters.get("password").toString();
        this.query = parameters.get("query").toString();
        
        if (parameters.containsKey("queryTimeout")) {
            Object timeoutObj = parameters.get("queryTimeout");
            this.queryTimeoutSeconds = timeoutObj instanceof Integer ? (Integer) timeoutObj : 
                                      Integer.parseInt(timeoutObj.toString());
        }
        
        if (parameters.containsKey("poolSize")) {
            Object poolSizeObj = parameters.get("poolSize");
            this.poolSize = poolSizeObj instanceof Integer ? (Integer) poolSizeObj : 
                           Integer.parseInt(poolSizeObj.toString());
        }
        
        // In real implementation, initialize connection pool:
        // HikariConfig config = new HikariConfig();
        // config.setJdbcUrl(jdbcUrl);
        // config.setUsername(username);
        // config.setPassword(password);
        // config.setMaximumPoolSize(poolSize);
        // config.setConnectionTimeout(queryTimeoutSeconds * 1000);
        // this.dataSource = new HikariDataSource(config);
    }
    
    @Override
    public TaskResult execute() throws Exception {
        long taskId = Thread.currentThread().threadId();
        long startTime = System.nanoTime();
        
        try {
            // In real implementation, execute query:
            // try (Connection conn = dataSource.getConnection();
            //      PreparedStatement stmt = conn.prepareStatement(query)) {
            //     
            //     stmt.setQueryTimeout(queryTimeoutSeconds);
            //     
            //     if (query.trim().toUpperCase().startsWith("SELECT")) {
            //         try (ResultSet rs = stmt.executeQuery()) {
            //             int rowCount = 0;
            //             while (rs.next()) {
            //                 rowCount++;
            //             }
            //             long latency = System.nanoTime() - startTime;
            //             return SimpleTaskResult.success(taskId, latency, rowCount,
            //                 Map.of("rowCount", rowCount, "query", query));
            //         }
            //     } else {
            //         int updateCount = stmt.executeUpdate();
            //         long latency = System.nanoTime() - startTime;
            //         return SimpleTaskResult.success(taskId, latency, updateCount,
            //             Map.of("updateCount", updateCount, "query", query));
            //     }
            // }
            
            // Example stub implementation that simulates database query:
            Thread.sleep(5); // Simulate query execution
            
            long latency = System.nanoTime() - startTime;
            
            return SimpleTaskResult.success(taskId, latency, 1, 
                Map.of("jdbcUrl", jdbcUrl, "query", query, "type", "example"));
            
        } catch (Exception e) {
            long latency = System.nanoTime() - startTime;
            return SimpleTaskResult.failure(taskId, latency, 
                "Query failed: " + e.getMessage(),
                Map.of("jdbcUrl", jdbcUrl, "query", query, "error", e.getClass().getSimpleName()));
        }
    }
    
    /**
     * Cleanup method to be called when the test is finished.
     * In real implementation, close the connection pool:
     * 
     * <pre>
     * public void shutdown() {
     *     if (dataSource != null && !dataSource.isClosed()) {
     *         dataSource.close();
     *     }
     * }
     * </pre>
     */
}
