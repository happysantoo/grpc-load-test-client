package net.vajraedge.perftest.validation.checks

import net.vajraedge.perftest.dto.TestConfigRequest
import net.vajraedge.perftest.validation.CheckResult
import net.vajraedge.perftest.validation.ValidationContext
import spock.lang.Specification

class NetworkCheckSpec extends Specification {
    
    NetworkCheck check = new NetworkCheck()
    
    def "should return check name"() {
        expect:
        check.getName() == "Network Check"
    }
    
    def "should skip for non-HTTP task types"() {
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
    
    def "should pass for well-known reachable host"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setTaskParameter("http://www.google.com")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = check.execute(context)
        
        then:
        // Google should be resolvable and reachable
        result.status in [CheckResult.Status.PASS, CheckResult.Status.WARN]
        result.details.any { it.contains("DNS resolved") || it.contains("Target:") }
    }
    
    def "should fail for invalid hostname"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setTaskParameter("http://this-host-definitely-does-not-exist-12345.com")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = check.execute(context)
        
        then:
        result.status == CheckResult.Status.FAIL
        result.message.contains("Cannot resolve")
        result.details.any { it.contains("DNS") || it.contains("resolution") }
    }
    
    def "should handle localhost properly"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setTaskParameter("http://localhost:8080")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = check.execute(context)
        
        then:
        // DNS should resolve localhost
        result.details.size() > 0
        // May pass or fail depending on whether service is actually running
        result.status in [CheckResult.Status.PASS, CheckResult.Status.FAIL, CheckResult.Status.WARN]
    }
    
    def "should handle IP address directly"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setTaskParameter("http://127.0.0.1:8080")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = check.execute(context)
        
        then:
        // IP addresses still go through DNS
        result.details.size() > 0
        result.status in [CheckResult.Status.PASS, CheckResult.Status.FAIL, CheckResult.Status.WARN]
    }
    
    def "should detect proxy configuration when present"() {
        given:
        def originalHttpProxy = System.getProperty("http.proxyHost")
        def originalHttpsProxy = System.getProperty("https.proxyHost")
        
        // Set proxy properties
        System.setProperty("http.proxyHost", "proxy.example.com")
        System.setProperty("http.proxyPort", "8080")
        
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setTaskParameter("http://www.google.com")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = check.execute(context)
        
        then:
        result.details.any { it.contains("Proxy") || it.contains("proxy") }
        
        cleanup:
        // Restore original proxy settings
        if (originalHttpProxy != null) {
            System.setProperty("http.proxyHost", originalHttpProxy)
        } else {
            System.clearProperty("http.proxyHost")
        }
        System.clearProperty("http.proxyPort")
        if (originalHttpsProxy != null) {
            System.setProperty("https.proxyHost", originalHttpsProxy)
        }
    }
    
    def "should succeed when no proxy configured"() {
        given:
        // Ensure no proxy is set
        System.clearProperty("http.proxyHost")
        System.clearProperty("https.proxyHost")
        
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setTaskParameter("http://www.google.com")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = check.execute(context)
        
        then:
        // Should succeed for Google without proxy
        result.status in [CheckResult.Status.PASS, CheckResult.Status.WARN]
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
        result.details.any { it.contains("Invalid") || it.contains("parse") }
    }
    
    def "should skip when task parameter is null"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setTaskParameter(null)
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = check.execute(context)
        
        then:
        result.status == CheckResult.Status.SKIP
        result.message.contains("No URL provided")
    }
    
    def "should test TCP connectivity"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setTaskParameter("http://www.google.com:80")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = check.execute(context)
        
        then:
        result.details.any { it.contains("TCP") || it.contains("connectivity") }
        // Google port 80 should be reachable
        if (result.status == CheckResult.Status.PASS) {
            assert result.details.any { it.contains("successful") || it.contains("established") }
        }
    }
    
    def "should fail on unreachable port"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        // Use a port that's likely closed
        config.setTaskParameter("http://localhost:9999")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = check.execute(context)
        
        then:
        // May fail on TCP connectivity if port is closed
        result.status in [CheckResult.Status.FAIL, CheckResult.Status.WARN]
    }
    
    def "should provide helpful suggestions on failure"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setTaskParameter("http://invalid-host-name-xyz.test")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = check.execute(context)
        
        then:
        result.status == CheckResult.Status.FAIL
        // Should provide suggestions
        result.details.size() > 1
        result.details.any { 
            it.contains("Check") || it.contains("Verify") || it.contains("Ensure")
        }
    }
}
