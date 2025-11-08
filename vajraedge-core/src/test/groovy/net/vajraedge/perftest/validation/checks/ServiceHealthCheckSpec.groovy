package net.vajraedge.perftest.validation.checks

import net.vajraedge.perftest.dto.TestConfigRequest
import net.vajraedge.perftest.validation.CheckResult
import net.vajraedge.perftest.validation.ValidationContext
import spock.lang.Specification

class ServiceHealthCheckSpec extends Specification {
    
    ServiceHealthCheck check = new ServiceHealthCheck()
    
    def "should return check name"() {
        expect:
        check.getName() == "Service Health Check"
    }
    
    def "should skip check for non-HTTP task types"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskType("CPU")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = check.execute(context)
        
        then:
        result.status == CheckResult.Status.SKIP
        result.message.contains("Not applicable")
    }
    
    def "should check reachable endpoint like google"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setTaskParameter("http://www.google.com")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = check.execute(context)
        
        then:
        // Should complete without exception
        result != null
        result.status != null
    }
    
    def "should fail when connection to unreachable host"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setTaskParameter("http://localhost:9999") // Likely closed port
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = check.execute(context)
        
        then:
        result.status == CheckResult.Status.FAIL
        result.details.any { it.contains("Connection failed") || it.contains("reach") }
    }
    
    def "should fail when URL is invalid"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setTaskParameter("not-a-valid-url")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = check.execute(context)
        
        then:
        result.status == CheckResult.Status.FAIL
    }
    
    def "should fail when task parameter is null"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setTaskParameter(null)
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = check.execute(context)
        
        then:
        result.status == CheckResult.Status.FAIL
        result.details.any { it.contains("URL") || it.contains("parameter") }
    }
}
