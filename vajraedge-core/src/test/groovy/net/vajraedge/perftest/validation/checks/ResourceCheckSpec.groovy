package net.vajraedge.perftest.validation.checks

import net.vajraedge.perftest.dto.TestConfigRequest
import net.vajraedge.perftest.validation.CheckResult
import net.vajraedge.perftest.validation.ValidationContext
import spock.lang.Specification

class ResourceCheckSpec extends Specification {
    
    ResourceCheck check = new ResourceCheck()
    
    def "should return check name"() {
        expect:
        check.getName() == "Resource Check"
    }
    
    def "should complete resource check successfully"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setMaxConcurrency(10) // Small concurrency
        config.setTaskParameter("http://localhost:8080")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = check.execute(context)
        
        then:
        // Should complete without exception
        result != null
        result.status != null
    }
    
    def "should check system resources"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setTaskParameter("http://localhost:8080")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = check.execute(context)
        
        then:
        // Should provide some details about resources
        result.details.size() > 0 || result.message != null
    }
}
