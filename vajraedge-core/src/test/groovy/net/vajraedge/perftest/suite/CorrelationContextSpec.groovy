package net.vajraedge.perftest.suite

import spock.lang.Specification

/**
 * Tests for CorrelationContext.
 */
class CorrelationContextSpec extends Specification {
    
    def "should store and retrieve single values"() {
        given: "a correlation context"
        def context = new CorrelationContext()
        
        when: "storing values"
        context.set("userId", "user123")
        context.set("token", "abc-token-xyz")
        context.set("count", 42)
        
        then: "values can be retrieved"
        context.get("userId") == "user123"
        context.get("token") == "abc-token-xyz"
        context.get("count") == 42
    }
    
    def "should retrieve typed values"() {
        given: "a context with typed values"
        def context = new CorrelationContext()
        context.set("name", "Alice")
        context.set("age", 30)
        context.set("active", true)
        
        expect: "typed retrieval works"
        context.get("name", String) == "Alice"
        context.get("age", Integer) == 30
        context.get("active", Boolean) == true
    }
    
    def "should add values to pools"() {
        given: "a correlation context"
        def context = new CorrelationContext()
        
        when: "adding values to a pool"
        context.addToPool("userIds", "user1")
        context.addToPool("userIds", "user2")
        context.addToPool("userIds", "user3")
        
        then: "pool contains all values"
        context.getPoolSize("userIds") == 3
        context.getPool("userIds") == ["user1", "user2", "user3"]
    }
    
    def "should get random values from pool"() {
        given: "a pool with values"
        def context = new CorrelationContext()
        (1..100).each { context.addToPool("numbers", it) }
        
        when: "getting random values"
        def samples = (1..1000).collect { context.getFromPool("numbers") }
        
        then: "all samples are from the pool"
        samples.every { it instanceof Integer && it >= 1 && it <= 100 }
        
        and: "distribution covers multiple values"
        samples.unique().size() > 10
    }
    
    def "should get typed random values from pool"() {
        given: "a pool with typed values"
        def context = new CorrelationContext()
        context.addToPool("ids", "id1")
        context.addToPool("ids", "id2")
        
        when: "getting typed random value"
        def id = context.getFromPool("ids", String)
        
        then: "value is correctly typed"
        id instanceof String
        id in ["id1", "id2"]
    }
    
    def "should return null for missing keys"() {
        given: "an empty context"
        def context = new CorrelationContext()
        
        expect:
        context.get("missing") == null
        context.get("missing", String) == null
        context.getFromPool("missingPool") == null
        context.getFromPool("missingPool", String) == null
    }
    
    def "should return empty list for missing pool"() {
        given: "an empty context"
        def context = new CorrelationContext()
        
        expect:
        context.getPool("missingPool") == []
        context.getPoolSize("missingPool") == 0
    }
    
    def "should check variable existence"() {
        given: "a context with some values"
        def context = new CorrelationContext()
        context.set("exists", "value")
        
        expect:
        context.has("exists")
        !context.has("doesNotExist")
    }
    
    def "should provide all keys"() {
        given: "a context with multiple values"
        def context = new CorrelationContext()
        context.set("key1", "val1")
        context.set("key2", "val2")
        context.addToPool("pool1", "item")
        context.addToPool("pool2", "item")
        
        expect: "all keys are available"
        context.getVariableKeys().containsAll(["key1", "key2"])
        context.getPoolKeys().containsAll(["pool1", "pool2"])
    }
    
    def "should clear all data"() {
        given: "a context with data"
        def context = new CorrelationContext()
        context.set("var", "value")
        context.addToPool("pool", "item")
        
        when: "clearing"
        context.clear()
        
        then: "all data is removed"
        context.getVariableKeys().isEmpty()
        context.getPoolKeys().isEmpty()
        !context.has("var")
        context.getPoolSize("pool") == 0
    }
    
    def "should be thread-safe for concurrent access"() {
        given: "a shared context"
        def context = new CorrelationContext()
        def threads = 10
        def itemsPerThread = 100
        
        when: "multiple threads add to pool concurrently"
        def futures = (1..threads).collect { threadNum ->
            Thread.start {
                (1..itemsPerThread).each { i ->
                    context.addToPool("concurrent", "thread${threadNum}-item${i}")
                }
            }
        }
        futures*.join()
        
        then: "all items are added"
        context.getPoolSize("concurrent") == threads * itemsPerThread
    }
}
