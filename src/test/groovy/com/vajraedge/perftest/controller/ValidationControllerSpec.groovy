package com.vajraedge.perftest.controller

import com.vajraedge.perftest.dto.TestConfigRequest
import com.vajraedge.perftest.validation.CheckResult
import com.vajraedge.perftest.validation.PreFlightValidator
import com.vajraedge.perftest.validation.ValidationContext
import com.vajraedge.perftest.validation.ValidationResult
import spock.lang.Specification

class ValidationControllerSpec extends Specification {
    
    PreFlightValidator mockValidator = Mock(PreFlightValidator)
    ValidationController controller
    
    def setup() {
        controller = new ValidationController(mockValidator)
    }
    
    def "should delegate validation to PreFlightValidator"() {
        given:
        def request = new TestConfigRequest()
        request.setTaskType("HTTP")
        request.setMaxConcurrency(100)
        request.setStartingConcurrency(10)
        request.setTestDurationSeconds(300L)
        request.setTaskParameter("http://localhost:8080")
        
        def validationResult = ValidationResult.builder()
            .addCheckResult(CheckResult.pass("Check 1", "Passed"))
            .addCheckResult(CheckResult.pass("Check 2", "Passed"))
            .build()
        
        when:
        def response = controller.validate(request)
        
        then:
        1 * mockValidator.validate(_) >> validationResult
        response.getBody() == validationResult
        response.getBody().status == ValidationResult.Status.PASS
        response.getBody().canProceed()
        response.getBody().checkResults.size() == 2
    }
    
    def "should return WARN status when any check warns"() {
        given:
        def request = new TestConfigRequest()
        request.setTaskType("HTTP")
        request.setMaxConcurrency(15000)
        request.setTestDurationSeconds(300L)
        request.setTaskParameter("http://localhost:8080")
        
        def validationResult = ValidationResult.builder()
            .addCheckResult(CheckResult.pass("Check 1", "Passed"))
            .addCheckResult(CheckResult.warn("Check 2", "High concurrency", ["Warning detail"]))
            .build()
        
        when:
        def response = controller.validate(request)
        
        then:
        1 * mockValidator.validate(_) >> validationResult
        response.getBody().status == ValidationResult.Status.WARN
        response.getBody().canProceed()
        response.getBody().checkResults.size() == 2
    }
    
    def "should return FAIL status when any check fails"() {
        given:
        def request = new TestConfigRequest()
        request.setTaskType("HTTP")
        request.setMaxConcurrency(100000)
        request.setTestDurationSeconds(300L)
        request.setTaskParameter("http://localhost:8080")
        
        def validationResult = ValidationResult.builder()
            .addCheckResult(CheckResult.pass("Check 1", "Passed"))
            .addCheckResult(CheckResult.fail("Check 2", "TPS too high", ["Exceeds limit"]))
            .build()
        
        when:
        def response = controller.validate(request)
        
        then:
        1 * mockValidator.validate(_) >> validationResult
        response.getBody().status == ValidationResult.Status.FAIL
        !response.getBody().canProceed()
        response.getBody().checkResults.size() == 2
    }
    
    def "should include check details in result"() {
        given:
        def request = new TestConfigRequest()
        request.setTaskType("HTTP")
        request.setMaxConcurrency(100)
        request.setTestDurationSeconds(300L)
        request.setTaskParameter("http://localhost:8080")
        
        def validationResult = ValidationResult.builder()
            .addCheckResult(CheckResult.pass("Service Health Check", "Service is healthy", 
                ["HTTP 200", "Latency: 50ms"]))
            .addCheckResult(CheckResult.pass("Configuration Check", "Configuration is valid",
                ["Task type: HTTP", "Concurrency: 100"]))
            .build()
        
        when:
        def response = controller.validate(request)
        
        then:
        1 * mockValidator.validate(_) >> validationResult
        def result = response.getBody()
        result.getCheckResults()[0].getCheckName() == "Service Health Check"
        result.getCheckResults()[0].getMessage() == "Service is healthy"
        result.getCheckResults()[0].getDetails()[0] == "HTTP 200"
        result.getCheckResults()[1].getCheckName() == "Configuration Check"
    }
    
    def "should include summary in result"() {
        given:
        def request = new TestConfigRequest()
        request.setTaskType("HTTP")
        request.setMaxConcurrency(100)
        request.setTestDurationSeconds(300L)
        request.setTaskParameter("http://localhost:8080")
        
        def validationResult = ValidationResult.builder()
            .addCheckResult(CheckResult.pass("Check 1", "Passed"))
            .addCheckResult(CheckResult.warn("Check 2", "Warning", ["detail"]))
            .addCheckResult(CheckResult.fail("Check 3", "Failed", ["detail"]))
            .build()
        
        when:
        def response = controller.validate(request)
        
        then:
        1 * mockValidator.validate(_) >> validationResult
        def result = response.getBody()
        result.getSummary() != null
        result.getSummary().contains("3 checks")
    }
}
