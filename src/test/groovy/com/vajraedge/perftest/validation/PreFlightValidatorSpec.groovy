package com.vajraedge.perftest.validation

import com.vajraedge.perftest.dto.TestConfigRequest
import spock.lang.Specification

class PreFlightValidatorSpec extends Specification {
    
    PreFlightValidator validator
    List<ValidationCheck> mockChecks
    
    def setup() {
        mockChecks = []
        validator = new PreFlightValidator(mockChecks)
    }
    
    def "should return PASS when all checks pass"() {
        given:
        def check1 = Mock(ValidationCheck)
        check1.getName() >> "Check 1"
        check1.execute(_) >> CheckResult.pass("Check 1", "Passed")
        
        def check2 = Mock(ValidationCheck)
        check2.getName() >> "Check 2"
        check2.execute(_) >> CheckResult.pass("Check 2", "Passed")
        
        mockChecks.addAll([check1, check2])
        
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setTaskParameter("http://localhost:8080")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = validator.validate(context)
        
        then:
        result.status == ValidationResult.Status.PASS
        result.canProceed()
        result.checkResults.size() == 2
        result.checkResults.every { it.status == CheckResult.Status.PASS }
    }
    
    def "should return WARN when any check warns"() {
        given:
        def check1 = Mock(ValidationCheck)
        check1.getName() >> "Check 1"
        check1.execute(_) >> CheckResult.pass("Check 1", "Passed")
        
        def check2 = Mock(ValidationCheck)
        check2.getName() >> "Check 2"
        check2.execute(_) >> CheckResult.warn("Check 2", "Warning message", ["Detail 1"])
        
        mockChecks.addAll([check1, check2])
        
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setTaskParameter("http://localhost:8080")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = validator.validate(context)
        
        then:
        result.status == ValidationResult.Status.WARN
        result.canProceed()
        result.checkResults.size() == 2
        result.checkResults.count { it.status == CheckResult.Status.WARN } == 1
    }
    
    def "should return FAIL when any check fails"() {
        given:
        def check1 = Mock(ValidationCheck)
        check1.getName() >> "Check 1"
        check1.execute(_) >> CheckResult.pass("Check 1", "Passed")
        
        def check2 = Mock(ValidationCheck)
        check2.getName() >> "Check 2"
        check2.execute(_) >> CheckResult.fail("Check 2", "Failure message", ["Error detail"])
        
        mockChecks.addAll([check1, check2])
        
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setTaskParameter("http://localhost:8080")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = validator.validate(context)
        
        then:
        result.status == ValidationResult.Status.FAIL
        !result.canProceed()
        result.checkResults.size() == 2
        result.checkResults.any { it.status == CheckResult.Status.FAIL }
    }
    
    def "should handle multiple failures"() {
        given:
        def check1 = Mock(ValidationCheck)
        check1.getName() >> "Check 1"
        check1.execute(_) >> CheckResult.fail("Check 1", "First failure", ["Error 1"])
        
        def check2 = Mock(ValidationCheck)
        check2.getName() >> "Check 2"
        check2.execute(_) >> CheckResult.fail("Check 2", "Second failure", ["Error 2"])
        
        def check3 = Mock(ValidationCheck)
        check3.getName() >> "Check 3"
        check3.execute(_) >> CheckResult.warn("Check 3", "Warning", ["Warning detail"])
        
        mockChecks.addAll([check1, check2, check3])
        
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setTaskParameter("http://localhost:8080")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = validator.validate(context)
        
        then:
        result.status == ValidationResult.Status.FAIL
        !result.canProceed()
        result.checkResults.size() == 3
        result.checkResults.count { it.status == CheckResult.Status.FAIL } == 2
        result.checkResults.count { it.status == CheckResult.Status.WARN } == 1
    }
    
    def "should handle skip results"() {
        given:
        def check1 = Mock(ValidationCheck)
        check1.getName() >> "Check 1"
        check1.execute(_) >> CheckResult.pass("Check 1", "Passed")
        
        def check2 = Mock(ValidationCheck)
        check2.getName() >> "Check 2"
        check2.execute(_) >> CheckResult.skip("Check 2", "Skipped for this task type")
        
        mockChecks.addAll([check1, check2])
        
        def config = new TestConfigRequest()
        config.setTaskType("CPU")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = validator.validate(context)
        
        then:
        result.status == ValidationResult.Status.PASS
        result.canProceed()
        result.checkResults.size() == 2
        result.checkResults.count { it.status == CheckResult.Status.SKIP } == 1
    }
    
    def "should handle empty check list gracefully"() {
        given:
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setTaskParameter("http://localhost:8080")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = validator.validate(context)
        
        then:
        result.status == ValidationResult.Status.PASS
        result.canProceed()
        result.checkResults.isEmpty()
    }
    
    def "should continue validation even when one check throws exception"() {
        given:
        def check1 = Mock(ValidationCheck)
        check1.getName() >> "Check 1"
        check1.execute(_) >> { throw new RuntimeException("Check 1 failed unexpectedly") }
        
        def check2 = Mock(ValidationCheck)
        check2.getName() >> "Check 2"
        check2.execute(_) >> CheckResult.pass("Check 2", "Passed")
        
        mockChecks.addAll([check1, check2])
        
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setTaskParameter("http://localhost:8080")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        def result = validator.validate(context)
        
        then:
        // Should have failed result for check1 and passed result for check2
        result.getCheckResults().size() == 2
        result.getCheckResults()[0].getStatus() == CheckResult.Status.FAIL
        result.getCheckResults()[1].getStatus() == CheckResult.Status.PASS
        result.getStatus() == ValidationResult.Status.FAIL
    }
    
    def "should execute all checks in order"() {
        given:
        def executionOrder = []
        
        def check1 = Mock(ValidationCheck)
        check1.getName() >> "Check 1"
        check1.execute(_) >> {
            executionOrder << "Check 1"
            return CheckResult.pass("Check 1", "Passed")
        }
        
        def check2 = Mock(ValidationCheck)
        check2.getName() >> "Check 2"
        check2.execute(_) >> {
            executionOrder << "Check 2"
            return CheckResult.pass("Check 2", "Passed")
        }
        
        def check3 = Mock(ValidationCheck)
        check3.getName() >> "Check 3"
        check3.execute(_) >> {
            executionOrder << "Check 3"
            return CheckResult.pass("Check 3", "Passed")
        }
        
        mockChecks.addAll([check1, check2, check3])
        
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setTaskParameter("http://localhost:8080")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        validator.validate(context)
        
        then:
        executionOrder == ["Check 1", "Check 2", "Check 3"]
    }
    
    def "should pass validation context to each check"() {
        given:
        def receivedContexts = []
        
        def check1 = Mock(ValidationCheck)
        check1.getName() >> "Check 1"
        check1.execute(_) >> { ValidationContext ctx ->
            receivedContexts << ctx
            return CheckResult.pass("Check 1", "Passed")
        }
        
        mockChecks.add(check1)
        
        def config = new TestConfigRequest()
        config.setTaskType("HTTP")
        config.setMaxConcurrency(100)
        config.setTestDurationSeconds(300L)
        config.setTaskParameter("http://localhost:8080")
        
        def context = ValidationContext.builder().config(config).build()
        
        when:
        validator.validate(context)
        
        then:
        receivedContexts.size() == 1
        receivedContexts[0].taskType == "HTTP"
        receivedContexts[0].maxConcurrency == 100
        receivedContexts[0].testDurationSeconds == 300L
        receivedContexts[0].taskParameter == "http://localhost:8080"
    }
}
