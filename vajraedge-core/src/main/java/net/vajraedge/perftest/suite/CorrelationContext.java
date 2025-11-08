package net.vajraedge.perftest.suite;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.Random;

/**
 * Context for sharing data between scenarios in a test suite.
 * 
 * <p>Enables correlation patterns like:
 * <ul>
 *   <li>Scenario 1 creates user IDs, Scenario 2 uses them</li>
 *   <li>Scenario 1 creates order IDs, Scenario 2 queries them</li>
 *   <li>Share authentication tokens across scenarios</li>
 * </ul>
 * 
 * <p>Thread-safe for concurrent access from multiple scenarios.
 * 
 * @author Santhosh Kuppusamy
 * @since 1.0
 */
public class CorrelationContext {
    private final Map<String, Object> variables;
    private final Map<String, List<Object>> pools;
    private final Random random;
    
    public CorrelationContext() {
        this.variables = new ConcurrentHashMap<>();
        this.pools = new ConcurrentHashMap<>();
        this.random = new Random();
    }
    
    /**
     * Store a single value by key.
     * 
     * @param key the variable key
     * @param value the value to store
     */
    public void set(String key, Object value) {
        variables.put(key, value);
    }
    
    /**
     * Retrieve a single value by key.
     * 
     * @param key the variable key
     * @return the stored value, or null if not found
     */
    public Object get(String key) {
        return variables.get(key);
    }
    
    /**
     * Retrieve a typed value by key.
     * 
     * @param key the variable key
     * @param type the expected type
     * @param <T> the type parameter
     * @return the typed value, or null if not found
     * @throws ClassCastException if value cannot be cast to type
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = variables.get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }
    
    /**
     * Add a value to a pool (list of values).
     * Useful for storing multiple IDs created by one scenario.
     * 
     * @param poolKey the pool key
     * @param value the value to add
     */
    public void addToPool(String poolKey, Object value) {
        pools.computeIfAbsent(poolKey, k -> new CopyOnWriteArrayList<>()).add(value);
    }
    
    /**
     * Get a random value from a pool.
     * 
     * @param poolKey the pool key
     * @return random value from pool, or null if pool empty
     */
    public Object getFromPool(String poolKey) {
        List<Object> pool = pools.get(poolKey);
        if (pool == null || pool.isEmpty()) {
            return null;
        }
        return pool.get(random.nextInt(pool.size()));
    }
    
    /**
     * Get a typed random value from a pool.
     * 
     * @param poolKey the pool key
     * @param type the expected type
     * @param <T> the type parameter
     * @return random typed value from pool, or null if pool empty
     * @throws ClassCastException if value cannot be cast to type
     */
    @SuppressWarnings("unchecked")
    public <T> T getFromPool(String poolKey, Class<T> type) {
        Object value = getFromPool(poolKey);
        if (value == null) {
            return null;
        }
        return (T) value;
    }
    
    /**
     * Get all values from a pool.
     * 
     * @param poolKey the pool key
     * @return list of all values, or empty list if pool doesn't exist
     */
    public List<Object> getPool(String poolKey) {
        List<Object> pool = pools.get(poolKey);
        return pool != null ? List.copyOf(pool) : List.of();
    }
    
    /**
     * Get size of a pool.
     * 
     * @param poolKey the pool key
     * @return number of items in pool
     */
    public int getPoolSize(String poolKey) {
        List<Object> pool = pools.get(poolKey);
        return pool != null ? pool.size() : 0;
    }
    
    /**
     * Check if a variable exists.
     * 
     * @param key the variable key
     * @return true if variable is set
     */
    public boolean has(String key) {
        return variables.containsKey(key);
    }
    
    /**
     * Clear all variables and pools.
     */
    public void clear() {
        variables.clear();
        pools.clear();
    }
    
    /**
     * Get all variable keys.
     * 
     * @return set of all variable keys
     */
    public java.util.Set<String> getVariableKeys() {
        return variables.keySet();
    }
    
    /**
     * Get all pool keys.
     * 
     * @return set of all pool keys
     */
    public java.util.Set<String> getPoolKeys() {
        return pools.keySet();
    }
}
