package net.vajraedge.perftest.validation.checks

import net.vajraedge.perftest.dto.TestConfigRequest
import net.vajraedge.perftest.validation.CheckResult
import net.vajraedge.perftest.validation.ValidationContext
import spock.lang.Specification
import spock.lang.Unroll

class ConfigurationCheckSpec extends Specification {
    
    ConfigurationCheck check = new ConfigurationCheck()
    
    def "should return check name"() {
        expect:
        check.getName() == "Configuration Check"
    }
    
    def "should pass with valid configuration"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setMaxConcurrency(100)
        config.setStartingConcurrency(10)
        config.setTestDurationSeconds(300L)
        config.setTaskParameter("http://localhost:8080")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = check.execute(context)
        
        then:
        result.status == CheckResult.Status.PASS
        result.message == "Configuration is valid"
        result.details.any { it.contains("Task type: HTTP") }
    }
    
    @Unroll
    def "should fail when TPS limit exceeds maximum (#tpsLimit > #maxSafe)"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setMaxTpsLimit(tpsLimit)
        config.setTaskParameter("http://localhost:8080")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = check.execute(context)
        
        then:
        result.status == CheckResult.Status.FAIL
        result.message == "Configuration validation failed"
        result.details.any { it.contains("exceeds maximum safe limit") }
        
        where:
        tpsLimit  | maxSafe
        100_001   | 100_000
        200_000   | 100_000
    }
    
    @Unroll
    def "should warn when TPS limit is high but acceptable (#tpsLimit)"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setMaxTpsLimit(tpsLimit)
        config.setTaskParameter("http://localhost:8080")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = check.execute(context)
        
        then:
        result.status == CheckResult.Status.WARN
        result.message == "Configuration has warnings"
        result.details.any { it.contains("very high") }
        
        where:
        tpsLimit << [10_001, 50_000, 99_999]
    }
    
    @Unroll
    def "should fail when concurrency exceeds maximum (#concurrency > #maxSafe)"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setMaxConcurrency(concurrency)
        config.setTaskParameter("http://localhost:8080")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = check.execute(context)
        
        then:
        result.status == CheckResult.Status.FAIL
        result.details.any { it.contains("exceeds maximum safe limit") }
        
        where:
        concurrency | maxSafe
        50_001      | 50_000
        100_000     | 50_000
    }
    
    @Unroll
    def "should warn when concurrency is high but acceptable (#concurrency)"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setMaxConcurrency(concurrency)
        config.setTaskParameter("http://localhost:8080")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = check.execute(context)
        
        then:
        result.status == CheckResult.Status.WARN
        result.details.any { it.contains("very high") }
        
        where:
        concurrency << [10_001, 25_000, 49_999]
    }
    
    def "should fail when test duration exceeds maximum"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setTestDurationSeconds(3601L) // > 1 hour
        config.setTaskParameter("http://localhost:8080")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = check.execute(context)
        
        then:
        result.status == CheckResult.Status.FAIL
        result.details.any { it.contains("exceeds maximum safe limit") }
    }
    
    def "should warn when test duration is very short"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setTestDurationSeconds(5L)
        config.setTaskParameter("http://localhost:8080")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = check.execute(context)
        
        then:
        result.status == CheckResult.Status.WARN
        result.details.any { it.contains("very short") }
    }
    
    def "should fail when starting concurrency > max concurrency"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setStartingConcurrency(100)
        config.setMaxConcurrency(50)
        config.setTaskParameter("http://localhost:8080")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = check.execute(context)
        
        then:
        result.status == CheckResult.Status.FAIL
        result.details.any { it.contains("cannot be greater than max concurrency") }
    }
    
    def "should fail when task type is null"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskType(null)
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = check.execute(context)
        
        then:
        result.status == CheckResult.Status.FAIL
        result.details.any { it.contains("Task type is required") }
    }
    
    @Unroll
    def "should fail when URL is missing for HTTP task type: #taskType"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskType(taskType)
        config.setTaskParameter(null)
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = check.execute(context)
        
        then:
        result.status == CheckResult.Status.FAIL
        result.details.any { it.contains("URL parameter is required") }
        
        where:
        taskType << ["HTTP", "HTTP_GET", "HTTP_POST"]
    }
    
    def "should fail when URL has invalid protocol"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setTaskParameter("ftp://localhost:8080")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = check.execute(context)
        
        then:
        result.status == CheckResult.Status.FAIL
        result.details.any { it.contains("must start with http://") }
    }
}
